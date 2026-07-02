package com.breakfree.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * An app the user has chosen to block. Apps are blocked by default the moment
 * they're added here — there is no separate "enabled" toggle by design.
 */
@Entity(tableName = "blocked_apps")
data class BlockedApp(
    @PrimaryKey val packageName: String,
    val appName: String,
    val addedAt: Long = System.currentTimeMillis()
)
