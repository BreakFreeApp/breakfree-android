package com.breakfree.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.settings.AppDefaults
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeViewModel
import com.breakfree.app.ui.components.SearchTopAppBar
import kotlin.math.absoluteValue

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onOpenAppPicker: () -> Unit,
    onOpenDomainList: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableUsageStats: () -> Unit,
    isAccessibilityEnabled: () -> Boolean,
    isUsageStatsEnabled: () -> Boolean,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val accessibilityEnabled = isAccessibilityEnabled()
    val usageStatsEnabled = isUsageStatsEnabled()

    val fixedDurations = remember { AppDefaults.DURATION_OPTIONS }

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
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (!accessibilityEnabled) {
                SetupCard(
                    title = "App blocking needs Accessibility permission",
                    body = "Required to detect when a blocked app opens.",
                    actionLabel = "Enable",
                    onAction = onEnableAccessibility
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

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.phase) {
                        BreakPhase.NONE -> {
                            Text("Blocking is active", style = MaterialTheme.typography.titleMedium)
                            Text("${state.blockedAppCount} apps, ${state.blockedDomainCount} domains blocked")

                            val pagerState = rememberPagerState(initialPage = 1, pageCount = { fixedDurations.size })

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                VerticalPager(
                                    state = pagerState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(100.dp),
                                    contentPadding = PaddingValues(vertical = 30.dp)
                                ) { page ->
                                    val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = fixedDurations[page].label,
                                            style = MaterialTheme.typography.titleLarge,
                                            modifier = Modifier.graphicsLayer {
                                                val scale = 1f - (pageOffset * 0.3f).coerceIn(0f, 0.3f)
                                                scaleX = scale
                                                scaleY = scale
                                                alpha = 1f - (pageOffset * 0.5f).coerceIn(0f, 0.5f)
                                            }
                                        )
                                    }
                                }
                                Button(
                                    onClick = { viewModel.requestBreak(fixedDurations[pagerState.currentPage].seconds) },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Start Break")
                                }
                            }
                        }
                        BreakPhase.GRACE -> {
                            Text("Break requested…", style = MaterialTheme.typography.titleMedium)
                            Text("Starts in ${state.graceSecondsRemaining}s — this pause is intentional.")
                            if (!state.strictMode) {
                                Button(onClick = { viewModel.cancelBreak() }) { Text("Cancel") }
                            }
                        }
                        BreakPhase.ACTIVE -> {
                            Text("Break active", style = MaterialTheme.typography.titleMedium)
                            Text("${state.activeSecondsRemaining}s remaining, then blocking resumes automatically.")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            OutlinedButton(onClick = onOpenAppPicker, modifier = Modifier.fillMaxWidth()) { Text("Blocked apps") }
            OutlinedButton(onClick = onOpenDomainList, modifier = Modifier.fillMaxWidth()) { Text("Blocked domains") }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, actionLabel: String, onAction: () -> Unit ) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
