package com.breakfree.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.core.ShakeDetector
import com.breakfree.app.data.settings.AppTheme
import com.breakfree.app.ui.screens.AppListScreen
import com.breakfree.app.ui.screens.BreakScreen
import com.breakfree.app.ui.screens.DomainListScreen
import com.breakfree.app.ui.screens.DoomscrollWhitelistScreen
import com.breakfree.app.ui.screens.FeedbackScreen
import com.breakfree.app.ui.screens.HomeScreen
import com.breakfree.app.ui.screens.SettingsScreen
import com.breakfree.app.ui.theme.BreakFreeTheme
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    private var permissionRefreshTrigger by mutableStateOf(0)
    private var navController: NavController? = null

    private lateinit var sensorManager: SensorManager
    private var shakeDetector: ShakeDetector? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageStatsSettings() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    fun isOverlayPermissionGranted(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:$packageName".toUri()
        )
        startActivity(intent)
    }

    fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        permissionRefreshTrigger++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = BreakFreeApplication.from(application)
        val settings = app.settingsDataStore
        
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        
        setContent {
            val theme by settings.theme.collectAsState(initial = AppTheme.AUTO)
            val shakeEnabled by settings.shakeToSendFeedback.collectAsState(initial = true)
            
            // Trigger recomposition when permissionRefreshTrigger changes
            val trigger = permissionRefreshTrigger 
            
            LaunchedEffect(shakeEnabled) {
                if (shakeEnabled) {
                    shakeDetector = ShakeDetector {
                        val screenshotPath = captureScreenshot()
                        navController?.navigate("feedback?screenshot=$screenshotPath")
                    }
                    sensorManager.registerListener(
                        shakeDetector,
                        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                        SensorManager.SENSOR_DELAY_UI
                    )
                } else {
                    shakeDetector?.let { sensorManager.unregisterListener(it) }
                    shakeDetector = null
                }
            }

            BreakFreeTheme(appTheme = theme) {
                AppNavHost(activity = this, trigger = trigger, onNavControllerCreated = { navController = it })
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        shakeDetector?.let { sensorManager.unregisterListener(it) }
    }

    private fun captureScreenshot(): String? {
        val rootView = window.decorView.rootView
        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        rootView.draw(canvas)
        
        val file = File(cacheDir, "shake_screenshot_${System.currentTimeMillis()}.png")
        return try {
            val out = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.flush()
            out.close()
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
private fun AppNavHost(activity: MainActivity, trigger: Int, onNavControllerCreated: (NavController) -> Unit) {
    val navController = rememberNavController()
    
    LaunchedEffect(navController) {
        onNavControllerCreated(navController)
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            // Using trigger here ensures this block recomposes when trigger changes
            val currentTrigger = trigger
            HomeScreen(
                onOpenAppList = { navController.navigate("apps") },
                onOpenDomainList = { navController.navigate("domains") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenBreakManagement = { navController.navigate("break") },
                onOpenFeedback = { navController.navigate("feedback") },
                onEnableAccessibility = { activity.openAccessibilitySettings() },
                onEnableUsageStats = { activity.openUsageStatsSettings() },
                onEnableOverlay = { activity.openOverlaySettings() },
                onEnableNotifications = { activity.requestNotificationPermission() },
                isAccessibilityEnabled = { activity.isAccessibilityServiceEnabled() },
                isUsageStatsEnabled = { activity.isUsageStatsPermissionGranted() },
                isOverlayEnabled = { activity.isOverlayPermissionGranted() },
                isNotificationsEnabled = { activity.isNotificationPermissionGranted() },
                refreshTrigger = currentTrigger
            )
        }
        composable("apps") {
            AppListScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("domains") {
            DomainListScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("settings") { 
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenDoomscrollWhitelist = { navController.navigate("doomscroll_whitelist") }
            ) 
        }
        composable("doomscroll_whitelist") {
            DoomscrollWhitelistScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable("break") {
            BreakScreen(
                onBack = {
                    // Always go back to home from break management screen
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
        composable(
            route = "feedback?screenshot={screenshot}",
            arguments = listOf(navArgument("screenshot") { 
                type = NavType.StringType
                nullable = true
                defaultValue = null
            })
        ) { backStackEntry ->
            val screenshot = backStackEntry.arguments?.getString("screenshot")
            FeedbackScreen(
                initialScreenshotPath = screenshot,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
