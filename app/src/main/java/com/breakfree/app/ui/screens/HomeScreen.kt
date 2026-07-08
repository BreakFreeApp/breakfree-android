package com.breakfree.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeUiState
import com.breakfree.app.ui.HomeViewModel
import com.breakfree.app.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenAppPicker: () -> Unit,
    onOpenDomainList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBreakManagement: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableUsageStats: () -> Unit,
    onEnableOverlay: () -> Unit,
    onEnableNotifications: () -> Unit,
    isAccessibilityEnabled: () -> Boolean,
    isUsageStatsEnabled: () -> Boolean,
    isOverlayEnabled: () -> Boolean,
    isNotificationsEnabled: () -> Boolean,
    refreshTrigger: Int = 0,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    
    // Use remember(refreshTrigger) to ensure these values are re-calculated when the trigger changes
    val accessibilityEnabled = remember(refreshTrigger) { isAccessibilityEnabled() }
    val usageStatsEnabled = remember(refreshTrigger) { isUsageStatsEnabled() }
    val overlayEnabled = remember(refreshTrigger) { isOverlayEnabled() }
    val notificationsEnabled = remember(refreshTrigger) { isNotificationsEnabled() }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "BreakFree",
                searchEnabled = false,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatsAndSuggestionsCard(state, onOpenAppPicker, onOpenDomainList)

            if (!accessibilityEnabled) {
                SetupCard(
                    title = "App blocking needs Accessibility permission",
                    body = "Required to detect when a blocked app opens.",
                    actionLabel = "Enable",
                    onAction = onEnableAccessibility,
                    isEnabled = accessibilityEnabled
                )
            }

            if (!usageStatsEnabled) {
                SetupCard(
                    title = "Usage sorting needs Permission",
                    body = "Required to sort apps by your actual usage time.",
                    actionLabel = "Enable",
                    onAction = onEnableUsageStats
                )
            }

            if (!overlayEnabled) {
                SetupCard(
                    title = "Better blocking needs Overlay permission",
                    body = "Required to show the block screen without switching apps.",
                    actionLabel = "Enable",
                    onAction = onEnableOverlay
                )
            }

            if (!notificationsEnabled) {
                SetupCard(
                    title = "Break timer needs Notification permission",
                    body = "Required to show a countdown while a break is active.",
                    actionLabel = "Enable",
                    onAction = onEnableNotifications
                )
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (state.phase == BreakPhase.ACTIVE) {
                        viewModel.cancelBreak()
                    } else {
                        onOpenBreakManagement()
                    }
                },
                colors = if (state.phase == BreakPhase.ACTIVE) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                } else {
                    CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black)
                }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val label = when(state.phase) {
                        BreakPhase.NONE -> "Request a Break"
                        BreakPhase.GRACE -> "Break Pending..."
                        BreakPhase.CHALLENGE -> "Confirm Break"
                        BreakPhase.ACTIVE -> "End the Break (${formatCountdown(state.activeSecondsRemaining)})"
                    }
                    Text(label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun StatsAndSuggestionsCard(state: HomeUiState, onOpenAppPicker: () -> Unit, onOpenDomainList: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val suggested = remember(state.apps, state.targetDailyHours) {
                val weeklyTargetMs = state.targetDailyHours * 7 * 3600 * 1000L
                if (state.weeklyUsageMs > weeklyTargetMs) {
                    state.apps
                        .filter { !it.isBlocked }
                        .sortedByDescending { it.usageTimeMs }
                        .take(3)
                } else {
                    emptyList()
                }
            }

            if (suggested.isNotEmpty()) {
                Text("Most used apps", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(8.dp))
                suggested.forEach { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val appDailyAvgMs = app.usageTimeMs / 7.0
                        val appHours = appDailyAvgMs / (1000.0 * 3600.0)
                        val appMinutes = appDailyAvgMs / (1000.0 * 60.0)
                        val appUsageText = if (appHours >= 1.0) "%.1f hour".format(appHours) else "%.0f min".format(appMinutes)
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                        Text(appUsageText, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Compact icon-based stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                val now = System.currentTimeMillis()
                val diffMs = if (state.lastBreakRequestTime > 0) now - state.lastBreakRequestTime else 0L
                val timeSinceText = when {
                    diffMs < 60_000 -> "${diffMs / 1000}s"
                    diffMs < 3600_000 -> "${diffMs / 60_000}m"
                    diffMs < 86400_000 -> "${diffMs / 3600_000}h"
                    else -> "${diffMs / 86400_000}d"
                }
                
                StatItem(
                    icon = Icons.Default.Schedule, 
                    text = timeSinceText,
                    onClick = { Toast.makeText(context, "Time since last break", Toast.LENGTH_SHORT).show() }
                )
                StatItem(
                    icon = Icons.Default.History, 
                    text = "${state.totalBreaksCount}", 
                    onClick = { Toast.makeText(context, "Number of breaks", Toast.LENGTH_SHORT).show() }
                )
                StatItem(
                    icon = Icons.Default.Timer, 
                    text = "${state.totalBreakTimeMs / (1000 * 60)}m", 
                    onClick = { Toast.makeText(context, "Daily break time", Toast.LENGTH_SHORT).show() }
                )
                
                val dailyAverageMs = state.weeklyUsageMs / 7.0
                val hours = dailyAverageMs / (1000.0 * 3600.0)
                val minutes = dailyAverageMs / (1000.0 * 60.0)
                val usageText = if (hours >= 1.0) "%.1fh".format(hours) else "%.0fm".format(minutes)
                StatItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp, 
                    text = usageText, 
                    onClick = { Toast.makeText(context, "Daily usage", Toast.LENGTH_SHORT).show() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenAppPicker, modifier = Modifier.weight(1f)) {
                    Text("Apps (${state.blockedAppCount})", maxLines = 1)
                }
                Button(onClick = onOpenDomainList, modifier = Modifier.weight(1f)) {
                    Text("Web (${state.blockedDomainCount})", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(onClick = onClick).padding(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SetupCard(title: String, body: String, actionLabel: String, onAction: () -> Unit, isEnabled: Boolean = false) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (isEnabled) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Enabled",
                        tint = Color(0xFF4CAF50),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(body, style = MaterialTheme.typography.bodySmall)
            if (!isEnabled) {
                Button(onClick = onAction) { Text(actionLabel) }
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
