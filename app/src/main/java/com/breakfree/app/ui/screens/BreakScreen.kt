package com.breakfree.app.ui.screens

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.R
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeViewModel
import com.breakfree.app.ui.components.SearchTopAppBar
import kotlin.random.Random

data class ChallengeUiState(
    val swapped: Boolean = false,
    val yesCapital: Boolean = false,
    val noCapital: Boolean = false,
    val yesHighlighted: Boolean = true,
    val noHighlighted: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreakScreen(onBack: () -> Unit, viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    
    var challengeUi by remember { mutableStateOf(ChallengeUiState()) }
    var selectedBreakDuration by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.phase) {
        if (state.phase == BreakPhase.CHALLENGE) {
            challengeUi = ChallengeUiState(
                swapped = Random.nextBoolean(),
                yesCapital = Random.nextBoolean(),
                noCapital = Random.nextBoolean(),
                yesHighlighted = Random.nextBoolean(),
                noHighlighted = Random.nextBoolean()
            )
        } else if (state.phase == BreakPhase.NONE) {
            selectedBreakDuration = null
        }
    }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "Break Management",
                onBack = onBack,
                searchEnabled = false
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (state.phase) {
                        BreakPhase.NONE -> {
                            Text("Request a break", style = MaterialTheme.typography.titleMedium)
                            
                            if (selectedBreakDuration == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val options = listOf(
                                        "1m" to 60,
                                        "5m" to 300,
                                        "10m" to 600,
                                        "30m" to 1800,
                                        "1h" to 3600
                                    )
                                    options.forEach { (label, secs) ->
                                        OutlinedButton(
                                            onClick = { selectedBreakDuration = secs },
                                            modifier = Modifier.weight(1f),
                                            contentPadding = PaddingValues(horizontal = 4.dp)
                                        ) {
                                            Text(label, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { viewModel.requestBreak(selectedBreakDuration!!) },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Request Break")
                                    }
                                    OutlinedButton(
                                        onClick = { selectedBreakDuration = null },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                        }
                        BreakPhase.GRACE -> {
                            Text("Friction active", style = MaterialTheme.typography.titleMedium)
                            Button(
                                onClick = {},
                                enabled = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Wait... ${formatCountdown(state.graceSecondsRemaining)}")
                            }
                            OutlinedButton(
                                onClick = { viewModel.cancelBreak() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel")
                            }
                        }
                        BreakPhase.CHALLENGE -> {
                            Text("Confirm break?", style = MaterialTheme.typography.titleMedium)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                val yesLabel = if (challengeUi.yesCapital) "YES" else "yes"
                                val noLabel = if (challengeUi.noCapital) "NO" else "no"
                                
                                val yesButton = @Composable {
                                    Button(
                                        onClick = { viewModel.confirmBreak() },
                                        modifier = Modifier.weight(1f),
                                        colors = if (challengeUi.yesHighlighted) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                                    ) {
                                        Text(yesLabel)
                                    }
                                }
                                
                                val noButton = @Composable {
                                    Button(
                                        onClick = { viewModel.cancelBreak() },
                                        modifier = Modifier.weight(1f),
                                        colors = if (challengeUi.noHighlighted) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                                    ) {
                                        Text(noLabel)
                                    }
                                }
                                
                                if (challengeUi.swapped) {
                                    noButton()
                                    yesButton()
                                } else {
                                    yesButton()
                                    noButton()
                                }
                            }
                        }
                        BreakPhase.ACTIVE -> {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .padding(bottom = 16.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text("Break active", style = MaterialTheme.typography.titleLarge)
                                    Text(formatCountdown(state.activeSecondsRemaining), style = MaterialTheme.typography.headlineMedium)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { viewModel.cancelBreak() },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Stop Break")
                                    }
                                }
                            }
                        }
                    }
                }
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
