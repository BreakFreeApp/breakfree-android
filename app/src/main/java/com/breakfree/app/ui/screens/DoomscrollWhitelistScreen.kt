package com.breakfree.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.HourglassEmpty
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.breakfree.app.data.model.AppInfo
import com.breakfree.app.ui.DoomscrollWhitelistViewModel
import com.breakfree.app.ui.SortOrder
import com.breakfree.app.ui.components.SearchTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoomscrollWhitelistScreen(
    onBack: () -> Unit,
    viewModel: DoomscrollWhitelistViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Column {
                SearchTopAppBar(
                    title = "Doomscroll Whitelist",
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
        if (state.apps.isEmpty() && state.isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                Text(
                    text = "Apps whitelisted here will not trigger doomscrolling warnings even if used for long periods.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp)
                )
                
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.apps, key = { it.packageName }) { app ->
                        WhitelistAppItem(
                            app = app,
                            onToggle = { viewModel.toggleWhitelist(app) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WhitelistAppItem(
    app: AppInfo,
    onToggle: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = app.isDoomscrollWhitelisted,
                onCheckedChange = null
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
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
    }
}
