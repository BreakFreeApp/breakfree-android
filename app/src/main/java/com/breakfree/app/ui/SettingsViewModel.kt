package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(val gracePeriodSeconds: Int = 30, val strictMode: Boolean = true)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)

    val uiState: StateFlow<SettingsUiState> = combine(
        breakFreeApp.settingsDataStore.gracePeriodSeconds,
        breakFreeApp.settingsDataStore.strictMode
    ) { grace, strict -> SettingsUiState(grace, strict) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    fun setGracePeriod(seconds: Int) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setGracePeriodSeconds(seconds) }
    }

    fun setStrictMode(enabled: Boolean) {
        viewModelScope.launch { breakFreeApp.settingsDataStore.setStrictMode(enabled) }
    }
}
