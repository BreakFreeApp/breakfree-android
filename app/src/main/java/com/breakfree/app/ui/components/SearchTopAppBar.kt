package com.breakfree.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.breakfree.android.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchTopAppBar(
    title: String,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onBack: (() -> Unit)? = null,
    placeholder: String = "Search...",
    searchEnabled: Boolean = true,
    showLogo: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    TopAppBar(
        title = {
            if (isSearchExpanded && searchEnabled) {
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    placeholder = { Text(placeholder, style = MaterialTheme.typography.bodyMedium) },
                    trailingIcon = {
                        IconButton(onClick = {
                            isSearchExpanded = false
                            onSearchQueryChange("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Close Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    )
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showLogo) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_launcher_foreground),
                            contentDescription = null,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(end = 8.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(title, style = MaterialTheme.typography.titleLarge)
                }
            }
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (!isSearchExpanded && searchEnabled) {
                IconButton(onClick = { isSearchExpanded = true }) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
            }
            actions()
        }
    )
}
