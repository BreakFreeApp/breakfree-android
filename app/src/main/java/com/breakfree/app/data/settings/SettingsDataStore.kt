package com.breakfree.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "breakfree_settings")

data class BreakDurationOption(val label: String, val seconds: Int)

enum class AppTheme { SYSTEM, LIGHT, DARK }

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val GRACE_PERIOD_SECONDS = intPreferencesKey("grace_period_seconds")
        val DURATION_OPTIONS_SECONDS = stringPreferencesKey("duration_options_seconds") // csv
        val THEME = stringPreferencesKey("app_theme")
        val TARGET_DAILY_HOURS = intPreferencesKey("target_daily_hours")
        val LAST_BREAK_REQUEST_TIME = longPreferencesKey("last_break_request_time")
        val TOTAL_BREAKS_COUNT = intPreferencesKey("total_breaks_count")
        val TOTAL_BREAK_TIME_MS = longPreferencesKey("total_break_time_ms")
        val SHOW_BREAK_NOTIFICATION = booleanPreferencesKey("show_break_notification")
        val AUTO_STOP_ON_LOCK_TIMEOUT_MINUTES = intPreferencesKey("auto_stop_on_lock_timeout_minutes")
    }

    val gracePeriodSeconds: Flow<Int> = context.settingsDataStore.data.map {
        it[Keys.GRACE_PERIOD_SECONDS] ?: AppDefaults.GRACE_PERIOD_SECONDS
    }

    val theme: Flow<AppTheme> = context.settingsDataStore.data.map {
        try {
            AppTheme.valueOf(it[Keys.THEME] ?: AppTheme.SYSTEM.name)
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }
    }

    val targetDailyHours: Flow<Int> = context.settingsDataStore.data.map {
        it[Keys.TARGET_DAILY_HOURS] ?: 4 // Default 4 hours
    }

    val lastBreakRequestTime: Flow<Long> = context.settingsDataStore.data.map {
        it[Keys.LAST_BREAK_REQUEST_TIME] ?: 0L
    }

    val totalBreaksCount: Flow<Int> = context.settingsDataStore.data.map {
        it[Keys.TOTAL_BREAKS_COUNT] ?: 0
    }

    val totalBreakTimeMs: Flow<Long> = context.settingsDataStore.data.map {
        it[Keys.TOTAL_BREAK_TIME_MS] ?: 0L
    }

    val showBreakNotification: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.SHOW_BREAK_NOTIFICATION] ?: true
    }

    val autoStopOnLockTimeoutMinutes: Flow<Int> = context.settingsDataStore.data.map {
        it[Keys.AUTO_STOP_ON_LOCK_TIMEOUT_MINUTES] ?: 1 // Default 1 min
    }

    val durationOptions: Flow<List<BreakDurationOption>> = context.settingsDataStore.data.map { prefs ->
        val csv = prefs[Keys.DURATION_OPTIONS_SECONDS]
        if (csv.isNullOrBlank()) {
            AppDefaults.DURATION_OPTIONS
        } else {
            csv.split(",").mapNotNull { it.trim().toIntOrNull() }.map { secs ->
                BreakDurationOption(labelForSeconds(secs), secs)
            }
        }
    }

    suspend fun setGracePeriodSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.GRACE_PERIOD_SECONDS] = seconds }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setTargetDailyHours(hours: Int) {
        context.settingsDataStore.edit { it[Keys.TARGET_DAILY_HOURS] = hours }
    }

    suspend fun setShowBreakNotification(show: Boolean) {
        context.settingsDataStore.edit { it[Keys.SHOW_BREAK_NOTIFICATION] = show }
    }

    suspend fun setAutoStopOnLockTimeoutMinutes(minutes: Int) {
        context.settingsDataStore.edit { it[Keys.AUTO_STOP_ON_LOCK_TIMEOUT_MINUTES] = minutes }
    }

    suspend fun recordBreakRequest() {
        context.settingsDataStore.edit {
            it[Keys.LAST_BREAK_REQUEST_TIME] = System.currentTimeMillis()
            val currentCount = it[Keys.TOTAL_BREAKS_COUNT] ?: 0
            it[Keys.TOTAL_BREAKS_COUNT] = currentCount + 1
        }
    }

    suspend fun recordBreakCompletion(actualDurationMs: Long) {
        context.settingsDataStore.edit {
            val currentTime = it[Keys.TOTAL_BREAK_TIME_MS] ?: 0L
            it[Keys.TOTAL_BREAK_TIME_MS] = currentTime + actualDurationMs
        }
    }

    suspend fun setDurationOptions(secondsList: List<Int>) {
        context.settingsDataStore.edit {
            it[Keys.DURATION_OPTIONS_SECONDS] = secondsList.joinToString(",")
        }
    }

    private fun labelForSeconds(secs: Int): String = when {
        secs < 60 -> "$secs sec"
        secs % 60 == 0 -> "${secs / 60} min"
        else -> "${secs / 60}m ${secs % 60}s"
    }
}
