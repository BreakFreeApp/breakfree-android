package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.db.entities.BlockedDomain
import com.breakfree.app.data.settings.AppDefaults
import com.breakfree.app.data.settings.BreakPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first

enum class DomainSortOrder {
    ALPHABETICAL,
    TIME_ADDED
}

data class DomainListUiState(
    val selectedDomains: List<BlockedDomain> = emptyList(),
    val otherDomains: List<BlockedDomain> = emptyList(),
    val lockedDomains: Set<String> = emptySet(),
    val searchQuery: String = "",
    val sortOrder: DomainSortOrder = DomainSortOrder.ALPHABETICAL,
    val isAscending: Boolean = true,
    val isBreakActive: Boolean = false
)

class DomainListViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)
    private val repository = breakFreeApp.repository
    private val breakStateManager = breakFreeApp.breakStateManager
    
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(DomainSortOrder.ALPHABETICAL)
    private val isAscending = MutableStateFlow(true)
    private val lockedDomains = MutableStateFlow<Set<String>>(emptySet())

    private val _toastMessage = MutableSharedFlow<String>()
    val toastMessage = _toastMessage.asSharedFlow()

    private val domainsFlow = repository.observeBlockedDomains()
    private val breakStateFlow = breakStateManager.state

    val uiState: StateFlow<DomainListUiState> = combine(
        combine(domainsFlow, lockedDomains, searchQuery) { d, l, q -> Triple(d, l, q) },
        combine(sortOrder, isAscending, breakStateFlow) { o, a, s -> Triple(o, a, s) }
    ) { group1, group2 ->
        val (domains, locked, query) = group1
        val (order, ascending, breakState) = group2
        val filtered = if (query.isBlank()) {
            domains
        } else {
            domains.filter { it.domain.contains(query, ignoreCase = true) }
        }

        // Suggested domains from AppDefaults that are not in the database yet
        val suggestedDomains = AppDefaults.DOOM_SCROLLING_DOMAINS
            .filter { domainStr -> domains.none { it.domain == domainStr } }
            .filter { it.contains(query, ignoreCase = true) }
            .map { BlockedDomain(it, isFavorite = false, isBlocked = false) }

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
            lockedDomains = locked,
            searchQuery = query,
            sortOrder = order,
            isAscending = ascending,
            isBreakActive = breakState.phase == BreakPhase.ACTIVE
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DomainListUiState())

    init {
        viewModelScope.launch {
            try {
                // Wait for the first emission of domains to determine which ones are "locked" for this session
                val initialDomains = repository.observeBlockedDomains().first()
                val selected = initialDomains.filter { it.isBlocked || it.isFavorite }.map { it.domain }.toSet()
                lockedDomains.value = selected
            } catch (e: Exception) {
                // Fallback or log
            }
        }
    }

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
        val isLocked = domain.domain in lockedDomains.value
        val isBreakActive = uiState.value.isBreakActive
        
        if (!blocked && isLocked && !isBreakActive) {
            viewModelScope.launch { _toastMessage.emit("removal is allowed only during breaks") }
            return
        }

        viewModelScope.launch {
            repository.toggleDomainBlock(domain.domain, blocked)
            if (blocked) {
                // Requirement: when an entry is checked, also the fev button is set to on.
                if (!domain.isFavorite) {
                    repository.toggleDomainFavorite(domain.domain)
                }
            }
        }
    }

    fun toggleFavorite(domain: BlockedDomain) {
        val isLocked = domain.domain in lockedDomains.value
        val isBreakActive = uiState.value.isBreakActive
        val requestedFavorite = !domain.isFavorite
        
        if (!requestedFavorite && isLocked && !isBreakActive) {
            viewModelScope.launch { _toastMessage.emit("removal is allowed only during breaks") }
            return
        }

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
