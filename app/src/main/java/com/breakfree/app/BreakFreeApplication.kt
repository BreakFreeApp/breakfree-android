package com.breakfree.app

import android.app.Application
import com.breakfree.app.core.BreakStateManager
import com.breakfree.app.data.db.BreakFreeDatabase
import com.breakfree.app.data.repository.BlockingRepository
import com.breakfree.app.data.settings.BreakStateStore
import com.breakfree.app.data.settings.SettingsDataStore

class BreakFreeApplication : Application() {

    val database: BreakFreeDatabase by lazy { BreakFreeDatabase.getInstance(this) }

    val repository: BlockingRepository by lazy {
        BlockingRepository(database.blockedAppDao(), database.blockedDomainDao())
    }

    val settingsDataStore: SettingsDataStore by lazy { SettingsDataStore(this) }

    private val breakStateStore: BreakStateStore by lazy { BreakStateStore(this) }

    val breakStateManager: BreakStateManager by lazy { BreakStateManager(this, breakStateStore) }

    companion object {
        fun from(context: android.content.Context): BreakFreeApplication =
            context.applicationContext as BreakFreeApplication
    }
}
