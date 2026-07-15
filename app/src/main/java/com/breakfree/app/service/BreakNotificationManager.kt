package com.breakfree.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.breakfree.android.R
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.data.settings.PersistedBreakState
import com.breakfree.app.ui.MainActivity

class BreakNotificationManager(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Break",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows countdown when a break is active"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun updateNotification(state: PersistedBreakState) {
        if (state.phase != BreakPhase.ACTIVE) {
            cancelNotification()
            return
        }

        val remainingMs = state.activeEndsAtEpochMs - System.currentTimeMillis()
        if (remainingMs <= 0) {
            cancelNotification()
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BreakFree is active")
            .setContentText("Tap to return to the app")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(state.activeEndsAtEpochMs)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun formatCountdown(seconds: Int): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> {
                val m = seconds / 60
                val s = seconds % 60
                "${m}m ${s}s"
            }
            else -> {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                "${h}h ${m}m"
            }
        }
    }

    companion object {
        private const val CHANNEL_ID = "active_break_channel"
        private const val NOTIFICATION_ID = 100
    }
}
