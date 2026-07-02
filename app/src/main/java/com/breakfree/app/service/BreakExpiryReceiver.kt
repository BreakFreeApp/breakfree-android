package com.breakfree.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.core.BreakStateManager

/** Fired by AlarmManager when the grace period elapses, and again when the break itself ends. */
class BreakExpiryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = BreakFreeApplication.from(context).breakStateManager
        when (intent.action) {
            BreakStateManager.ACTION_GRACE_ENDS_INTENT -> manager.onGraceExpiredAlarm()
            BreakStateManager.ACTION_BREAK_ENDS_INTENT -> manager.onBreakExpiredAlarm()
        }
    }
}
