package com.breakfree.app

import android.app.Application
import com.breakfree.app.core.BreakStateManager
import com.breakfree.app.data.db.BreakFreeDatabase
import com.breakfree.app.data.repository.AppRepository
import com.breakfree.app.data.repository.BlockingRepository
import com.breakfree.app.data.settings.BreakStateStore
import com.breakfree.app.data.settings.SettingsDataStore
import com.breakfree.app.ui.image.AppIconFetcher
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class BreakFreeApplication : Application(), ImageLoaderFactory {

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(AppIconFetcher.Factory(this@BreakFreeApplication))
            }
            .crossfade(true)
            .build()
    }

    val applicationScope = CoroutineScope(SupervisorJob())

    val database: BreakFreeDatabase by lazy { BreakFreeDatabase.getInstance(this) }

    val repository: BlockingRepository by lazy {
        BlockingRepository(database.blockedAppDao(), database.blockedDomainDao())
    }

    val appRepository: AppRepository by lazy { AppRepository(this, database.appMetadataDao()) }

    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

    private val breakStateStore: BreakStateStore by lazy { BreakStateStore(this) }

    val breakStateManager: BreakStateManager by lazy { BreakStateManager(this, breakStateStore) }

    companion object {
        fun from(context: android.content.Context): BreakFreeApplication =
            context.applicationContext as BreakFreeApplication
    }
}
