package com.breakfree.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Strict mode", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Breaks can't be cancelled early once requested.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = state.strictMode, onCheckedChange = { viewModel.setStrictMode(it) })
            }
        }
    }
}
