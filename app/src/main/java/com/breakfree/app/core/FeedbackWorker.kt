package com.breakfree.app.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class FeedbackWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    private val client = OkHttpClient()
    private val proxyUrl = "https://feedback-android.hello-breakfreeapp.workers.dev"

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val textFilePath = inputData.getString("TEXT_FILE_PATH") ?: return@withContext Result.failure()
        val imageFilePath = inputData.getString("IMAGE_FILE_PATH")

        val textFile = File(textFilePath)
        if (!textFile.exists()) return@withContext Result.failure()

        val userMessage = textFile.readText()
        val systemInfo = gatherSystemInfo()
        val combinedText = "$userMessage\n\n$systemInfo"

        var processedImageFile: File? = null

        try {
            if (imageFilePath != null) {
                val originalImage = File(imageFilePath)
                if (originalImage.exists()) {
                    processedImageFile = resizeAndCompressImage(originalImage)
                }
            }

            val success = transmitToProxy(combinedText, processedImageFile)

            if (success) {
                // Cleanup cache on success
                textFile.delete()
                if (imageFilePath != null) File(imageFilePath).delete()
                processedImageFile?.delete()
                return@withContext Result.success()
            } else {
                // Return retry to trigger WorkManager's exponential backoff
                return@withContext Result.retry()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext Result.retry()
        }
    }

    private fun gatherSystemInfo(): String {
        return """
            **System Telemetry**
            `OS:` Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            `Device:` ${Build.MANUFACTURER} ${Build.MODEL}
        """.trimIndent()
    }

    private fun resizeAndCompressImage(originalFile: File): File {
        val maxEdge = 1920

        // 1. Read dimensions without loading into memory to avoid OOM
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(originalFile.absolutePath, options)

        // 2. Calculate scale factor
        var scale = 1
        while (options.outWidth / scale / 2 >= maxEdge && options.outHeight / scale / 2 >= maxEdge) {
            scale *= 2
        }

        // 3. Load downsampled bitmap
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = BitmapFactory.decodeFile(originalFile.absolutePath, decodeOptions)

        // 4. Compress to JPEG
        val compressedFile = File(applicationContext.cacheDir, "compressed_feedback_${System.currentTimeMillis()}.jpg")
        FileOutputStream(compressedFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
        }

        bitmap.recycle() // Free memory immediately
        return compressedFile
    }

    private fun transmitToProxy(text: String, imageFile: File?): Boolean {
        val safeText = text.take(1990).replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

        val requestBody = if (imageFile != null) {
            val mediaType = "image/jpeg".toMediaTypeOrNull()
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("payload_json", """{"content": "$safeText"}""")
                .addFormDataPart("file", imageFile.name, imageFile.asRequestBody(mediaType))
                .build()
        } else {
            """{"content": "$safeText"}""".toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
        }

        val request = Request.Builder().url(proxyUrl).post(requestBody).build()
        return client.newCall(request).execute().use { it.isSuccessful }
    }
}


