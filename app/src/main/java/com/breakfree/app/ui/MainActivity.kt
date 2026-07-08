package com.breakfree.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.settings.AppTheme
import com.breakfree.app.ui.screens.AppListScreen
import com.breakfree.app.ui.screens.BreakScreen
import com.breakfree.app.ui.screens.DomainListScreen
import com.breakfree.app.ui.screens.HomeScreen
import com.breakfree.app.ui.screens.SettingsScreen
import com.breakfree.app.ui.theme.BreakFreeTheme

class MainActivity : ComponentActivity() {
    private var permissionRefreshTrigger by mutableStateOf(0)

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
            android.net.Uri.parse("package:$packageName")
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
        
        val settings = BreakFreeApplication.from(application).settingsDataStore
        setContent {
            val theme by settings.theme.collectAsState(initial = AppTheme.SYSTEM)
            // Trigger recomposition when permissionRefreshTrigger changes
            val trigger = permissionRefreshTrigger 
            BreakFreeTheme(appTheme = theme) {
                AppNavHost(activity = this, trigger = trigger)
            }
        }
    }
}

@Composable
private fun AppNavHost(activity: MainActivity, trigger: Int) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            // Using trigger here ensures this block recomposes when trigger changes
            val currentTrigger = trigger
            HomeScreen(
                onOpenAppList = { navController.navigate("apps") },
                onOpenDomainList = { navController.navigate("domains") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenBreakManagement = { navController.navigate("break") },
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
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable("break") {
            BreakScreen(
                onBack = {
                    // Always go back to home from break management screen
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}
