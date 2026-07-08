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
import androidx.compose.material3.Slider
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
import com.breakfree.android.R
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
    
    val durationOptions = remember {
        listOf(
            "1m" to 60,
            "5m" to 300,
            "10m" to 600,
            "30m" to 1800,
            "1h" to 3600,
            "2h" to 7200
        )
    }
    var sliderValue by remember { mutableStateOf(1f) } // Default 5m

    LaunchedEffect(state.phase) {
        if (state.phase == BreakPhase.CHALLENGE) {
            challengeUi = ChallengeUiState(
                swapped = Random.nextBoolean(),
                yesCapital = Random.nextBoolean(),
                noCapital = Random.nextBoolean(),
                yesHighlighted = Random.nextBoolean(),
                noHighlighted = Random.nextBoolean()
            )
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.phase == BreakPhase.ACTIVE) {
                // Active Break UI (Centered)
                Spacer(modifier = Modifier.weight(1f))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(80.dp)
                                .padding(bottom = 16.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text("Break active", style = MaterialTheme.typography.headlineSmall)
                        Text(formatCountdown(state.activeSecondsRemaining), style = MaterialTheme.typography.displayMedium)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.cancelBreak() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Stop Break")
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                // Stepper UI for Requesting a Break
                
                // Step 1: Duration Selection
                val isSelectionActive = state.phase == BreakPhase.NONE
                val isSelectionCompleted = state.phase != BreakPhase.NONE
                
                StepCard(
                    title = "1. Choose Duration",
                    isCompleted = isSelectionCompleted,
                    isActive = isSelectionActive
                ) {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text(
                            "Duration: ${durationOptions[sliderValue.toInt()].first}",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Slider(
                            value = sliderValue,
                            onValueChange = { sliderValue = it },
                            valueRange = 0f..5f,
                            steps = 4,
                            enabled = isSelectionActive
                        )
                        if (isSelectionActive) {
                            Button(
                                onClick = { viewModel.requestBreak(durationOptions[sliderValue.toInt()].second) },
                                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                            ) {
                                Text("Request Break")
                            }
                        }
                    }
                }

                // Step 2: Grace Period
                val isGraceActive = state.phase == BreakPhase.GRACE
                val isGraceCompleted = state.phase == BreakPhase.CHALLENGE
                val isGraceVisible = isGraceActive || isGraceCompleted || isSelectionActive
                
                if (isGraceVisible) {
                    StepCard(
                        title = "2. Friction Period",
                        isCompleted = isGraceCompleted,
                        isActive = isGraceActive
                    ) {
                        if (isGraceActive || isGraceCompleted) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                if (isGraceActive) {
                                    Text("Wait for the friction period to end...", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Wait... ${formatCountdown(state.graceSecondsRemaining)}")
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.cancelBreak() },
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                                    ) {
                                        Text("Cancel")
                                    }
                                } else {
                                    Text("Friction period completed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        } else {
                            Text("Waiting for request...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                // Step 3: Challenge
                val isChallengeActive = state.phase == BreakPhase.CHALLENGE
                val isChallengeVisible = isChallengeActive || isGraceActive
                
                if (isChallengeVisible) {
                    StepCard(
                        title = "3. Confirm Break",
                        isCompleted = false,
                        isActive = isChallengeActive
                    ) {
                        if (isChallengeActive) {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                Text("Are you sure you want to start the break?", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(16.dp))
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
                        } else {
                            Text("Confirmation will appear here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
    isCompleted: Boolean,
    isActive: Boolean,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isActive) 4.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isCompleted) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.checkbox_on_background),
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            content()
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
