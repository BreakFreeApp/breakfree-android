package com.breakfree.app.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.model.AppInfo
import com.breakfree.app.data.settings.BreakPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "AppPickerViewModel"

enum class SortOrder {
    ALPHABETICAL,
    USAGE
}

data class AppPickerUiState(
    val selectedApps: List<AppInfo> = emptyList(),
    val otherApps: List<AppInfo> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.USAGE,
    val isAscending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBreakActive: Boolean = false
)

class AppPickerViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)
    private val appRepository = breakFreeApp.appRepository
    private val breakStateManager = breakFreeApp.breakStateManager
    
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(SortOrder.USAGE)
    private val isAscending = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val localBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
    
    private var initialBlockedPackages: Set<String> = emptySet()

    val uiState: StateFlow<AppPickerUiState> = combine(
        appRepository.apps,
        localBlockedPackages,
        searchQuery,
        sortOrder,
        combine(isAscending, isRefreshing, breakStateManager.state) { a, r, s -> Triple(a, r, s) }
    ) { apps, localBlocked, query, order, misc ->
        val (ascending, refreshing, breakState) = misc
        try {
            val filtered = apps.filter { 
                it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
            
            val favoritePackages = apps.filter { it.isFavorite }.map { it.packageName }.toSet()
            val selectedPackages = localBlocked + favoritePackages
            
            val selectedToShow = apps.filter { it.packageName in selectedPackages }
            val othersToShow = filtered.filter { it.packageName !in selectedPackages }
            
            fun sort(list: List<AppInfo>, prioritizeBlocked: Boolean) = when (order) {
                SortOrder.ALPHABETICAL -> {
                    var comparator = compareBy<AppInfo> { it.appName.lowercase() }
                    if (prioritizeBlocked) {
                        comparator = compareByDescending<AppInfo> { it.packageName in localBlocked }
                            .thenBy { it.appName.lowercase() }
                    }
                    if (ascending) list.sortedWith(comparator) else list.sortedWith(comparator).reversed()
                }
                SortOrder.USAGE -> {
                    var comparator = compareByDescending<AppInfo> { it.usageTimeMs }
                        .thenByDescending { it.popularityScore }
                        .thenBy { it.appName.lowercase() }
                    if (prioritizeBlocked) {
                        comparator = compareByDescending<AppInfo> { it.packageName in localBlocked }
                            .thenByDescending { it.usageTimeMs }
                            .thenByDescending { it.popularityScore }
                            .thenBy { it.appName.lowercase() }
                    }
                    if (ascending) list.sortedWith(comparator).reversed() else list.sortedWith(comparator)
                }
            }
            
            AppPickerUiState(
                selectedApps = sort(selectedToShow, prioritizeBlocked = true),
                otherApps = sort(othersToShow, prioritizeBlocked = false),
                blockedPackages = localBlocked,
                searchQuery = query,
                sortOrder = order,
                isAscending = ascending,
                isRefreshing = refreshing,
                isBreakActive = breakState.phase == BreakPhase.ACTIVE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in combine block", e)
            AppPickerUiState(
                blockedPackages = localBlocked,
                searchQuery = query,
                sortOrder = order,
                isAscending = ascending,
                isRefreshing = refreshing,
                isBreakActive = breakState.phase == BreakPhase.ACTIVE
            )
        }
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        AppPickerUiState()
    )

    init {
        viewModelScope.launch {
            try {
                // Wait for cache to be ready if it's currently empty, with a timeout
                withTimeoutOrNull(3000) {
                    if (appRepository.apps.value.isEmpty()) {
                        appRepository.apps.first { it.isNotEmpty() }
                    }
                }
                
                val cachedApps = appRepository.apps.value
                val cachedBlocked = cachedApps.filter { it.isBlocked }.map { it.packageName }.toSet()
                
                initialBlockedPackages = cachedBlocked
                localBlockedPackages.value = initialBlockedPackages
                
                // Trigger lazy refresh of usage stats/popularity in background
                refreshApps()
            } catch (e: Exception) {
                Log.e(TAG, "Error in init block", e)
            }
        }
    }

    fun refreshApps() {
        viewModelScope.launch {
            try {
                isRefreshing.value = true
                appRepository.refreshCache(blockedPackageNames = localBlockedPackages.value)
                isRefreshing.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh apps", e)
                isRefreshing.value = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleSortOrder() {
        if (sortOrder.value == SortOrder.ALPHABETICAL) {
            if (isAscending.value) {
                // A-Z -> Z-A
                isAscending.value = false
            } else {
                // Z-A -> Usage (Most)
                sortOrder.value = SortOrder.USAGE
                isAscending.value = false
            }
        } else { // Current is USAGE
            if (!isAscending.value) {
                // Usage (Most) -> Usage (Least)
                isAscending.value = true
            } else {
                // Usage (Least) -> Alpha (A-Z)
                sortOrder.value = SortOrder.ALPHABETICAL
                isAscending.value = true
            }
        }
    }

    fun toggle(app: AppInfo, blocked: Boolean) {
        val current = localBlockedPackages.value.toMutableSet()
        if (blocked) current.add(app.packageName) else current.remove(app.packageName)
        localBlockedPackages.value = current
        
        // Save immediately to repository so other screens (like Home) update
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (blocked) {
                    breakFreeApp.repository.addApp(app.packageName, app.appName)
                } else {
                    breakFreeApp.repository.removeApp(app.packageName)
                }
                // Also update the metadata cache
                breakFreeApp.appRepository.updateBlockedStatus(localBlockedPackages.value)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle app in database", e)
            }
        }
    }
    
    fun toggleFavorite(app: AppInfo) {
        viewModelScope.launch {
            try {
                appRepository.toggleFavorite(app.packageName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite", e)
            }
        }
    }
}
