package com.breakfree.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Coffee
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toColorLong
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeUiState
import com.breakfree.app.ui.HomeViewModel
import com.breakfree.app.ui.components.PermissionInfo
import com.breakfree.app.ui.components.PermissionsCard
import com.breakfree.app.ui.components.SearchTopAppBar

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
                    .padding(horizontal = 24.dp, vertical = 16.dp)
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
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
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
                            BreakPhase.NONE -> "Request a break"
                            BreakPhase.GRACE -> "Break Pending..."
                            BreakPhase.CHALLENGE -> "Confirm Break"
                            BreakPhase.ACTIVE -> "End the break (${formatCountdown(state.activeSecondsRemaining)})"
                        }
                        Text(label, style = MaterialTheme.typography.titleMedium)
                    }
                }

                // Balance the vertical centering
                Spacer(modifier = Modifier.weight(1f))

                // 4: Bottom Section (Contribute)
                ContributeSection(
                    onFeedback = onOpenFeedback,
                    onGitHub = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BreakFreeApp/breakfree-android"))
                        context.startActivity(intent)
                    },
                    onCoffee = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ko-fi.com/cesarem"))
                        context.startActivity(intent)
                    }
                )
            }
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
                text = "GitHub",
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
    var selectedTab by remember { mutableStateOf(0) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column() {
            Row(
                modifier = Modifier.fillMaxWidth().padding(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                StatTabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.List,
                    label = "Apps",
                    isSelected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                StatTabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Schedule,
                    label = "Interval",
                    value = formatValue(state.timeBetweenBreaks.val24h, true),
                    isSelected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                StatTabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.History,
                    label = "Breaks",
                    value = "%.1f".format(state.dailyBreaksCount.val24h),
                    isSelected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
                StatTabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Timer,
                    label = "Break Time",
                    value = formatValue(state.dailyBreakTime.val24h, true),
                    isSelected = selectedTab == 3,
                    onClick = { selectedTab = 3 }
                )
                StatTabItem(
                    modifier = Modifier.weight(1f),
                    icon = Icons.AutoMirrored.Filled.TrendingUp,
                    label = "Usage",
                    value = formatValue(state.dailyScreenTime.val24h, true),
                    isSelected = selectedTab == 4,
                    onClick = { selectedTab = 4 }
                )
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Main Content Area
                Box(modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
                    when (selectedTab) {
                        0 -> MostUsedAppsContent(state)
                        1 -> StatComparisonContent("Time Between Breaks", state.timeBetweenBreaks)
                        2 -> StatComparisonContent("Daily Breaks Count", state.dailyBreaksCount)
                        3 -> StatComparisonContent("Daily Break Time", state.dailyBreakTime)
                        4 -> StatComparisonContent("Daily Screen Usage", state.dailyScreenTime)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
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
}

@Composable
private fun StatTabItem(
    modifier: Modifier,
    icon: ImageVector,
    label: String,
    value: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    first: Boolean = false,
    last: Boolean = false,
) {
    var backgroundColor = MaterialTheme.colorScheme.onPrimary
    if (isSelected){
        backgroundColor = MaterialTheme.colorScheme.primaryContainer
    }

    var shape = RoundedCornerShape(0)
    if (first) {
        shape = RoundedCornerShape(topStart = 16.dp)
    }
    if (last){
        shape = RoundedCornerShape(topEnd = 16.dp)
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .background(backgroundColor,shape=shape)
    ){
        Column(
            modifier = Modifier.fillMaxSize().fillMaxHeight().fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,

        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (value != null) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MostUsedAppsContent(state: HomeUiState) {
    val suggested = remember(state.apps) {
        state.apps
            .filter { !it.isBlocked }
            .sortedByDescending { it.usageTimeMs }
            .take(3)
    }

    Column {
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
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No usage data available yet", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StatComparisonContent(title: String, summary: com.breakfree.app.ui.StatSummary) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatComparisonItem("Last 24h", summary.val24h, summary.val7d, summary.isTime)
            StatComparisonItem("Last 7d", summary.val7d, summary.val7d, summary.isTime, isBaseline = true)
            StatComparisonItem("Last 30d", summary.val30d, summary.val7d, summary.isTime)
        }
    }
}

@Composable
private fun StatComparisonItem(
    label: String,
    value: Double,
    baseline: Double,
    isTime: Boolean,
    isBaseline: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = formatValue(value, isTime),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        

        val diff = value - baseline
        val percent = if (baseline > 0) (diff / baseline) * 100 else 0.0

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (diff >= 0) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (diff >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
            Text(
                text = "%.0f%%".format(Math.abs(percent)),
                style = MaterialTheme.typography.labelSmall,
                color = if (diff >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
            )
        }
    }
}

private fun formatValue(value: Double, isTime: Boolean): String {
    if (!isTime) return "%.1f".format(value)
    
    val seconds = value / 1000
    return when {
        seconds < 60 -> "%.0fs".format(seconds)
        seconds < 3600 -> "%.0fm".format(seconds / 60)
        else -> {
            val h = seconds / 3600.0
            if (h >= 10.0) "%.0fh".format(h) else "%.1fh".format(h)
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
