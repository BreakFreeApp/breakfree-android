package com.breakfree.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Periodically fetches a static JSON manifest (hosted on GitHub Pages or any CDN) and
 * refreshes whatever local cache it feeds — e.g. curated default domain block-lists,
 * app metadata, or a version-check payload. This is a plain HTTP GET with no auth;
 * point MANIFEST_URL at your own hosted file.
 *
 * Left intentionally light on parsing/application logic — wire in whatever your
 * manifest actually contains once you have a real hosted asset.
 */
class AssetSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val json = fetch(MANIFEST_URL)
            // TODO: parse `json` and update local Room tables / DataStore as needed.
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun fetch(urlString: String): String {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    companion object {
        // Replace with your actual hosted manifest, e.g. https://youruser.github.io/breakfree-assets/manifest.json
        private const val MANIFEST_URL = "https://example.github.io/breakfree-assets/manifest.json"
        private const val WORK_NAME = "breakfree_asset_sync"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AssetSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
