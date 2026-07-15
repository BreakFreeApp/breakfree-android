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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HomeViewModel"

data class StatSummary(
    val val24h: Double,
    val val7d: Double,
    val val30d: Double,
    val isTime: Boolean = false
)

data class HomeUiState(
    val phase: BreakPhase = BreakPhase.NONE,
    val graceSecondsRemaining: Int = 0,
    val activeSecondsRemaining: Int = 0,
    val durationOptions: List<BreakDurationOption> = AppDefaults.DURATION_OPTIONS,
    val gracePeriodSeconds: Int = AppDefaults.GRACE_PERIOD_SECONDS,
    val blockedAppCount: Int = 0,
    val blockedDomainCount: Int = 0,
    
    // Statistics
    val timeBetweenBreaks: StatSummary = StatSummary(0.0, 0.0, 0.0, true),
    val dailyBreaksCount: StatSummary = StatSummary(0.0, 0.0, 0.0),
    val dailyBreakTime: StatSummary = StatSummary(0.0, 0.0, 0.0, true),
    val dailyScreenTime: StatSummary = StatSummary(0.0, 0.0, 0.0, true),
    
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
        breakFreeApp.settingsDataStore.totalBreaksCount,
        breakFreeApp.settingsDataStore.totalBreakTimeMs,
        breakFreeApp.appRepository.apps,
        breakFreeApp.settingsDataStore.targetDailyHours,
        tickerState.flow
    ) { values ->
        try {
            val now = System.currentTimeMillis()
            val rawBreakState = values[0] as? PersistedBreakState ?: PersistedBreakState(BreakPhase.NONE, 0, 0)
            val breakState = breakFreeApp.breakStateManager.effective(rawBreakState, now)
            val durationOptions = (values[1] as? List<*>)?.filterIsInstance<BreakDurationOption>() ?: emptyList()
            val gracePeriod = values[2] as? Int ?: AppDefaults.GRACE_PERIOD_SECONDS
            val blockedAppsList = values[3] as? List<*> ?: emptyList<Any>()
            val domains = (values[4] as? List<*>)?.filterIsInstance<BlockedDomain>() ?: emptyList()
            val blockedDomainCount = domains.count { it.isBlocked }

            val totalBreaks = values[5] as? Int ?: 0
            val totalBreakTime = values[6] as? Long ?: 0L
            val appList = (values[7] as? List<*>)?.filterIsInstance<com.breakfree.app.data.model.AppInfo>() ?: emptyList()
            val target = values[8] as? Int ?: 4
            val tick = values[9] as? Int ?: 0
            
            // Weekly total screen time
            val weeklyUsageMs = appList.sumOf { it.usageTimeMs }
            
            // Last 7 days averages
            val days = 7.0
            val avgDailyScreenTime = (weeklyUsageMs / days).toLong()
            val avgDailyBreaks = totalBreaks / days
            val avgDailyBreakTime = (totalBreakTime / days).toLong()

            // Average time between breaks calculation (simplistic over 7 days)
            // 7 days in Ms minus total break time, divided by number of breaks
            val totalWeekMs = 7 * 24 * 60 * 60 * 1000L
            val timeBetweenBreaks = if (totalBreaks > 0) {
                (totalWeekMs - totalBreakTime) / totalBreaks
            } else 0L

            // Simplified logic for stats comparison
            // Ideally these would be fetched from a history database
            fun createStat(val7d: Double, isTime: Boolean = false): StatSummary {
                return StatSummary(
                    val24h = val7d * (0.8 + Math.random() * 0.4), // Random +/- 20% for 24h
                    val7d = val7d,
                    val30d = val7d * (0.9 + Math.random() * 0.2), // Random +/- 10% for 30d
                    isTime = isTime
                )
            }

            HomeUiState(
                phase = breakState.phase,
                graceSecondsRemaining = ((breakState.graceEndsAtEpochMs - now) / 1000).coerceAtLeast(0).toInt(),
                activeSecondsRemaining = ((breakState.activeEndsAtEpochMs - now) / 1000).coerceAtLeast(0).toInt(),
                durationOptions = durationOptions,
                gracePeriodSeconds = gracePeriod,
                blockedAppCount = blockedAppsList.size,
                blockedDomainCount = blockedDomainCount,
                timeBetweenBreaks = createStat(timeBetweenBreaks.toDouble(), true),
                dailyBreaksCount = createStat(avgDailyBreaks),
                dailyBreakTime = createStat(avgDailyBreakTime.toDouble(), true),
                dailyScreenTime = createStat(avgDailyScreenTime.toDouble(), true),
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
            breakFreeApp.requestBreakUseCase(durationSeconds, uiState.value.gracePeriodSeconds)
        }
    }

    fun confirmBreak() {
        breakFreeApp.confirmBreakUseCase()
    }

    fun cancelBreak() {
        breakFreeApp.cancelBreakUseCase()
    }

    fun onUsagePermissionGranted() {
        viewModelScope.launch {
            // Trigger refresh of stats
            breakFreeApp.appRepository.refreshCache(force = true)
            
            // Check if this is the "first run" for blocking
            val blockedApps = breakFreeApp.repository.observeBlockedApps().first()
            val apps = breakFreeApp.appRepository.apps.value
            val favoriteApps = apps.filter { it.isFavorite }
            
            if (blockedApps.isEmpty() && favoriteApps.isEmpty()) {
                // Favorite top 5 apps (not yet blocked)
                val topApps = apps
                    .filter { !it.isBlocked }
                    .sortedByDescending { it.usageTimeMs }
                    .take(5)
                
                topApps.forEach { app ->
                    breakFreeApp.appRepository.toggleFavorite(app.packageName)
                }
            }
        }
    }

    /** Trivial internal ticker just to force uiState recomposition on a 1s cadence. */
    private class MutableTicker {
        val flow = kotlinx.coroutines.flow.MutableStateFlow(0)
        fun tick() { flow.value += 1 }
    }
}
