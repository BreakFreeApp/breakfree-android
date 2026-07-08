package com.breakfree.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.settings.AppTheme
import com.breakfree.app.ui.SettingsViewModel
import com.breakfree.app.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "Settings",
                onBack = onBack,
                searchEnabled = false
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("Grace period: ${state.gracePeriodSeconds}s", style = MaterialTheme.typography.titleSmall)
            Text(
                "How long you must wait after requesting a break before it starts.",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = state.gracePeriodSeconds.toFloat(),
                onValueChange = { viewModel.setGracePeriod(it.toInt()) },
                valueRange = 5f..120f,
                steps = 22,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Break Notification", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Show a persistent notification with a countdown during an active break.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = state.showBreakNotification,
                    onCheckedChange = { viewModel.setShowBreakNotification(it) }
                )
            }

            Text("Appearance", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 24.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppTheme.values().forEach { theme ->
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

            // Auto-stop on lock setting
            val autoStopOptions = listOf(0, 1, 2, 5, 10)
            val autoStopIndex = autoStopOptions.indexOf(state.autoStopOnLockTimeoutMinutes).coerceAtLeast(0)
            
            Text(
                "Auto-stop break when locked: ${if (state.autoStopOnLockTimeoutMinutes == 0) "Disabled" else "${state.autoStopOnLockTimeoutMinutes} min"}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                "Automatically stop an active break if the screen remains off for the specified time.",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = autoStopIndex.toFloat(),
                onValueChange = { index ->
                    viewModel.setAutoStopOnLockTimeout(autoStopOptions[index.toInt()])
                },
                valueRange = 0f..4f,
                steps = 3,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }
    }
}
