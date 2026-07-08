package com.breakfree.app.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import com.breakfree.app.BreakFreeApplication
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val ACTION_GRACE_ENDS = "com.breakfree.app.action.GRACE_ENDS"
private const val ACTION_BREAK_ENDS = "com.breakfree.app.action.BREAK_ENDS"
private const val ACTION_AUTO_STOP = "com.breakfree.app.action.AUTO_STOP"
private const val REQUEST_CODE_GRACE = 1001
private const val REQUEST_CODE_ACTIVE = 1002
private const val REQUEST_CODE_AUTO_STOP = 1003

/**
 * Owns the lifecycle of the single global break: NONE -> GRACE -> ACTIVE -> NONE.
 *
 * This is the one place that decides "is the user currently allowed through the
 * blockers right now". State is persisted (via BreakStateStore/DataStore)
 * so it survives process death, and re-derived defensively from timestamps 
 * rather than trusting the stored phase blindly, in case an AlarmManager 
 * callback was delayed (e.g. by Doze).
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
        registerScreenStateReceiver()
    }

    private fun registerScreenStateReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> handleScreenOff()
                    Intent.ACTION_SCREEN_ON -> handleScreenOn()
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            appContext.registerReceiver(receiver, filter)
        }
    }

    private fun handleScreenOff() {
        val current = _state.value
        if (current.phase != BreakPhase.ACTIVE) return

        scope.launch {
            val timeoutMinutes = BreakFreeApplication.from(appContext).settingsDataStore.autoStopOnLockTimeoutMinutes.first()
            if (timeoutMinutes > 0) {
                val atEpochMs = System.currentTimeMillis() + timeoutMinutes * 60 * 1000L
                scheduleAlarm(atEpochMs, ACTION_AUTO_STOP, REQUEST_CODE_AUTO_STOP)
            }
        }
    }

    private fun handleScreenOn() {
        cancelAlarm(REQUEST_CODE_AUTO_STOP, ACTION_AUTO_STOP)
    }

    /** Self-healing: derive the true phase from timestamps, not just the stored label. */
    private fun effective(s: PersistedBreakState, now: Long): PersistedBreakState = when (s.phase) {
        BreakPhase.GRACE -> if (now >= s.graceEndsAtEpochMs) {
            s.copy(phase = BreakPhase.CHALLENGE)
        } else s
        BreakPhase.CHALLENGE -> s
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
        // We don't know activeEnds yet, we'll set it when confirmed. 
        // But for persistence, we'll store durationSeconds in activeEndsAtEpochMs field temporarily (hacky)
        // or better, just store the intended duration in a new field if we want to be clean.
        // Let's reuse activeEndsAtEpochMs as "duration in ms" while in GRACE/CHALLENGE.
        val newState = PersistedBreakState(BreakPhase.GRACE, graceEnds, durationSeconds * 1000L)

        _state.value = newState
        scope.launch { store.write(newState) }
        scheduleAlarm(graceEnds, ACTION_GRACE_ENDS, REQUEST_CODE_GRACE)
        return true
    }

    fun confirmBreak() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (current.phase != BreakPhase.CHALLENGE) return

        val durationMs = current.activeEndsAtEpochMs // stored duration
        val activeEnds = now + durationMs
        val newState = PersistedBreakState(BreakPhase.ACTIVE, now, activeEnds) // graceEnds field re-used for start time

        _state.value = newState
        scope.launch { store.write(newState) }
        scheduleAlarm(activeEnds, ACTION_BREAK_ENDS, REQUEST_CODE_ACTIVE)
    }

    /** Only permitted when strict mode is off; strict mode is enforced by the caller (UI). */
    fun cancelBreak() {
        val now = System.currentTimeMillis()
        val current = _state.value
        
        if (current.phase == BreakPhase.ACTIVE) {
            val startTime = current.graceEndsAtEpochMs
            val elapsed = now - startTime
            if (elapsed > 0) {
                scope.launch {
                    BreakFreeApplication.from(appContext).settingsDataStore.recordBreakCompletion(elapsed)
                }
            }
        }

        cancelAlarm(REQUEST_CODE_GRACE, ACTION_GRACE_ENDS)
        cancelAlarm(REQUEST_CODE_ACTIVE, ACTION_BREAK_ENDS)
        cancelAlarm(REQUEST_CODE_AUTO_STOP, ACTION_AUTO_STOP)
        _state.value = PersistedBreakState(BreakPhase.NONE, 0L, 0L)
        scope.launch { store.clear() }
    }

    /** Called by BreakExpiryReceiver when the grace-period alarm fires. */
    fun onGraceExpiredAlarm() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (current.phase == BreakPhase.GRACE) {
            val newState = current.copy(phase = BreakPhase.CHALLENGE)
            _state.value = newState
            scope.launch { store.write(newState) }
        }
    }

    /** Called by BreakExpiryReceiver when the active-break alarm fires. */
    fun onBreakExpiredAlarm() {
        val now = System.currentTimeMillis()
        val current = _state.value
        if (current.phase == BreakPhase.ACTIVE) {
            val startTime = current.graceEndsAtEpochMs
            val elapsed = now - startTime
            if (elapsed > 0) {
                scope.launch {
                    BreakFreeApplication.from(appContext).settingsDataStore.recordBreakCompletion(elapsed)
                }
            }
        }
        cancelBreak()
    }

    private fun scheduleAlarm(atEpochMs: Long, action: String, requestCode: Int) {
        val intent = Intent(appContext, BreakExpiryReceiver::class.java).setAction(action)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, flags)

        // Using setAndAllowWhileIdle for inexact but power-efficient alarms.
        // Precision is not critical (a few seconds delay is acceptable), and 
        // it avoids the need for the restricted SCHEDULE_EXACT_ALARM permission.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atEpochMs, pendingIntent)
    }

    private fun cancelAlarm(requestCode: Int, action: String? = null) {
        val intent = Intent(appContext, BreakExpiryReceiver::class.java)
        if (action != null) intent.action = action
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(appContext, requestCode, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        const val ACTION_GRACE_ENDS_INTENT = ACTION_GRACE_ENDS
        const val ACTION_BREAK_ENDS_INTENT = ACTION_BREAK_ENDS
        const val ACTION_AUTO_STOP_INTENT = ACTION_AUTO_STOP
    }
}
