package com.breakfree.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.db.entities.BlockedDomain
import com.breakfree.app.data.settings.AppDefaults
import com.breakfree.app.data.settings.BreakDurationOption
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.data.settings.PersistedBreakState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

data class HomeUiState(
    val phase: BreakPhase = BreakPhase.NONE,
    val graceSecondsRemaining: Int = 0,
    val activeSecondsRemaining: Int = 0,
    val durationOptions: List<BreakDurationOption> = AppDefaults.DURATION_OPTIONS,
    val gracePeriodSeconds: Int = AppDefaults.GRACE_PERIOD_SECONDS,
    val blockedAppCount: Int = 0,
    val blockedDomainCount: Int = 0,
    val lastBreakRequestTime: Long = 0,
    val totalBreaksCount: Int = 0,
    val totalBreakTimeMs: Long = 0,
    val weeklyUsageMs: Long = 0,
    val targetDailyHours: Int = 4,
    val apps: List<com.breakfree.app.data.model.AppInfo> = emptyList(),
    val ticker: Int = 0
)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)

    private val tickerState = MutableTicker()

    val uiState: StateFlow<HomeUiState> = combine(
        breakFreeApp.breakStateManager.state,
        breakFreeApp.settingsDataStore.durationOptions,
        breakFreeApp.settingsDataStore.gracePeriodSeconds,
        breakFreeApp.repository.observeBlockedApps(),
        breakFreeApp.repository.observeBlockedDomains(),
        breakFreeApp.settingsDataStore.lastBreakRequestTime,
        breakFreeApp.settingsDataStore.totalBreaksCount,
        breakFreeApp.settingsDataStore.totalBreakTimeMs,
        breakFreeApp.appRepository.apps,
        breakFreeApp.settingsDataStore.targetDailyHours,
        tickerState.flow
    ) { values ->
        try {
            val breakState = values[0] as? PersistedBreakState ?: PersistedBreakState(BreakPhase.NONE, 0, 0)
            val durationOptions = (values[1] as? List<*>)?.filterIsInstance<BreakDurationOption>() ?: emptyList()
            val gracePeriod = values[2] as? Int ?: AppDefaults.GRACE_PERIOD_SECONDS
            val apps = values[3] as? List<*> ?: emptyList<Any>()
            val domains = (values[4] as? List<*>)?.filterIsInstance<BlockedDomain>() ?: emptyList()
            val blockedDomainCount = domains.count { it.isBlocked }

            val lastBreak = values[5] as? Long ?: 0L
            val totalBreaks = values[6] as? Int ?: 0
            val totalBreakTime = values[7] as? Long ?: 0L
            val appList = (values[8] as? List<*>)?.filterIsInstance<com.breakfree.app.data.model.AppInfo>() ?: emptyList()
            val target = 1
            val tick = values[10] as? Int ?: 0
            
            val weeklyUsage = appList.sumOf { it.usageTimeMs }

            val now = System.currentTimeMillis()
            HomeUiState(
                phase = breakState.phase,
                graceSecondsRemaining = ((breakState.graceEndsAtEpochMs - now) / 1000).coerceAtLeast(0).toInt(),
                activeSecondsRemaining = ((breakState.activeEndsAtEpochMs - now) / 1000).coerceAtLeast(0).toInt(),
                durationOptions = durationOptions,
                gracePeriodSeconds = gracePeriod,
                blockedAppCount = apps.size,
                blockedDomainCount = blockedDomainCount,
                lastBreakRequestTime = lastBreak,
                totalBreaksCount = totalBreaks,
                totalBreakTimeMs = totalBreakTime,
                weeklyUsageMs = weeklyUsage,
                targetDailyHours = target,
                apps = appList,
                ticker = tick
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in combine block", e)
            HomeUiState()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        // Preload basic app info when app starts
        viewModelScope.launch {
            try {
                breakFreeApp.appRepository.preload()
                // Lazy refresh of full stats in background
                breakFreeApp.appRepository.refreshCache()
            } catch (e: Exception) {
                Log.e(TAG, "Error preloading apps", e)
            }
        }

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
            val success = breakFreeApp.breakStateManager.requestBreak(durationSeconds, uiState.value.gracePeriodSeconds)
            if (success) {
                breakFreeApp.settingsDataStore.recordBreakRequest()
            }
        }
    }

    fun confirmBreak() {
        viewModelScope.launch {
            breakFreeApp.breakStateManager.confirmBreak()
        }
    }

    fun cancelBreak() {
        breakFreeApp.breakStateManager.cancelBreak()
    }

    /** Trivial internal ticker just to force uiState recomposition on a 1s cadence. */
    private class MutableTicker {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(0)
        fun tick() { flow.value += 1 }
    }
}
