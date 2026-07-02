package com.breakfree.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.data.settings.BreakStateStore
import com.breakfree.app.data.settings.PersistedBreakState
import com.breakfree.app.service.BreakExpiryReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val ACTION_GRACE_ENDS = "com.breakfree.app.action.GRACE_ENDS"
private const val ACTION_BREAK_ENDS = "com.breakfree.app.action.BREAK_ENDS"
private const val REQUEST_CODE_GRACE = 1001
private const val REQUEST_CODE_ACTIVE = 1002

/**
 * Owns the lifecycle of the single global break: NONE -> GRACE -> ACTIVE -> NONE.
 *
 * This is the one place that decides "is the user currently allowed through the
 * blockers right now" — both the AccessibilityService and the VpnService consult it.
 * State is persisted (via BreakStateStore/DataStore) so it survives process death,
 * and re-derived defensively from timestamps rather than trusting the stored phase
 * blindly, in case an AlarmManager callback was delayed (e.g. by Doze).
 */
class BreakStateManager(
    context: Context,
    private val store: BreakStateStore
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    private val _state = MutableStateFlow(PersistedBreakState(BreakPhase.NONE, 0L, 0L))
    val state: StateFlow<PersistedBreakState> = _state.asStateFlow()

    init {
        scope.launch {
            store.state.collect { persisted ->
                _state.value = effective(persisted, System.currentTimeMillis())
            }
        }
    }

    /** Self-healing: derive the true phase from timestamps, not just the stored label. */
    private fun effective(s: PersistedBreakState, now: Long): PersistedBreakState = when (s.phase) {
        BreakPhase.GRACE -> if (now >= s.graceEndsAtEpochMs) {
            if (now < s.activeEndsAtEpochMs) s.copy(phase = BreakPhase.ACTIVE)
            else PersistedBreakState(BreakPhase.NONE, 0L, 0L)
        } else s
        BreakPhase.ACTIVE -> if (now >= s.activeEndsAtEpochMs) PersistedBreakState(BreakPhase.NONE, 0L, 0L) else s
        BreakPhase.NONE -> s
    }

    /** Fast, synchronous check used by the accessibility service on every window change. */
    fun isBreakActiveNow(now: Long = System.currentTimeMillis()): Boolean {
        val s = effective(_state.value, now)
        return s.phase == BreakPhase.ACTIVE
    }

    /** Returns false if a break is already pending/active and can't be superseded. */
    fun requestBreak(durationSeconds: Int, gracePeriodSeconds: Int): Boolean {
        val now = System.currentTimeMillis()
        val current = effective(_state.value, now)
        if (current.phase != BreakPhase.NONE) return false

        val graceEnds = now + gracePeriodSeconds * 1000L
        val activeEnds = graceEnds + durationSeconds * 1000L
        val newState = PersistedBreakState(BreakPhase.GRACE, graceEnds, activeEnds)

        _state.value = newState
        scope.launch { store.write(newState) }
        scheduleAlarm(graceEnds, ACTION_GRACE_ENDS, REQUEST_CODE_GRACE)
        scheduleAlarm(activeEnds, ACTION_BREAK_ENDS, REQUEST_CODE_ACTIVE)
        return true
    }

    /** Only permitted when strict mode is off; strict mode is enforced by the caller (UI). */
    fun cancelBreak() {
        cancelAlarm(REQUEST_CODE_GRACE)
        cancelAlarm(REQUEST_CODE_ACTIVE)
        _state.value = PersistedBreakState(BreakPhase.NONE, 0L, 0L)
        scope.launch { store.clear() }
    }

    /** Called by BreakExpiryReceiver when the grace-period alarm fires. */
    fun onGraceExpiredAlarm() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (current.phase == BreakPhase.GRACE) {
            val next = effective(current, now)
            _state.value = next
            scope.launch { store.write(next) }
        }
    }

    /** Called by BreakExpiryReceiver when the active-break alarm fires. */
    fun onBreakExpiredAlarm() {
        cancelBreak()
    }

    private fun scheduleAlarm(atEpochMs: Long, action: String, requestCode: Int) {
        val intent = Intent(appContext, BreakExpiryReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, flags)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pendingIntent)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pendingIntent)
        }
    }

    private fun cancelAlarm(requestCode: Int) {
        val intent = Intent(appContext, BreakExpiryReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ACTION_GRACE_ENDS_INTENT = ACTION_GRACE_ENDS
        const val ACTION_BREAK_ENDS_INTENT = ACTION_BREAK_ENDS
    }
}
