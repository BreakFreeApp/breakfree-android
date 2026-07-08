package com.breakfree.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.breakfree.app.data.model.AppInfo
import com.breakfree.app.ui.AppListViewModel
import com.breakfree.app.ui.SortOrder
import com.breakfree.app.ui.components.SearchTopAppBar
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    onBack: () -> Unit,
    viewModel: AppListViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            Column {
                SearchTopAppBar(
                    title = "App List",
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = { viewModel.onSearchQueryChanged(it) },
                    onBack = onBack,
                    placeholder = "Search apps...",
                    showLogo = false,
                    actions = {
                        IconButton(onClick = { viewModel.toggleSortOrder() }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val icon = when (state.sortOrder) {
                                    SortOrder.ALPHABETICAL -> Icons.Default.SortByAlpha
                                    SortOrder.USAGE -> Icons.Default.HourglassEmpty
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
                if (state.isRefreshing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    ) { padding ->
        if (state.selectedApps.isEmpty() && state.otherApps.isEmpty() && state.isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (state.selectedApps.isNotEmpty()) {
                    item(key = "header_selected") {
                        SectionHeader("Blocked & Favorites")
                    }
                    items(state.selectedApps, key = { "selected_${it.packageName}" }) { app ->
                        val isBlocked = app.packageName in state.blockedPackages
                        AppItem(
                            app = app,
                            isBlocked = isBlocked,
                            onToggleBlocked = { requestedBlocked ->
                                viewModel.toggle(app, requestedBlocked)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(app) }
                        )
                    }
                }
                
                if (state.otherApps.isNotEmpty()) {
                    item(key = "header_others") {
                        SectionHeader(if (state.selectedApps.isEmpty()) "Available" else "Other apps")
                    }
                    items(state.otherApps, key = { "other_${it.packageName}" }) { app ->
                        AppItem(
                            app = app,
                            isBlocked = false,
                            onToggleBlocked = { requestedBlocked ->
                                viewModel.toggle(app, requestedBlocked)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(app) }
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
private fun AppItem(
    app: AppInfo,
    isBlocked: Boolean,
    onToggleBlocked: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleBlocked(!isBlocked) }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isBlocked,
                onCheckedChange = null // Handled by Row clickable
            )
            Spacer(modifier = Modifier.width(8.dp))
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest.Builder(LocalContext.current)
                        .data("appicon://${app.packageName}")
                        .crossfade(true)
                        .build()
                ),
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = app.appName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (app.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Toggle Favorite",
                    tint = if (app.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
