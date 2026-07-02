package com.breakfree.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "breakfree_settings")

data class BreakDurationOption(val label: String, val seconds: Int)

/** Default break-length choices shown when requesting a break. */
val DEFAULT_DURATION_OPTIONS = listOf(
    BreakDurationOption("30 sec", 30),
    BreakDurationOption("1 min", 60),
    BreakDurationOption("10 min", 600)
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val GRACE_PERIOD_SECONDS = intPreferencesKey("grace_period_seconds")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val DURATION_OPTIONS_SECONDS = stringPreferencesKey("duration_options_seconds") // csv
    }

    val gracePeriodSeconds: Flow<Int> = context.settingsDataStore.data.map {
        it[Keys.GRACE_PERIOD_SECONDS] ?: 30
    }

    val strictMode: Flow<Boolean> = context.settingsDataStore.data.map {
        it[Keys.STRICT_MODE] ?: true
    }

    val durationOptions: Flow<List<BreakDurationOption>> = context.settingsDataStore.data.map { prefs ->
        val csv = prefs[Keys.DURATION_OPTIONS_SECONDS]
        if (csv.isNullOrBlank()) {
            DEFAULT_DURATION_OPTIONS
        } else {
            csv.split(",").mapNotNull { it.trim().toIntOrNull() }.map { secs ->
                BreakDurationOption(labelForSeconds(secs), secs)
            }
        }
    }

    suspend fun setGracePeriodSeconds(seconds: Int) {
        context.settingsDataStore.edit { it[Keys.GRACE_PERIOD_SECONDS] = seconds }
    }

    suspend fun setStrictMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.STRICT_MODE] = enabled }
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
