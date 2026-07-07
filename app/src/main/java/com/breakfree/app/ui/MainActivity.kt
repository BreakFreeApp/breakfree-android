package com.breakfree.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AppOpsManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.settings.AppTheme
import com.breakfree.app.ui.screens.AppPickerScreen
import com.breakfree.app.ui.screens.BreakScreen
import com.breakfree.app.ui.screens.DomainListScreen
import com.breakfree.app.ui.screens.HomeScreen
import com.breakfree.app.ui.screens.SettingsScreen
import com.breakfree.app.ui.theme.BreakFreeTheme

class MainActivity : ComponentActivity() {
    private var permissionRefreshTrigger by mutableStateOf(0)

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
        val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
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
                onOpenAppPicker = { navController.navigate("apps") },
                onOpenDomainList = { navController.navigate("domains") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenBreakManagement = { navController.navigate("break") },
                onEnableAccessibility = { activity.openAccessibilitySettings() },
                onEnableUsageStats = { activity.openUsageStatsSettings() },
                onEnableOverlay = { activity.openOverlaySettings() },
                isAccessibilityEnabled = { activity.isAccessibilityServiceEnabled() },
                isUsageStatsEnabled = { activity.isUsageStatsPermissionGranted() },
                isOverlayEnabled = { activity.isOverlayPermissionGranted() }
            )
        }
        composable("apps") { AppPickerScreen(onBack = { navController.popBackStack() }) }
        composable("domains") { DomainListScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
        composable("break") { BreakScreen(onBack = { navController.popBackStack() }) }
    }
}
