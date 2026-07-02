package com.breakfree.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.breakfree.app.BreakFreeApplication
import com.breakfree.app.ui.BlockOverlayActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Watches window-state-changed events (fires on every foreground app switch) and,
 * for any app on the blocked list, immediately launches a full-screen interstitial
 * — UNLESS a global break is currently ACTIVE.
 *
 * We deliberately don't use UsageStatsManager polling: accessibility events fire
 * instantly on app switch, which is what makes the "blocked by default" experience
 * feel instant rather than blocking-after-a-delay.
 */
class BreakFreeAccessibilityService : AccessibilityService() {

    private lateinit var scope: CoroutineScope
    @Volatile private var blockedPackages: Set<String> = emptySet()
    private var ownPackageName: String = ""

    override fun onCreate() {
        super.onCreate()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        ownPackageName = packageName
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val app = BreakFreeApplication.from(this)
        scope.launch {
            app.repository.observeBlockedApps().collectLatest { apps ->
                blockedPackages = apps.map { it.packageName }.toSet()
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == ownPackageName) return
        if (pkg !in blockedPackages) return

        val app = BreakFreeApplication.from(this)
        if (app.breakStateManager.isBreakActiveNow()) return

        launchBlockOverlay(pkg)
    }

    private fun launchBlockOverlay(blockedPackage: String) {
        val intent = Intent(this, BlockOverlayActivity::class.java)
            .putExtra(BlockOverlayActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            )
        startActivity(intent)
    }

    override fun onInterrupt() { /* no-op */ }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
