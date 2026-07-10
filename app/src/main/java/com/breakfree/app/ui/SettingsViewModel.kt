package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.settings.AppTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val gracePeriodSeconds: Int = 30,
    val theme: AppTheme = AppTheme.AUTO,
    val targetDailyHours: Int = 1,
    val targetScreenTimeMinutes: Int = 60,
    val weeklyAverageHours: Double = 0.0,
    val showBreakNotification: Boolean = true,
    val autoStopOnLockTimeoutMinutes: Int = 1,
    val shakeToSendFeedback: Boolean = true,
    val doomscrollingProtectionEnabled: Boolean = false,
    val isUsageStatsEnabled: Boolean = false
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            breakFreeApp.settingsDataStore.gracePeriodSeconds,
            breakFreeApp.settingsDataStore.theme,
            breakFreeApp.appRepository.apps
        ) { g, t, a -> Triple(g, t, a) },
        combine(
            breakFreeApp.settingsDataStore.showBreakNotification,
            breakFreeApp.settingsDataStore.autoStopOnLockTimeoutMinutes,
            breakFreeApp.settingsDataStore.shakeToSendFeedback
        ) { sn, aso, stf -> Triple(sn, aso, stf) },
        combine(
            breakFreeApp.settingsDataStore.doomscrollingProtectionEnabled,
            breakFreeApp.settingsDataStore.targetScreenTimeMinutes
        ) { dpe, tst -> dpe to tst }
    ) { group1, group2, group3 ->
        val (grace, theme, apps) = group1
        val (showNotif, autoStop, shake) = group2
        val (doomEnabled, targetMins) = group3
        
        val totalWeeklyMs = apps.sumOf { it.usageTimeMs }
        val weeklyAverage = (totalWeeklyMs / (1000.0 * 3600.0 * 7.0))
        val isUsageEnabled = breakFreeApp.appRepository.isUsageStatsPermissionGranted()
        
        SettingsUiState(
            gracePeriodSeconds = grace,
            theme = theme,
            targetDailyHours = targetMins / 60,
            targetScreenTimeMinutes = targetMins,
            weeklyAverageHours = weeklyAverage,
            showBreakNotification = showNotif,
            autoStopOnLockTimeoutMinutes = autoStop,
            shakeToSendFeedback = shake,
            doomscrollingProtectionEnabled = doomEnabled,
            isUsageStatsEnabled = isUsageEnabled
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setGracePeriod(seconds: Int) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setGracePeriodSeconds(seconds) }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setTheme(theme) }
    }

    fun setTargetDailyHours(hours: Int) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setTargetDailyHours(hours) }
    }

    fun setTargetScreenTimeMinutes(minutes: Int) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setTargetScreenTimeMinutes(minutes) }
    }

    fun setDoomscrollingProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setDoomscrollingProtectionEnabled(enabled) }
    }

    fun setShowBreakNotification(show: Boolean) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setShowBreakNotification(show) }
    }

    fun setAutoStopOnLockTimeout(minutes: Int) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setAutoStopOnLockTimeoutMinutes(minutes) }
    }

    fun setShakeToSendFeedback(enabled: Boolean) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setShakeToSendFeedback(enabled) }
    }
}
