package com.breakfree.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.android.R
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.HomeViewModel
import com.breakfree.app.ui.components.SearchTopAppBar
import kotlin.math.roundToInt
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
    
    // Internal UI state to manage the linear flow locally
    var internalFlowPhase by remember { mutableStateOf(BreakPhase.NONE) }
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

    // Ensure we always start from NONE when entering this screen
    LaunchedEffect(Unit) {
        if (state.phase != BreakPhase.NONE) {
            viewModel.cancelBreak()
        }
        internalFlowPhase = BreakPhase.NONE
    }

    // Sync internal flow with external state changes (like timer expiry)
    LaunchedEffect(state.phase) {
        if (state.phase == BreakPhase.CHALLENGE) {
            internalFlowPhase = BreakPhase.CHALLENGE
            challengeUi = ChallengeUiState(
                swapped = Random.nextBoolean(),
                yesCapital = Random.nextBoolean(),
                noCapital = Random.nextBoolean(),
                yesHighlighted = Random.nextBoolean(),
                noHighlighted = Random.nextBoolean()
            )
        } else if (state.phase == BreakPhase.ACTIVE) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "Request a break",
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Step 1: Duration Selection
            // Always visible and active if we are in Step 1
            val isStep1Active = internalFlowPhase == BreakPhase.NONE
            
            StepCard(
                title = "1. Choose Duration",
                isActive = isStep1Active
            ) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        "Duration: ${durationOptions[sliderValue.roundToInt()].first}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..5f,
                        steps = 4,
                        enabled = isStep1Active
                    )
                    if (isStep1Active) {
                        Button(
                            onClick = { 
                                viewModel.requestBreak(durationOptions[sliderValue.roundToInt()].second)
                                internalFlowPhase = BreakPhase.GRACE
                            },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                        ) {
                            Text("Request Break")
                        }
                    }
                }
            }

            // Step 2: Friction Period
            // Appears only when duration is pressed (GRACE or CHALLENGE phase)
            if (internalFlowPhase == BreakPhase.GRACE || internalFlowPhase == BreakPhase.CHALLENGE) {
                StepCard(
                    title = "2. Friction Period",
                    isActive = internalFlowPhase == BreakPhase.GRACE
                ) {
                    if (internalFlowPhase == BreakPhase.GRACE) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text("Wait for the friction period to end...", style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp).fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = formatCountdown(state.graceSecondsRemaining),
                                            style = MaterialTheme.typography.titleLarge,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = { 
                                        viewModel.cancelBreak()
                                        onBack()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Text("Cancel")
                                }
                            }
                        }
                    } else {
                        Text("Friction period completed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Step 3: Confirmation Challenge
            // Appears only when friction time is over (CHALLENGE phase)
            if (internalFlowPhase == BreakPhase.CHALLENGE) {
                StepCard(
                    title = "3. Confirm Break",
                    isActive = true
                ) {
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
                                    onClick = { 
                                        viewModel.confirmBreak()
                                        onBack() 
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = if (challengeUi.yesHighlighted) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors()
                                ) {
                                    Text(yesLabel)
                                }
                            }
                            
                            val noButton = @Composable {
                                Button(
                                    onClick = { 
                                        viewModel.cancelBreak()
                                        onBack()
                                    },
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
                }
            }
        }
    }
}

@Composable
private fun StepCard(
    title: String,
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
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun Box(
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.Box(modifier = modifier, contentAlignment = contentAlignment) {
        content()
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
