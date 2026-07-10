package com.breakfree.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.model.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "DoomscrollWhitelistVM"

data class DoomscrollWhitelistUiState(
    val apps: List<AppInfo> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.USAGE,
    val isAscending: Boolean = false,
    val isRefreshing: Boolean = false
)

class DoomscrollWhitelistViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)
    private val appRepository = breakFreeApp.appRepository
    
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(SortOrder.USAGE)
    private val isAscending = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)

    val uiState: StateFlow<DoomscrollWhitelistUiState> = combine(
        appRepository.apps,
        searchQuery,
        sortOrder,
        isAscending,
        isRefreshing
    ) { apps, query, order, ascending, refreshing ->
        // Only show unblocked apps for whitelisting
        val unblockedApps = apps.filter { !it.isBlocked }
        
        val filtered = unblockedApps.filter { 
            it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }
        
        val sorted = when (order) {
            SortOrder.ALPHABETICAL -> {
                val comparator = compareBy<AppInfo> { it.appName.lowercase() }
                if (ascending) filtered.sortedWith(comparator) else filtered.sortedWith(comparator).reversed()
            }
            SortOrder.USAGE -> {
                val comparator = compareByDescending<AppInfo> { it.usageTimeMs }
                    .thenByDescending { it.popularityScore }
                    .thenBy { it.appName.lowercase() }
                if (ascending) filtered.sortedWith(comparator).reversed() else filtered.sortedWith(comparator)
            }
        }
        
        DoomscrollWhitelistUiState(
            apps = sorted,
            searchQuery = query,
            sortOrder = order,
            isAscending = ascending,
            isRefreshing = refreshing
        )
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        DoomscrollWhitelistUiState()
    )

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleSortOrder() {
        if (sortOrder.value == SortOrder.ALPHABETICAL) {
            if (isAscending.value) {
                isAscending.value = false
            } else {
                sortOrder.value = SortOrder.USAGE
                isAscending.value = false
            }
        } else {
            if (!isAscending.value) {
                isAscending.value = true
            } else {
                sortOrder.value = SortOrder.ALPHABETICAL
                isAscending.value = true
            }
        }
    }

    fun toggleWhitelist(app: AppInfo) {
        viewModelScope.launch {
            try {
                appRepository.toggleDoomscrollWhitelist(app.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle whitelist", e)
            }
        }
    }
    
    fun refreshApps() {
        viewModelScope.launch {
            try {
                isRefreshing.value = true
                // Get current blocked apps to keep them out
                val blockedFromDb = breakFreeApp.repository.blockedPackageNames()
                appRepository.refreshCache(blockedPackageNames = blockedFromDb)
                isRefreshing.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh apps", e)
                isRefreshing.value = false
            }
        }
    }
}
