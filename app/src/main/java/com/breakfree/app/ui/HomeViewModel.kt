package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.settings.BreakDurationOption
import com.breakfree.app.data.settings.BreakPhase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val phase: BreakPhase = BreakPhase.NONE,
    val graceSecondsRemaining: Int = 0,
    val activeSecondsRemaining: Int = 0,
    val durationOptions: List<BreakDurationOption> = emptyList(),
    val gracePeriodSeconds: Int = 30,
    val strictMode: Boolean = true,
    val blockedAppCount: Int = 0,
    val blockedDomainCount: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)

    private val tickerState = MutableTicker()

    val uiState: StateFlow<HomeUiState> = combine(
        breakFreeApp.breakStateManager.state,
        breakFreeApp.settingsDataStore.durationOptions,
        breakFreeApp.settingsDataStore.gracePeriodSeconds,
        breakFreeApp.settingsDataStore.strictMode,
        breakFreeApp.repository.observeBlockedApps(),
        breakFreeApp.repository.observeBlockedDomains()
    ) { values ->
        val breakState = values[0] as com.breakfree.app.data.settings.PersistedBreakState
        val durationOptions = values[1] as List<BreakDurationOption>
        val gracePeriod = values[2] as Int
        val strict = values[3] as Boolean
        val apps = values[4] as List<*>
        val domains = values[5] as List<*>

        val now = System.currentTimeMillis()
        HomeUiState(
            phase = breakState.phase,
            graceSecondsRemaining = ((breakState.graceEndsAtEpochMs - now) / 1000).coerceAtLeast(0).toInt(),
            activeSecondsRemaining = ((breakState.activeEndsAtEpochMs - now) / 1000).coerceAtLeast(0).toInt(),
            durationOptions = durationOptions,
            gracePeriodSeconds = gracePeriod,
            strictMode = strict,
            blockedAppCount = apps.size,
            blockedDomainCount = domains.size
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        // Re-emit every second while a break is pending/active so the countdown UI updates.
        viewModelScope.launch {
            while (true) {
                delay(1000)
                tickerState.tick()
            }
        }
    }

    fun requestBreak(durationSeconds: Int) {
        viewModelScope.launch {
            breakFreeApp.breakStateManager.requestBreak(durationSeconds, uiState.value.gracePeriodSeconds)
        }
    }

    fun cancelBreak() {
        if (uiState.value.strictMode) return // not allowed to bail early in strict mode
        breakFreeApp.breakStateManager.cancelBreak()
    }

    /** Trivial internal ticker just to force uiState recomposition on a 1s cadence. */
    private class MutableTicker {
        private val flow = kotlinx.coroutines.flow.MutableStateFlow(0)
        fun tick() { flow.value += 1 }
    }
}
