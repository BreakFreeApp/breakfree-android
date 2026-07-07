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
    val theme: AppTheme = AppTheme.SYSTEM,
    val targetDailyHours: Int = 1,
    val weeklyAverageHours: Double = 0.0,
    val showBreakNotification: Boolean = true
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        breakFreeApp.settingsDataStore.gracePeriodSeconds,
        breakFreeApp.settingsDataStore.theme,
        breakFreeApp.appRepository.apps,
        breakFreeApp.settingsDataStore.showBreakNotification
    ) { grace, theme, apps, showNotif ->
        val totalWeeklyMs = apps.sumOf { it.usageTimeMs }
        val weeklyAverage = (totalWeeklyMs / (1000.0 * 3600.0 * 7.0))
        SettingsUiState(grace, theme, 1, weeklyAverage, showNotif)
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

    fun setShowBreakNotification(show: Boolean) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setShowBreakNotification(show) }
    }
}
