package com.breakfree.app.domain

import com.breakfree.app.core.BreakStateManager
import com.breakfree.app.data.settings.SettingsDataStore

class RequestBreakUseCase(
    private val breakStateManager: BreakStateManager,
    private val settingsDataStore: SettingsDataStore
) {
    suspend operator fun invoke(durationSeconds: Int, gracePeriodSeconds: Int): Boolean {
        val success = breakStateManager.requestBreak(durationSeconds, gracePeriodSeconds)
        if (success) {
            settingsDataStore.recordBreakRequest()
        }
        return success
    }
}

class ConfirmBreakUseCase(
    private val breakStateManager: BreakStateManager
) {
    operator fun invoke() {
        breakStateManager.confirmBreak()
    }
}

class CancelBreakUseCase(
    private val breakStateManager: BreakStateManager
) {
    operator fun invoke() {
        breakStateManager.cancelBreak()
    }
}
