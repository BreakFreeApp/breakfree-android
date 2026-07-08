package com.breakfree.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.breakfree.app.data.db.entities.BlockedDomain
import com.breakfree.app.ui.DomainSortOrder
import com.breakfree.app.ui.DomainListViewModel
import com.breakfree.app.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DomainListScreen(
    onBack: () -> Unit,
    onNavigateToBreak: () -> Unit,
    viewModel: DomainListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            SearchTopAppBar(
                title = "Websites",
                searchQuery = state.searchQuery,
                onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                onBack = onBack,
                placeholder = "Search domains...",
                showLogo = false,
                actions = {
                    IconButton(onClick = { viewModel.toggleSortOrder() }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (state.sortOrder) {
                                DomainSortOrder.ALPHABETICAL -> Icons.Default.SortByAlpha
                                DomainSortOrder.TIME_ADDED -> Icons.Default.History
                            }
                            Icon(icon, contentDescription = "Toggle Sort", modifier = Modifier.size(18.dp))
                            Icon(
                                if (state.isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("e.g. instagram.com") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.add(input); input = "" },
                    enabled = input.isNotBlank()
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.selectedDomains.isNotEmpty()) {
                    item(key = "header_selected") {
                        SectionHeader("Blocked & Favorites")
                    }
                    items(state.selectedDomains, key = { "selected_${it.domain}" }) { domain ->
                        DomainItem(
                            domain = domain,
                            onToggleBlocked = { requestedBlocked ->
                                // Allowing adding (true) but restricting removing (false)
                                if (!requestedBlocked && !state.isBreakActive) {
                                    onNavigateToBreak()
                                } else {
                                    viewModel.toggleBlock(domain, requestedBlocked)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(domain) }
                        )
                    }
                }

                if (state.otherDomains.isNotEmpty()) {
                    item(key = "header_others") {
                        SectionHeader(if (state.selectedDomains.isEmpty()) "Available" else "Others")
                    }
                    items(state.otherDomains, key = { "other_${it.domain}" }) { domain ->
                        DomainItem(
                            domain = domain,
                            onToggleBlocked = { requestedBlocked ->
                                // Allowing adding (true) but restricting removing (false)
                                if (!requestedBlocked && !state.isBreakActive) {
                                    onNavigateToBreak()
                                } else {
                                    viewModel.toggleBlock(domain, requestedBlocked)
                                }
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(domain) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun DomainItem(
    domain: BlockedDomain,
    onToggleBlocked: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleBlocked(!domain.isBlocked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = domain.isBlocked,
                onCheckedChange = null // Handled by Row clickable
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = domain.domain,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (domain.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (domain.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
