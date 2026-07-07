package com.breakfree.app

import android.app.Application
import com.breakfree.app.core.BreakStateManager
import com.breakfree.app.data.db.BreakFreeDatabase
import com.breakfree.app.data.repository.AppRepository
import com.breakfree.app.data.repository.BlockingRepository
import com.breakfree.app.data.settings.AppDefaults
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.data.settings.BreakStateStore
import com.breakfree.app.data.settings.SettingsDataStore
import com.breakfree.app.service.BreakNotificationManager
import com.breakfree.app.ui.image.AppIconFetcher
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    private val breakNotificationManager by lazy { BreakNotificationManager(this) }

    override fun onCreate() {
        super.onCreate()
        
        // Initialize suggested domains on first run
        applicationScope.launch {
            val domains = repository.observeBlockedDomains().first()
            if (domains.isEmpty()) {
                AppDefaults.DOOM_SCROLLING_DOMAINS.forEach { domain ->
                    repository.addDomain(domain, isBlocked = true, isFavorite = true)
                }
            }
        }

        // Notification management loop
        applicationScope.launch {
            combine(
                breakStateManager.state,
                settingsDataStore.showBreakNotification
            ) { state, show -> state to show }
                .collect { (state, show) ->
                    if (show && state.phase == BreakPhase.ACTIVE) {
                        // Start a local loop to update every second while active
                        while (state.phase == BreakPhase.ACTIVE) {
                            breakNotificationManager.updateNotification(state)
                            delay(1000)
                            // Re-check state from manager to see if it changed
                            if (breakStateManager.state.value.phase != BreakPhase.ACTIVE) break
                        }
                    } else {
                        breakNotificationManager.cancelNotification()
                    }
                }
        }
    }

    companion object {
        fun from(context: android.content.Context): BreakFreeApplication =
            context.applicationContext as BreakFreeApplication
    }
}
