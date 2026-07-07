package com.breakfree.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.breakfree.app.R
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.data.settings.PersistedBreakState

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
                NotificationManager.IMPORTANCE_LOW
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

        val seconds = (remainingMs / 1000).toInt()
        val contentText = "Break ends in ${formatCountdown(seconds)}"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("BreakFree is active")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
