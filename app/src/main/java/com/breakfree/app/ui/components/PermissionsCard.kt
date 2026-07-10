package com.breakfree.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class PermissionToggleState {
    CLOSED, OPEN, MISSING
}

data class PermissionInfo(
    val name: String,
    val description: String,
    val isGranted: Boolean,
    val onEnable: () -> Unit
)

@Composable
fun PermissionsCard(
    permissions: List<PermissionInfo>,
    modifier: Modifier = Modifier
) {
    var toggleState by remember { mutableStateOf(PermissionToggleState.MISSING) }
    
    val grantedCount = permissions.count { it.isGranted }
    val totalCount = permissions.size
    
    if (grantedCount == totalCount) return

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "permissions ($grantedCount/$totalCount)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(
                    onClick = {
                        toggleState = when (toggleState) {
                            PermissionToggleState.MISSING -> PermissionToggleState.OPEN
                            PermissionToggleState.OPEN -> PermissionToggleState.CLOSED
                            PermissionToggleState.CLOSED -> PermissionToggleState.MISSING
                        }
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    val icon = when (toggleState) {
                        PermissionToggleState.MISSING -> Icons.Default.Visibility
                        PermissionToggleState.OPEN -> Icons.Default.ExpandLess
                        PermissionToggleState.CLOSED -> Icons.Default.ExpandMore
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Toggle permissions view",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            if (toggleState != PermissionToggleState.CLOSED) {
                val visiblePermissions = when (toggleState) {
                    PermissionToggleState.OPEN -> permissions
                    PermissionToggleState.MISSING -> permissions.filter { !it.isGranted }
                    else -> emptyList()
                }
                
                if (visiblePermissions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        visiblePermissions.forEach { permission ->
                            PermissionRow(permission)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(permission: PermissionInfo) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = permission.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val baseStyle = MaterialTheme.typography.bodySmall
            var fontSize by remember { mutableStateOf(baseStyle.fontSize) }
            Text(
                text = permission.description,
                style = baseStyle.copy(fontSize = fontSize),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { result ->
                    if (result.hasVisualOverflow) {
                        fontSize = fontSize * 0.9f
                    }
                }
            )
        }
        
        Button(
            onClick = permission.onEnable,
            enabled = !permission.isGranted
        ) {
            Text(if (permission.isGranted) "Enabled" else "Enable")
        }
    }
}
