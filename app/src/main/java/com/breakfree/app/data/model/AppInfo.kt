package com.breakfree.app.data.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val usageTimeMs: Long = 0,
    val popularityScore: Int = 0,
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false
)
