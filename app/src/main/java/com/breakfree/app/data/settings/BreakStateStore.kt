package com.breakfree.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.breakStateDataStore by preferencesDataStore(name = "breakfree_break_state")

enum class BreakPhase { NONE, GRACE, CHALLENGE, ACTIVE }

data class PersistedBreakState(
    val phase: BreakPhase,
    val graceEndsAtEpochMs: Long,
    val activeEndsAtEpochMs: Long
)

/**
 * Raw persistence for the break lifecycle. This does NOT contain business logic
 * (that lives in BreakStateManager) — it's just durable storage so an active break
 * survives app process death, since AlarmManager fires a BroadcastReceiver that may
 * run without the app process alive.
 */
class BreakStateStore(private val context: Context) {

    private object Keys {
        val PHASE = stringPreferencesKey("phase")
        val GRACE_ENDS_AT = longPreferencesKey("grace_ends_at")
        val ACTIVE_ENDS_AT = longPreferencesKey("active_ends_at")
    }

    val state: Flow<PersistedBreakState> = context.breakStateDataStore.data.map { prefs ->
        PersistedBreakState(
            phase = prefs[Keys.PHASE]?.let { runCatching { BreakPhase.valueOf(it) }.getOrNull() }
                ?: BreakPhase.NONE,
            graceEndsAtEpochMs = prefs[Keys.GRACE_ENDS_AT] ?: 0L,
            activeEndsAtEpochMs = prefs[Keys.ACTIVE_ENDS_AT] ?: 0L
        )
    }

    suspend fun write(state: PersistedBreakState) {
        context.breakStateDataStore.edit {
            it[Keys.PHASE] = state.phase.name
            it[Keys.GRACE_ENDS_AT] = state.graceEndsAtEpochMs
            it[Keys.ACTIVE_ENDS_AT] = state.activeEndsAtEpochMs
        }
    }

    suspend fun clear() = write(PersistedBreakState(BreakPhase.NONE, 0L, 0L))
}
