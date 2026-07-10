package com.breakfree.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Feedback
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeUiState
import com.breakfree.app.ui.HomeViewModel
import com.breakfree.app.ui.components.PermissionInfo
import com.breakfree.app.ui.components.PermissionsCard
import com.breakfree.app.ui.components.SearchTopAppBar
import androidx.core.net.toUri
import com.breakfree.app.ui.FeedbackViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenAppList: () -> Unit,
    onOpenDomainList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBreakManagement: () -> Unit,
    onOpenFeedback: () -> Unit,
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
    val onCancelBreak = { viewModel.cancelBreak() }
    val context = LocalContext.current
    
    val accessibilityEnabled = remember(refreshTrigger) { isAccessibilityEnabled() }
    val usageStatsEnabled = remember(refreshTrigger) { isUsageStatsEnabled() }
    val overlayEnabled = remember(refreshTrigger) { isOverlayEnabled() }
    val notificationsEnabled = remember(refreshTrigger) { isNotificationsEnabled() }

    LaunchedEffect(usageStatsEnabled) {
        if (usageStatsEnabled) {
            viewModel.onUsagePermissionGranted()
        }
    }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "BreakFree",
                searchEnabled = false,
                showLogo = true,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val minHeight = maxHeight
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .heightIn(min = minHeight)
            ) {
                // 1 & 2: Top Sections
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    StatsAndSuggestionsCard(state, onOpenAppList, onOpenDomainList)
                    
                    val permissions = remember(accessibilityEnabled, usageStatsEnabled, overlayEnabled, notificationsEnabled) {
                        listOf(
                            PermissionInfo(
                                name = "Accessibility",
                                description = "Required to detect and block apps.",
                                isGranted = accessibilityEnabled,
                                onEnable = onEnableAccessibility
                            ),
                            PermissionInfo(
                                name = "Usage Stats",
                                description = "Required to track app usage time.",
                                isGranted = usageStatsEnabled,
                                onEnable = onEnableUsageStats
                            ),
                            PermissionInfo(
                                name = "Overlay",
                                description = "Required to show the block screen.",
                                isGranted = overlayEnabled,
                                onEnable = onEnableOverlay
                            ),
                            PermissionInfo(
                                name = "Notifications",
                                description = "Required for the break timer.",
                                isGranted = notificationsEnabled,
                                onEnable = onEnableNotifications
                            )
                        )
                    }
                    PermissionsCard(permissions = permissions)
                }

                // Center the break button in the remaining space
                Spacer(modifier = Modifier.weight(1f))

                // 3: Middle Section (Break Button)
                StartStopBreakSection(state, onCancelBreak ,onOpenBreakManagement )

                // Balance the vertical centering
                Spacer(modifier = Modifier.weight(1f))

                // 4: Bottom Section (Contribute)
                ContributeSection(
                    onFeedback = onOpenFeedback,
                    onGitHub = {
                        val intent = Intent(Intent.ACTION_VIEW,
                            "https://github.com/BreakFreeApp/breakfree-android".toUri())
                        context.startActivity(intent)
                    },
                    onCoffee = {
                        val intent = Intent(Intent.ACTION_VIEW, "https://ko-fi.com/cesarem".toUri())
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}

@Composable
private fun StartStopBreakSection(state: HomeUiState, onCancelBreak: ()->Unit, onOpenBreakManagement: ()->Unit  ) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        onClick = {
            if (state.phase == BreakPhase.ACTIVE) {
                onCancelBreak()
            } else {
                onOpenBreakManagement()
            }
        },
        colors = if (state.phase == BreakPhase.ACTIVE) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
        } else {
            CardDefaults.cardColors(containerColor = Color.White, contentColor = Color.Black)
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val label = when(state.phase) {
                BreakPhase.NONE -> "Request a break"
                BreakPhase.GRACE -> "Break Pending..."
                BreakPhase.CHALLENGE -> "Confirm Break"
                BreakPhase.ACTIVE -> "End the break (${formatCountdown(state.activeSecondsRemaining)})"
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun ContributeSection(onFeedback: () -> Unit, onGitHub: () -> Unit, onCoffee: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Text(
            text = "Contribute",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp),
            textAlign = TextAlign.Center
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContributeButton(
                icon = Icons.Default.Feedback,
                text = "Feedback",
                onClick = onFeedback,
                modifier = Modifier.weight(1f)
            )
            ContributeButton(
                icon = Icons.Default.Code,
                text = "Code",
                onClick = onGitHub,
                modifier = Modifier.weight(1f)
            )
            ContributeButton(
                icon = Icons.Default.Coffee,
                text = "Coffee",
                onClick = onCoffee,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ContributeButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.aspectRatio(1f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun StatsAndSuggestionsCard(state: HomeUiState, onOpenAppList: () -> Unit, onOpenDomainList: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val suggested = remember(state.apps) {
                state.apps
                    .filter { !it.isBlocked }
                    .sortedByDescending { it.usageTimeMs }
                    .take(3)
            }

            if (suggested.isNotEmpty() && suggested.any { it.usageTimeMs > 0 }) {
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
                        val appUsageText = if (appHours >= 1.0) "%.1f h".format(appHours) else "%.0f m".format(appMinutes)
                        Text(app.appName, style = MaterialTheme.typography.bodyMedium)
                        Text(appUsageText, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val context = LocalContext.current
                
                StatItem(
                    icon = Icons.Default.Schedule, 
                    text = formatDurationStat(state.avgTimeBetweenBreaksMs),
                    onClick = { Toast.makeText(context, "Avg time between breaks (daily)", Toast.LENGTH_SHORT).show() }
                )
                
                StatItem(
                    icon = Icons.Default.History, 
                    text = "%.1f".format(state.avgDailyBreaksCount), 
                    onClick = { Toast.makeText(context, "Avg daily number of breaks", Toast.LENGTH_SHORT).show() }
                )
                
                StatItem(
                    icon = Icons.Default.Timer, 
                    text = formatDurationStat(state.avgDailyBreakTimeMs), 
                    onClick = { Toast.makeText(context, "Daily average break time", Toast.LENGTH_SHORT).show() }
                )
                
                StatItem(
                    icon = Icons.AutoMirrored.Filled.TrendingUp, 
                    text = formatDurationStat(state.avgDailyScreenTimeMs), 
                    onClick = { Toast.makeText(context, "Daily average screen time", Toast.LENGTH_SHORT).show() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(0.9f).align(Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpenAppList, modifier = Modifier.weight(1f)) {
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
private fun StatItem(icon: ImageVector, text: String, onClick: () -> Unit) {
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

private fun formatDurationStat(ms: Long): String {
    val seconds = ms / 1000
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m"
        else -> {
            val h = seconds / 3600.0
            if (h >= 10.0) "%.0fh".format(h) else "%.1fh".format(h)
        }
    }
}
