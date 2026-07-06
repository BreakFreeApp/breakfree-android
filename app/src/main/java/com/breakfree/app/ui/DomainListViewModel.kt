package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.db.entities.BlockedDomain
import com.breakfree.app.data.settings.AppDefaults
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class DomainSortOrder {
    ALPHABETICAL,
    TIME_ADDED
}

data class DomainListUiState(
    val selectedDomains: List<BlockedDomain> = emptyList(),
    val otherDomains: List<BlockedDomain> = emptyList(),
    val searchQuery: String = "",
    val sortOrder: DomainSortOrder = DomainSortOrder.ALPHABETICAL,
    val isAscending: Boolean = true
)

class DomainListViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)
    private val repository = breakFreeApp.repository
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(DomainSortOrder.ALPHABETICAL)
    private val isAscending = MutableStateFlow(true)

    val uiState: StateFlow<DomainListUiState> = combine(
        repository.observeBlockedDomains(),
        searchQuery,
        sortOrder,
        isAscending
    ) { domains, query, order, ascending ->
        val filtered = if (query.isBlank()) {
            domains
        } else {
            domains.filter { it.domain.contains(query, ignoreCase = true) }
        }

        // Suggested domains from AppDefaults that are not in the database yet
        val suggestedDomains = AppDefaults.DOOM_SCROLLING_DOMAINS
            .filter { domainStr -> domains.none { it.domain == domainStr } }
            .filter { it.contains(query, ignoreCase = true) }
            .map { BlockedDomain(it, isFavorite = true, isBlocked = false) }

        val allToConsider = filtered + suggestedDomains
        
        val selected = allToConsider.filter { it.isBlocked || it.isFavorite }
        val others = allToConsider.filter { !it.isBlocked && !it.isFavorite }

        fun sort(list: List<BlockedDomain>) = when (order) {
            DomainSortOrder.ALPHABETICAL -> {
                if (ascending) list.sortedBy { it.domain } else list.sortedByDescending { it.domain }
            }
            DomainSortOrder.TIME_ADDED -> {
                if (ascending) list.sortedBy { it.addedAt } else list.sortedByDescending { it.addedAt }
            }
        }

        DomainListUiState(
            selectedDomains = sort(selected),
            otherDomains = sort(others),
            searchQuery = query,
            sortOrder = order,
            isAscending = ascending
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DomainListUiState())

    fun onSearchQueryChanged(query: String) {
        searchQuery.value = query
    }

    fun toggleSortOrder() {
        if (sortOrder.value == DomainSortOrder.ALPHABETICAL) {
            if (isAscending.value) {
                isAscending.value = false // A-Z -> Z-A
            } else {
                sortOrder.value = DomainSortOrder.TIME_ADDED
                isAscending.value = false // Z-A -> Time (Newest first)
            }
        } else {
            if (!isAscending.value) {
                isAscending.value = true // Time (Newest) -> Time (Oldest)
            } else {
                sortOrder.value = DomainSortOrder.ALPHABETICAL
                isAscending.value = true // Time (Oldest) -> Alpha (A-Z)
            }
        }
    }

    fun toggleBlock(domain: BlockedDomain, blocked: Boolean) {
        viewModelScope.launch {
            repository.toggleDomainBlock(domain.domain, blocked)
        }
    }

    fun toggleFavorite(domain: BlockedDomain) {
        viewModelScope.launch {
            repository.toggleDomainFavorite(domain.domain)
        }
    }

    fun add(domain: String) {
        val cleaned = domain.trim().lowercase()
        if (cleaned.isEmpty()) return
        viewModelScope.launch { repository.addDomain(cleaned) }
    }

    fun remove(domain: BlockedDomain) {
        viewModelScope.launch { repository.removeDomain(domain.domain) }
    }
}
