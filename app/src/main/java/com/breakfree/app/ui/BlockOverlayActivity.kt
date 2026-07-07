package com.breakfree.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakfree.app.R
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.theme.BreakFreeTheme
import kotlinx.coroutines.delay

class BlockOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prevent back-press from revealing the blocked app underneath: go home instead.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goHome()
            }
        })

        val blockedPackage = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE) ?: ""
        val blockedDomain = intent.getStringExtra(EXTRA_BLOCKED_DOMAIN)

        setContent {
            BreakFreeTheme {
                BlockOverlayContent(
                    blockedPackage = blockedPackage, 
                    blockedDomain = blockedDomain,
                    onGoHome = { goHome() }
                )
            }
        }
    }

    private fun goHome() {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(homeIntent)
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_BLOCKED_DOMAIN = "blocked_domain"
    }
}

@Composable
private fun BlockOverlayContent(blockedPackage: String, blockedDomain: String?, onGoHome: () -> Unit) {
    val context = LocalContext.current
    val app = com.breakfree.app.BreakFreeApplication.from(context)
    val breakState by app.breakStateManager.state.collectAsStateWithLifecycle()
    
    var isVisible by remember { mutableStateOf(false) }

    LaunchedEffect(breakState.phase) {
        if (breakState.phase == BreakPhase.ACTIVE) {
            onGoHome()
        }
    }
    
    LaunchedEffect(Unit) {
        // Keep it transparent for a brief moment to avoid flickering
        delay(100)
        isVisible = true
    }

    if (breakState.phase == BreakPhase.ACTIVE) return

    val appLabel = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(blockedPackage, 0)
        ).toString()
    }.getOrDefault(blockedPackage)

    val blockMessage = if (blockedDomain != null) {
        "$blockedDomain is blocked on $appLabel"
    } else {
        "$appLabel is blocked"
    }

    Scaffold(
        containerColor = if (isVisible) MaterialTheme.colorScheme.background else Color.Transparent
    ) { padding ->
        if (isVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier
                        .size(80.dp)
                        .padding(bottom = 16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(blockMessage, style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                
                if (breakState.phase == BreakPhase.GRACE) {
                    Text(
                        "Break starts in ${formatCountdown(((breakState.graceEndsAtEpochMs - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else if (breakState.phase == BreakPhase.CHALLENGE) {
                    Text(
                        "Confirm the break in BreakFree to unlock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Text(
                    "Open BreakFree and request a break to access it temporarily.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Button(onClick = onGoHome) { Text("Go home") }
            }
        }
    }
}

private fun formatCountdown(seconds: Int): String {
    return when {
        seconds < 60 -> "${seconds}s"
        seconds < 3600 -> {
            val m = seconds / 60
            val s = seconds % 60
            "${m}m ${s}s"
        }
        else -> {
            val h = seconds / 3600
            val m = (seconds % 3600) / 60
            "${h}h ${m}m"
        }
    }
}
