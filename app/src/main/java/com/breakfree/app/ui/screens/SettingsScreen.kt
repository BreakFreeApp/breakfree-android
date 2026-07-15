package com.breakfree.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.settings.AppTheme
import com.breakfree.app.ui.SettingsViewModel
import com.breakfree.app.ui.components.SearchTopAppBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDoomscrollWhitelist: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "Settings",
                onBack = onBack,
                searchEnabled = false,
                showLogo = false
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Protection & Goals
            SettingsSection(title = "Protection & Goals") {
                SettingsSwitchItem(
                    title = "Doomscrolling Protection",
                    description = "Warn you when you spend too much time on potential time-sink apps.",
                    checked = state.doomscrollingProtectionEnabled,
                    onCheckedChange = { viewModel.setDoomscrollingProtectionEnabled(it) }
                )

                if (state.doomscrollingProtectionEnabled) {
                    Spacer(modifier = Modifier.height(16.dp))
                    TargetScreenTimeSlider(
                        currentMins = state.targetScreenTimeMinutes,
                        onValueChange = { viewModel.setTargetScreenTimeMinutes(it) }
                    )

                    OutlinedButton(
                        onClick = onOpenDoomscrollWhitelist,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Whitelist Applications")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                val graceOptions = com.breakfree.app.data.settings.AppDefaults.GRACE_PERIOD_OPTIONS
                val graceIndex = graceOptions.indexOf(state.gracePeriodSeconds).coerceAtLeast(0)
                val graceLabel = if (state.gracePeriodSeconds == 0) "Disabled" else "${state.gracePeriodSeconds}s"

                SettingsSliderItem(
                    title = "Grace period",
                    description = "How long you must wait after requesting a break before it starts.",
                    value = graceIndex.toFloat(),
                    onValueChange = { viewModel.setGracePeriod(graceOptions[it.roundToInt()]) },
                    valueRange = 0f..(graceOptions.size - 1).toFloat(),
                    steps = graceOptions.size - 2,
                    valueLabel = graceLabel
                )
            }

            // Section 2: Notifications & Behavior
            SettingsSection(title = "Notifications & Behavior") {
                SettingsSwitchItem(
                    title = "Break Notification",
                    description = "Show a persistent notification with a countdown during an active break.",
                    checked = state.showBreakNotification,
                    onCheckedChange = { viewModel.setShowBreakNotification(it) }
                )

                Spacer(modifier = Modifier.height(16.dp))

                val autoStopOptions = listOf(0, 1, 2, 5, 10)
                val autoStopIndex = autoStopOptions.indexOf(state.autoStopOnLockTimeoutMinutes).coerceAtLeast(0)
                val autoStopLabel = if (state.autoStopOnLockTimeoutMinutes == 0) "Disabled" else "${state.autoStopOnLockTimeoutMinutes} min"

                SettingsSliderItem(
                    title = "Auto-stop break when locked",
                    description = "Automatically stop an active break if the screen remains off for the specified time.",
                    value = autoStopIndex.toFloat(),
                    onValueChange = { viewModel.setAutoStopOnLockTimeout(autoStopOptions[it.roundToInt()]) },
                    valueRange = 0f..4f,
                    steps = 3,
                    valueLabel = autoStopLabel
                )
            }

            SettingsSection(title = "Feedbacks") {

                SettingsSwitchItem(
                    title = "Shake to send feedback",
                    description = "Shake your device to quickly capture a screenshot and send feedback.",
                    checked = state.shakeToSendFeedback,
                    onCheckedChange = { viewModel.setShakeToSendFeedback(it) }
                )
            }

            // Section 3: Appearance
            SettingsSection(title = "Appearance") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppTheme.entries.forEach { theme ->
                        val selected = state.theme == theme
                        Button(
                            onClick = { viewModel.setTheme(theme) },
                            modifier = Modifier.weight(1f),
                            colors = if (selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                        ) {
                            Text(theme.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
        )
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                content = content
            )
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int = 0,
    valueLabel: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(description, style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(vertical = 8.dp)
        )
    }
}

@Composable
private fun TargetScreenTimeSlider(
    currentMins: Int,
    onValueChange: (Int) -> Unit
) {
    val options = listOf(30, 60, 120, 180, 240, 300, 360, 420, 480)
    val currentIndex = options.indexOf(currentMins).coerceAtLeast(0)
    
    val hours = currentMins / 60
    val mins = currentMins % 60
    val label = if (mins == 0) "${hours}h" else if (hours == 0) "${mins}m" else "${hours}h ${mins}m"

    SettingsSliderItem(
        title = "Target screen time",
        description = "Your ideal daily total screen time.",
        value = currentIndex.toFloat(),
        onValueChange = { onValueChange(options[it.roundToInt()]) },
        valueRange = 0f..(options.size - 1).toFloat(),
        steps = options.size - 2,
        valueLabel = label
    )
}
