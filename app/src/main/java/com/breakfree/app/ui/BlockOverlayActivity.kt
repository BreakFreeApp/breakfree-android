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
import androidx.compose.material3.Button
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakfree.app.data.settings.BreakPhase
import com.breakfree.app.ui.theme.BreakFreeTheme

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

        setContent {
            BreakFreeTheme {
                BlockOverlayContent(blockedPackage = blockedPackage, onGoHome = { goHome() })
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
    }
}

@Composable
private fun BlockOverlayContent(blockedPackage: String, onGoHome: () -> Unit) {
    val context = LocalContext.current
    val app = com.breakfree.app.BreakFreeApplication.from(context)
    val breakState by app.breakStateManager.state.collectAsStateWithLifecycle()

    // If a break is (or becomes) active, this screen has no reason to stay up.
    if (breakState.phase == BreakPhase.ACTIVE) {
        onGoHome()
        return
    }

    val appLabel = runCatching {
        context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(blockedPackage, 0)
        ).toString()
    }.getOrDefault(blockedPackage)

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("$appLabel is blocked", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Open BreakFree and request a break to access it temporarily.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )
            Button(onClick = {
                val breakFreeIntent = Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(breakFreeIntent)
            }) { Text("Open BreakFree") }

            Button(onClick = onGoHome, modifier = Modifier.padding(top = 12.dp)) { Text("Go home") }
        }
    }
}
