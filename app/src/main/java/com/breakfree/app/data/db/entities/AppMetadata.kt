package com.breakfree.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_metadata_cache")
data class AppMetadata(
    @PrimaryKey val packageName: String,
    val appName: String,
    val usageTimeMs: Long = 0,
    val popularityScore: Int = 0,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val isDoomscrollWhitelisted: Boolean = false
)
