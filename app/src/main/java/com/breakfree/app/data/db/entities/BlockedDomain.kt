package com.breakfree.app.data.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A domain blocked at the DNS level via BreakFreeVpnService.
 * Stored in lowercase, no scheme, no path (e.g. "instagram.com").
 */
@Entity(tableName = "blocked_domains")
data class BlockedDomain(
    @PrimaryKey val domain: String,
    val addedAt: Long = System.currentTimeMillis()
)
