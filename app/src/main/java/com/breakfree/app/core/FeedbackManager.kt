package com.breakfree.app.core

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

object FeedbackManager {

    /**
     * Caches feedback locally and queues it for background transmission.
     */
    suspend fun submitFeedback(
        context: Context,
        feedbackText: String,
        imageFile: File? = null
    ) {
        withContext(Dispatchers.IO) {
            val uniqueId = UUID.randomUUID().toString()

            // 1. Cache the text securely
            val textCacheFile = File(context.cacheDir, "feedback_txt_$uniqueId.txt")
            textCacheFile.writeText(feedbackText)

            // 2. Prepare the WorkManager payload
            val dataBuilder = Data.Builder()
                .putString("TEXT_FILE_PATH", textCacheFile.absolutePath)

            if (imageFile != null && imageFile.exists()) {
                // If an image is provided, copy it to our cache to ensure it isn't
                // deleted by the UI before the background worker processes it.
                val imageCacheFile = File(context.cacheDir, "feedback_img_$uniqueId.png")
                imageFile.copyTo(imageCacheFile, overwrite = true)
                dataBuilder.putString("IMAGE_FILE_PATH", imageCacheFile.absolutePath)
            }

            // 3. Define constraints (Only run when network is connected)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            // 4. Queue the work
            val uploadWorkRequest = OneTimeWorkRequestBuilder<FeedbackWorker>()
                .setConstraints(constraints)
                .setInputData(dataBuilder.build())
                .build()

            WorkManager.getInstance(context).enqueue(uploadWorkRequest)
        }
    }
}