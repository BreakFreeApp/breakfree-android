package com.breakfree.app.ui.image

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options

class AppIconFetcher(
    private val packageName: String,
    private val context: Context
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val pm = context.packageManager
        val icon = try {
            // Use the most direct way to get the icon
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadIcon(pm)
        } catch (e: Exception) {
            null
        } ?: return null

        return DrawableResult(
            drawable = icon,
            isSampled = false,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<Any> {
        override fun create(data: Any, options: Options, imageLoader: ImageLoader): Fetcher? {
            val stringData = data.toString()
            if (!stringData.startsWith("appicon://")) return null
            
            val packageName = stringData.removePrefix("appicon://")
            if (packageName.isBlank()) return null

            return AppIconFetcher(packageName, context)
        }
    }
}
