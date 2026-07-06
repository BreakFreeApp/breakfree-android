package com.breakfree.app.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.util.Log
import com.breakfree.app.data.db.dao.AppMetadataDao
import com.breakfree.app.data.db.entities.AppMetadata
import com.breakfree.app.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.Calendar

private const val TAG = "AppRepository"

class AppRepository(
    private val context: Context,
    private val appMetadataDao: AppMetadataDao
) {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val pm = context.packageManager
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    
    private val prefs = context.getSharedPreferences("app_repository_prefs", Context.MODE_PRIVATE)
    private val LAST_REFRESH_KEY = "last_full_refresh_timestamp"

    /**
     * Quickly preloads from database cache.
     */
    suspend fun preload() = withContext(Dispatchers.IO) {
        try {
            val cached = appMetadataDao.getAll()
            if (cached.isNotEmpty()) {
                _apps.value = cached.map { it.toAppInfo() }
            } else {
                val basic = getLaunchableApps()
                _apps.value = basic
                appMetadataDao.insertAll(basic.map { it.toEntity() })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to preload apps", e)
        }
    }

    suspend fun refreshCache(force: Boolean = false, blockedPackageNames: Set<String> = emptySet()) = withContext(Dispatchers.IO) {
        try {
            val lastRefresh = prefs.getLong(LAST_REFRESH_KEY, 0L)
            val now = System.currentTimeMillis()
            
            if (!force && isSameDay(lastRefresh, now) && _apps.value.any { it.usageTimeMs > 0 }) {
                val current = _apps.value
                if (current.isNotEmpty() && blockedPackageNames.isNotEmpty()) {
                    val updated = current.map { it.copy(isBlocked = blockedPackageNames.contains(it.packageName)) }
                    _apps.value = updated
                    appMetadataDao.insertAll(updated.map { it.toEntity() })
                }
                return@withContext
            }

            val launchable = getLaunchableApps()
            val usageStats = if (isUsageStatsPermissionGranted()) getUsageStats() else emptyMap()
            val popularity = fetchPopularity()
            
            val existing = appMetadataDao.getAll().associateBy { it.packageName }

            val updated = launchable.map { app ->
                val cached = existing[app.packageName]
                app.copy(
                    usageTimeMs = usageStats[app.packageName] ?: 0L,
                    popularityScore = popularity[app.packageName] ?: 0,
                    isFavorite = cached?.isFavorite ?: false,
                    isBlocked = blockedPackageNames.contains(app.packageName)
                )
            }
            
            _apps.value = updated
            appMetadataDao.insertAll(updated.map { it.toEntity() })
            prefs.edit().putLong(LAST_REFRESH_KEY, now).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh app cache", e)
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    private fun getLaunchableApps(): List<AppInfo> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = context.packageName
        
        return try {
            pm.queryIntentActivities(intent, 0)
                .distinctBy { it.activityInfo.packageName }
                .filter { it.activityInfo.packageName != ownPackage }
                .map { resolveInfo ->
                    AppInfo(
                        packageName = resolveInfo.activityInfo.packageName,
                        appName = resolveInfo.loadLabel(pm).toString()
                    )
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query launchable apps", e)
            emptyList()
        }
    }

    private fun getUsageStats(): Map<String, Long> {
        val usm = usageStatsManager ?: return emptyMap()
        return try {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            val endTime = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val startTime = cal.timeInMillis

            val statsList = usm.queryUsageStats(UsageStatsManager.INTERVAL_BEST, startTime, endTime)
            if (statsList.isNullOrEmpty()) {
                val aggregated = usm.queryAndAggregateUsageStats(startTime, endTime)
                return aggregated.mapValues { it.value.totalTimeInForeground }
            }
            statsList.groupBy { it.packageName }
                .mapValues { entry -> entry.value.sumOf { it.totalTimeInForeground } }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query usage stats", e)
            emptyMap()
        }
    }

    private fun fetchPopularity(): Map<String, Int> {
        return try {
            mapOf(
                "com.instagram.android" to 100,
                "com.facebook.katana" to 95,
                "com.twitter.android" to 80,
                "com.zhiliaoapp.musically" to 110,
                "com.google.android.youtube" to 120
            )
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun toggleFavorite(packageName: String) = withContext(Dispatchers.IO) {
        try {
            val current = _apps.value
            val app = current.find { it.packageName == packageName } ?: return@withContext
            val newFav = !app.isFavorite
            
            appMetadataDao.updateFavorite(packageName, newFav)
            _apps.value = current.map {
                if (it.packageName == packageName) it.copy(isFavorite = newFav) else it
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle favorite", e)
        }
    }

    suspend fun updateBlockedStatus(blockedPackageNames: Set<String>) = withContext(Dispatchers.IO) {
        try {
            val current = _apps.value
            if (current.isNotEmpty()) {
                val updated = current.map { it.copy(isBlocked = blockedPackageNames.contains(it.packageName)) }
                _apps.value = updated
                appMetadataDao.insertAll(updated.map { it.toEntity() })
            } else {}
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update blocked status", e)
        }
    }

    private fun AppMetadata.toAppInfo() = AppInfo(
        packageName = packageName,
        appName = appName,
        usageTimeMs = usageTimeMs,
        popularityScore = popularityScore,
        isFavorite = isFavorite,
        isBlocked = isBlocked
    )

    private fun AppInfo.toEntity() = AppMetadata(
        packageName = packageName,
        appName = appName,
        usageTimeMs = usageTimeMs,
        popularityScore = popularityScore,
        isFavorite = isFavorite,
        isBlocked = isBlocked
    )
}
