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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.breakfree.app.ui.screens.AppPickerScreen
import com.breakfree.app.ui.screens.DomainListScreen
import com.breakfree.app.ui.screens.HomeScreen
import com.breakfree.app.ui.screens.SettingsScreen
import com.breakfree.app.ui.theme.BreakFreeTheme

class MainActivity : ComponentActivity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BreakFreeTheme {
                AppNavHost(activity = this)
            }
        }
    }
}

@Composable
private fun AppNavHost(activity: MainActivity) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenAppPicker = { navController.navigate("apps") },
                onOpenDomainList = { navController.navigate("domains") },
                onOpenSettings = { navController.navigate("settings") },
                onEnableAccessibility = { activity.openAccessibilitySettings() },
                onEnableUsageStats = { activity.openUsageStatsSettings() },
                isAccessibilityEnabled = { activity.isAccessibilityServiceEnabled() },
                isUsageStatsEnabled = { activity.isUsageStatsPermissionGranted() }
            )
        }
        composable("apps") { AppPickerScreen(onBack = { navController.popBackStack() }) }
        composable("domains") { DomainListScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
