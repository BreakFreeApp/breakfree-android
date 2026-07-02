package com.breakfree.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.breakfree.app.sync.AssetSyncWorker

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // WorkManager's own periodic-work persistence handles most of this automatically,
            // but re-enqueueing (idempotent, KEEP policy) is a cheap belt-and-braces step.
            AssetSyncWorker.enqueuePeriodic(context)
        }
    }
}
