package com.breakfree.app.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.breakfree.app.service.BreakFreeAccessibilityService
import com.breakfree.app.service.BreakFreeVpnService
import com.breakfree.app.ui.screens.AppPickerScreen
import com.breakfree.app.ui.screens.DomainListScreen
import com.breakfree.app.ui.screens.HomeScreen
import com.breakfree.app.ui.screens.SettingsScreen
import com.breakfree.app.ui.theme.BreakFreeTheme

class MainActivity : ComponentActivity() {

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            BreakFreeVpnService.start(this)
        }
    }

    fun requestVpnPermissionAndStart() {
        val intent = BreakFreeVpnService.prepareIntent(this)
        if (intent != null) vpnPermissionLauncher.launch(intent) else BreakFreeVpnService.start(this)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any { it.resolveInfo.serviceInfo.packageName == packageName }
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
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
                onEnableVpn = { activity.requestVpnPermissionAndStart() },
                isAccessibilityEnabled = { activity.isAccessibilityServiceEnabled() }
            )
        }
        composable("apps") { AppPickerScreen(onBack = { navController.popBackStack() }) }
        composable("domains") { DomainListScreen(onBack = { navController.popBackStack() }) }
        composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
    }
}
