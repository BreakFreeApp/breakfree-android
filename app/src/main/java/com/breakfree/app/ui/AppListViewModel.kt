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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

private const val TAG = "AppListViewModel"

enum class SortOrder {
    ALPHABETICAL,
    USAGE
}

data class AppListUiState(
    val selectedApps: List<AppInfo> = emptyList(),
    val otherApps: List<AppInfo> = emptyList(),
    val blockedPackages: Set<String> = emptySet(),
    val lockedPackages: Set<String> = emptySet(),
    val searchQuery: String = "",
    val sortOrder: SortOrder = SortOrder.USAGE,
    val isAscending: Boolean = false,
    val isRefreshing: Boolean = false,
    val isBreakActive: Boolean = false
)

class AppListViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)
    private val appRepository = breakFreeApp.appRepository
    private val breakStateManager = breakFreeApp.breakStateManager
    
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(SortOrder.USAGE)
    private val isAscending = MutableStateFlow(false)
    private val isRefreshing = MutableStateFlow(false)
    private val localBlockedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val lockedPackages = MutableStateFlow<Set<String>>(emptySet())

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val appsFlow = appRepository.apps
    private val breakStateFlow = breakStateManager.state

    val uiState: StateFlow<AppListUiState> = combine(
        combine(appsFlow, localBlockedPackages, lockedPackages) { a, b, l -> Triple(a, b, l) },
        combine(searchQuery, sortOrder, isAscending) { q, o, asc -> Triple(q, o, asc) },
        combine(isRefreshing, breakStateFlow) { r, s -> r to s }
    ) { group1, group2, group3 ->
        val (apps, localBlocked, locked) = group1
        val (query, order, ascending) = group2
        val (refreshing, breakState) = group3
        
        try {
            val filtered = apps.filter { 
                it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
            
            val favoritePackages = apps.filter { it.isFavorite }.map { it.packageName }.toSet()
            val selectedPackages = localBlocked + favoritePackages
            
            val selectedToShow = filtered.filter { it.packageName in selectedPackages }
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
            
            AppListUiState(
                selectedApps = sort(selectedToShow, prioritizeBlocked = true),
                otherApps = sort(othersToShow, prioritizeBlocked = false),
                blockedPackages = localBlocked,
                lockedPackages = locked,
                searchQuery = query,
                sortOrder = order,
                isAscending = ascending,
                isRefreshing = refreshing,
                isBreakActive = breakState.phase == BreakPhase.ACTIVE
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in combine block", e)
            AppListUiState(
                blockedPackages = localBlocked,
                lockedPackages = locked,
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
        AppListUiState()
    )

    init {
        viewModelScope.launch {
            try {
                // Fetch the actual source of truth for blocked apps from the BlockingRepository
                val blockedFromDb = breakFreeApp.repository.blockedPackageNames()
                localBlockedPackages.value = blockedFromDb

                // Wait for cache to be ready if it's currently empty, with a timeout
                withTimeoutOrNull(3000) {
                    if (appRepository.apps.value.isEmpty()) {
                        appRepository.apps.first { it.isNotEmpty() }
                    }
                }
                
                // Determine which apps are initially "selected" (blocked or favorite)
                val apps = appRepository.apps.value
                val favorites = apps.filter { it.isFavorite }.map { it.packageName }.toSet()
                lockedPackages.value = blockedFromDb + favorites
                
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
        val isLocked = app.packageName in lockedPackages.value
        val isBreakActive = uiState.value.isBreakActive
        
        if (!blocked && isLocked && !isBreakActive) {
            viewModelScope.launch { _toastMessage.emit("removal is allowed only during breaks") }
            return
        }

        val current = localBlockedPackages.value.toMutableSet()
        if (blocked) current.add(app.packageName) else current.remove(app.packageName)
        val updatedSet = current.toSet()
        localBlockedPackages.value = updatedSet
        
        // Save immediately to repository so other screens (like Home) update
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (blocked) {
                    breakFreeApp.repository.addApp(app.packageName, app.appName)
                    // Requirement: when an entry is checked, also the fev button is set to on.
                    appRepository.setFavorite(app.packageName, true)
                } else {
                    breakFreeApp.repository.removeApp(app.packageName)
                }
                // Also update the metadata cache
                breakFreeApp.appRepository.updateBlockedStatus(updatedSet)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle app in database", e)
            }
        }
    }
    
    fun toggleFavorite(app: AppInfo) {
        val isLocked = app.packageName in lockedPackages.value
        val isBreakActive = uiState.value.isBreakActive
        val requestedFavorite = !app.isFavorite
        
        if (!requestedFavorite && isLocked && !isBreakActive) {
            viewModelScope.launch { _toastMessage.emit("removal is allowed only during breaks") }
            return
        }

        viewModelScope.launch {
            try {
                appRepository.setFavorite(app.packageName, requestedFavorite)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to toggle favorite", e)
            }
        }
    }
}
