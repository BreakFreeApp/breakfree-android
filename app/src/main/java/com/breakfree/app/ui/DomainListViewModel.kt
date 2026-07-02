package com.breakfree.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.data.db.entities.BlockedDomain
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DomainListViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)

    val domains: StateFlow<List<BlockedDomain>> = breakFreeApp.repository.observeBlockedDomains()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(domain: String) {
        val cleaned = domain.trim().lowercase()
        if (cleaned.isEmpty()) return
        viewModelScope.launch { breakFreeApp.repository.addDomain(cleaned) }
    }

    fun remove(domain: BlockedDomain) {
        viewModelScope.launch { breakFreeApp.repository.removeDomain(domain.domain) }
    }
}
