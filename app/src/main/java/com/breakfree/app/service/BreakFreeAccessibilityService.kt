package com.breakfree.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.breakfree.app.BreakFreeApplication
import com.breakfree.android.R
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.BlockOverlayActivity
import com.breakfree.app.ui.theme.BreakFreeTheme
import com.breakfree.app.data.model.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.net.URI

/**
 * Watches window events and blocks access to blocked apps and domains
 * unless a break is active. Uses WindowManager overlay for a seamless experience.
 */
class BreakFreeAccessibilityService : AccessibilityService() {

    private lateinit var scope: CoroutineScope
    @Volatile private var blockedPackages: Set<String> = emptySet()
    @Volatile private var blockedDomainsRegex: Regex? = null
    @Volatile private var doomscrollWhitelist: Set<String> = emptySet()
    @Volatile private var mostUsedApps: Set<String> = emptySet()
    @Volatile private var doomscrollProtectionEnabled: Boolean = false
    @Volatile private var targetScreenTimeMinutes: Int = 60
    
    private var ownPackageName: String = ""
    
    private var overlayView: View? = null
    private var lifecycleOwner: ServiceLifecycleOwner? = null

    // Doomscrolling monitor state
    private var currentForegroundApp: String? = null
    private var appStartTime: Long = 0
    private var doomscrollJob: Job? = null

    // URL monitoring state
    private var lastSeenUrl: String? = null
    private var browserUrlCheckJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        ownPackageName = packageName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = BreakFreeApplication.from(this)
        scope.launch {
            app.repository.observeBlockedApps().collectLatest { apps ->
                blockedPackages = apps.map { it.packageName }.toSet()
            }
        }

        scope.launch {
            app.repository.observeBlockedDomains().collectLatest { domains ->
                val blockedOnes = domains.filter { it.isBlocked }.map { it.domain.lowercase() }
                if (blockedOnes.isEmpty()) {
                    blockedDomainsRegex = null
                } else {
                    // Pattern: match exactly the domain OR any subdomain of it
                    // Example: (.*\.)?(facebook\.com|instagram\.com)
                    val patterns = blockedOnes.joinToString("|") { Regex.escape(it) }
                    blockedDomainsRegex = Regex("^(.*\\.)?($patterns)$", RegexOption.IGNORE_CASE)
                }
            }
        }

        scope.launch {
            app.settingsDataStore.doomscrollingProtectionEnabled.collectLatest { enabled ->
                doomscrollProtectionEnabled = enabled
                if (!enabled) cancelDoomscrollCheck()
            }
        }

        scope.launch {
            app.settingsDataStore.targetScreenTimeMinutes.collectLatest { mins ->
                targetScreenTimeMinutes = mins
            }
        }

        scope.launch {
            app.appRepository.apps.collectLatest { apps ->
                doomscrollWhitelist = apps.filter { it.isDoomscrollWhitelisted }.map { it.packageName }.toSet()
                
                // Identify "most used apps" - e.g., top 10 apps to be safer
                mostUsedApps = apps.sortedByDescending { it.usageTimeMs + it.popularityScore * 60000L }
                    .take(10)
                    .map { it.packageName }
                    .toSet()
                
                android.util.Log.d("BreakFreeService", "Most used apps updated: $mostUsedApps")
            }
        }
        
        // Hide overlay if break becomes active, or show it if it expires
        scope.launch {
            app.breakStateManager.state
                .map { it.phase == BreakPhase.ACTIVE }
                .distinctUntilChanged()
                .collectLatest { active ->
                    if (active) {
                        hideOverlay()
                    } else {
                        // Break ended, re-evaluate current window
                        val root = rootInActiveWindow
                        val pkg = root?.packageName?.toString()
                        if (pkg != null) {
                            handleWindowStateChanged(pkg)
                        }
                    }
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        try {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
                AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: getForegroundPackageName()
                    if (packageName != null && packageName != ownPackageName) {
                        handleWindowStateChanged(packageName)
                    }
                }
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val packageName = event.packageName?.toString() ?: getForegroundPackageName()
                    if (packageName != null && isBrowser(packageName)) {
                        scheduleBrowserUrlCheck(packageName)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("BreakFreeService", "Error handling accessibility event", e)
        }
    }

    private fun getForegroundPackageName(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    private fun handleWindowStateChanged(packageName: String) {
        val app = BreakFreeApplication.from(this)
        val isBreakActive = app.breakStateManager.isBreakActiveNow()

        try {
            updateDoomscrollMonitor(packageName)

            if (isBreakActive) {
                hideOverlay()
                return
            }

            if (packageName in blockedPackages) {
                showOverlay(packageName)
                return
            }

            if (isBrowser(packageName)) {
                // For browsers, we check the URL
                checkBrowserUrl(packageName)
            } else if (packageName != "android" && 
                       packageName != "com.android.systemui" && 
                       !packageName.contains("inputmethod")) {
                hideOverlay()
            }
        } catch (e: Exception) {
            android.util.Log.e("BreakFreeService", "Error in handleWindowStateChanged", e)
        }
    }

    private fun scheduleBrowserUrlCheck(packageName: String) {
        browserUrlCheckJob?.cancel()
        browserUrlCheckJob = scope.launch {
            // Debounce since TYPE_WINDOW_CONTENT_CHANGED fires in bursts
            delay(200) 
            checkBrowserUrl(packageName)
        }
    }

    private fun checkBrowserUrl(packageName: String) {
        val app = BreakFreeApplication.from(this)
        val isBreakActive = app.breakStateManager.isBreakActiveNow()

        if (isBreakActive) {
            hideOverlay()
            return
        }

        // Always find current URL node to ensure we have latest text
        val urlNode = findUrlBarNode(packageName)
        val url = urlNode?.text?.toString()
        urlNode?.recycle()

        if (url == null) return
        
        // Normalize URL if needed (some browsers don't include protocol)
        val domain = getDomainFromUrl(url) ?: return
        
        val isBlocked = blockedDomainsRegex?.matches(domain) == true
        if (isBlocked) {
            showOverlay(packageName, domain)
        } else {
            hideOverlay()
        }
        
        lastSeenUrl = url
    }

    private fun findUrlBarNode(packageName: String): AccessibilityNodeInfo? {
        val rootNode = rootInActiveWindow ?: return null
        val config = BROWSER_CONFIGS[packageName]
        
        // 1. Try specific resource IDs from config
        config?.urlBarIds?.forEach { id ->
            rootNode.findAccessibilityNodeInfosByViewId(id)?.firstOrNull()?.let { return it }
        }

        // 2. Generic fallback search for common address bar patterns
        return findUrlNodeRecursive(rootNode)
    }

    private fun findUrlNodeRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val resourceName = node.viewIdResourceName
        val contentDesc = node.contentDescription?.toString()
        val text = node.text?.toString()

        if (resourceName?.contains("url_bar", ignoreCase = true) == true ||
            resourceName?.contains("address_bar", ignoreCase = true) == true ||
            resourceName?.contains("location_bar", ignoreCase = true) == true ||
            resourceName?.contains("url_view", ignoreCase = true) == true ||
            contentDesc?.contains("address", ignoreCase = true) == true ||
            contentDesc?.contains("url", ignoreCase = true) == true ||
            text?.contains("search or type", ignoreCase = true) == true ||
            text?.contains("search or enter", ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                val result = findUrlNodeRecursive(child)
                if (result != null) return result
            }
        }
        return null
    }

    private fun isBrowser(packageName: String): Boolean {
        return packageName in BROWSER_CONFIGS.keys
    }

    private fun getDomainFromUrl(url: String): String? {
        return try {
            val uri = if (url.contains("://")) URI(url) else URI("http://$url")
            val host = uri.host ?: return null
            host.removePrefix("www.").lowercase()
        } catch (e: Exception) {
            // Fallback for partial URLs or browsers that only show "domain.com"
            val domain = url.split("/").firstOrNull()?.removePrefix("www.")?.lowercase()
            if (domain?.contains(".") == true) domain else null
        }
    }

    private fun showOverlay(blockedPackage: String, blockedDomain: String? = null) {
        if (!Settings.canDrawOverlays(this)) {
            launchBlockOverlayActivity(blockedPackage, blockedDomain)
            return
        }

        if (overlayView != null) return

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or 
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )

        val owner = ServiceLifecycleOwner().also { it.start() }
        lifecycleOwner = owner
        
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            
            setContent {
                BreakFreeTheme {
                    OverlayContent(
                        blockedPackage = blockedPackage,
                        blockedDomain = blockedDomain,
                        onGoHome = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(homeIntent)
                            hideOverlay()
                        }
                    )
                }
            }
        }

        wm.addView(overlayView, params)
    }

    private fun hideOverlay() {
        overlayView?.let {
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            wm.removeView(it)
            overlayView = null
            lifecycleOwner?.destroy()
            lifecycleOwner = null
        }
    }

    private fun launchBlockOverlayActivity(blockedPackage: String, blockedDomain: String? = null) {
        val intent = Intent(this, BlockOverlayActivity::class.java)
            .putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            .putExtra(BlockOverlayActivity.EXTRA_BLOCKED_DOMAIN, blockedDomain)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        startActivity(intent)
    }

    private fun updateDoomscrollMonitor(packageName: String) {
        if (!doomscrollProtectionEnabled) {
            cancelDoomscrollCheck()
            return
        }
        
        if (packageName == currentForegroundApp) return
        
        cancelDoomscrollCheck()
        
        currentForegroundApp = packageName
        appStartTime = System.currentTimeMillis()
        
        // Don't monitor system apps, blocked apps, or whitelisted apps
        if (packageName == "android" ||
            packageName == "com.android.settings" || 
            packageName.contains("android.settings") ||
            packageName == "com.android.systemui" ||
            packageName == ownPackageName ||
            packageName in blockedPackages ||
            packageName in doomscrollWhitelist) {
            android.util.Log.d("BreakFreeService", "Doomscroll: Skipping $packageName (system/blocked/whitelist)")
            return
        }

        // Only monitor "most used apps" as per requirement
        if (packageName !in mostUsedApps) {
            android.util.Log.d("BreakFreeService", "Doomscroll: Skipping $packageName (not in most used)")
            return
        }

        android.util.Log.d("BreakFreeService", "Doomscroll: Starting timer for $packageName")
        // Start 5-minute timer
        doomscrollJob = scope.launch {
            delay(5 * 60 * 1000) // 5 minutes
            android.util.Log.d("BreakFreeService", "Doomscroll: Warning triggered for $packageName")
            triggerDoomscrollWarning(packageName)
        }
    }

    private fun cancelDoomscrollCheck() {
        doomscrollJob?.cancel()
        doomscrollJob = null
    }

    private fun triggerDoomscrollWarning(packageName: String) {
        val appLabel = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "doomscroll_warnings"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "Doomscroll Warnings",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Periodic checks to help you avoid doomscrolling"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, com.breakfree.app.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Time Check!")
            .setContentText("You've been using $appLabel for 5 minutes. Is this a good use of your time?")
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(2, notification)
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        scope.cancel()
    }

    private data class BrowserConfig(val urlBarIds: List<String>)

    companion object {
        private val BROWSER_CONFIGS = mapOf(
            "com.android.chrome" to BrowserConfig(listOf(
                "com.android.chrome:id/url_bar",
                "com.android.chrome:id/search_box_text"
            )),
            "org.mozilla.firefox" to BrowserConfig(listOf(
                "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
                "org.mozilla.firefox:id/url_bar_title",
                "org.mozilla.firefox:id/url_view"
            )),
            "com.sec.android.app.sbrowser" to BrowserConfig(listOf(
                "com.sec.android.app.sbrowser:id/location_bar_edit_text",
                "com.sec.android.app.sbrowser:id/url_bar"
            )),
            "com.opera.browser" to BrowserConfig(listOf(
                "com.opera.browser:id/url_field",
                "com.opera.browser:id/address_bar"
            )),
            "com.duckduckgo.mobile.android" to BrowserConfig(listOf(
                "com.duckduckgo.mobile.android:id/omnibarTextInput",
                "com.duckduckgo.mobile.android:id/search_box"
            )),
            "com.brave.browser" to BrowserConfig(listOf(
                "com.brave.browser:id/url_bar",
                "com.brave.browser:id/search_box_text"
            )),
            "com.microsoft.emmx" to BrowserConfig(listOf(
                "com.microsoft.emmx:id/url_bar",
                "com.microsoft.emmx:id/search_box_text"
            )),
            "com.vivaldi.browser" to BrowserConfig(listOf(
                "com.vivaldi.browser:id/url_bar",
                "com.vivaldi.browser:id/search_box_text"
            ))
        )
    }
}

@Composable
private fun OverlayContent(
    blockedPackage: String,
    blockedDomain: String? = null,
    onGoHome: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = BreakFreeApplication.from(context)
    val breakState by app.breakStateManager.state.collectAsState()
    
    var isVisible by remember { mutableStateOf(false) }
    var ticker by remember { mutableStateOf(0) }

    val appLabel = remember(blockedPackage) {
        runCatching {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(blockedPackage, 0)
            ).toString()
        }.getOrDefault(blockedPackage)
    }

    val blockMessage = if (blockedDomain != null) {
        "$blockedDomain is blocked on $appLabel"
    } else {
        "$appLabel is blocked"
    }
    
    LaunchedEffect(Unit) {
        // Keep it transparent for a brief moment to avoid flickering
        delay(100)
        isVisible = true
        
        while (true) {
            delay(1000)
            ticker++
        }
    }

    Scaffold(
        containerColor = if (isVisible) MaterialTheme.colorScheme.background else Color.Transparent
    ) { padding ->
        if (isVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(blockMessage, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                
                if (breakState.phase == BreakPhase.GRACE) {
                    Text(
                        "Break starts in ${formatCountdown(((breakState.graceEndsAtEpochMs - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    "Open BreakFree and request a break to access it temporarily.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                
                Button(
                    onClick = onGoHome, 
                    modifier = Modifier.fillMaxWidth()
                ) { 
                    Text("Go home") 
                }
            }
        }
    }
}

private fun formatCountdown(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val m = seconds / 60
            val s = seconds % 60
            "${m}m ${s}s"
        }
        else -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            "${h}h ${m}m"
        }
    }
}
