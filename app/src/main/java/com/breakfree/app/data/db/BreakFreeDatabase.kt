package com.breakfree.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.breakfree.app.data.db.dao.BlockedAppDao
import com.breakfree.app.data.db.dao.BlockedDomainDao
import com.breakfree.app.data.db.entities.BlockedApp
import com.breakfree.app.data.db.entities.BlockedDomain

@Database(
    entities = [BlockedApp::class, BlockedDomain::class],
    version = 1,
    exportSchema = false
)
abstract class BreakFreeDatabase : RoomDatabase() {

    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun blockedDomainDao(): BlockedDomainDao

    companion object {
        @Volatile private var instance: BreakFreeDatabase? = null

        fun getInstance(context: Context): BreakFreeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    BreakFreeDatabase::class.java,
                    "breakfree.db"
                ).build().also { instance = it }
            }
    }
}
