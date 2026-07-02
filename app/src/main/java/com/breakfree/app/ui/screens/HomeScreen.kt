package com.breakfree.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun HomeScreen(
    onOpenAppPicker: () -> Unit,
    onOpenDomainList: () -> Unit,
    onOpenSettings: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onEnableVpn: () -> Unit,
    isAccessibilityEnabled: () -> Boolean,
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val accessibilityEnabled = isAccessibilityEnabled()

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("BreakFree", style = MaterialTheme.typography.headlineMedium)

            if (!accessibilityEnabled) {
                SetupCard(
                    title = "App blocking needs Accessibility permission",
                    body = "Required to detect when a blocked app opens.",
                    actionLabel = "Enable",
                    onAction = onEnableAccessibility
                )
            }

            SetupCard(
                title = "Domain blocking runs via a local VPN",
                body = "Nothing leaves your device — it's a local DNS filter only.",
                actionLabel = "Enable",
                onAction = onEnableVpn
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.phase) {
                        BreakPhase.NONE -> {
                            Text("Blocking is active", style = MaterialTheme.typography.titleMedium)
                            Text("${state.blockedAppCount} apps, ${state.blockedDomainCount} domains blocked")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.durationOptions.forEach { option ->
                                    OutlinedButton(onClick = { viewModel.requestBreak(option.seconds) }) {
                                        Text(option.label)
                                    }
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
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Settings") }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, actionLabel: String, onAction: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
