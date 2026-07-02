package com.breakfree.app.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.breakfree.app.BreakFreeApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppInfo(val packageName: String, val appName: String)

data class AppPickerUiState(
    val allApps: List<InstalledAppInfo> = emptyList(),
    val blockedPackages: Set<String> = emptySet()
)

class AppPickerViewModel(app: Application) : AndroidViewModel(app) {

    private val breakFreeApp = BreakFreeApplication.from(app)
    private val installedApps = MutableStateFlow<List<InstalledAppInfo>>(emptyList())

    val uiState: StateFlow<AppPickerUiState> = combine(
        installedApps,
        breakFreeApp.repository.observeBlockedApps()
    ) { apps, blocked ->
        AppPickerUiState(apps, blocked.map { it.packageName }.toSet())
    }.stateIn(
        viewModelScope,
        kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
        AppPickerUiState()
    )

    init {
        viewModelScope.launch {
            installedApps.value = withContext(Dispatchers.IO) { loadLaunchableApps() }
        }
    }

    private fun loadLaunchableApps(): List<InstalledAppInfo> {
        val pm = getApplication<Application>().packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val ownPackage = getApplication<Application>().packageName
        return pm.queryIntentActivities(intent, 0)
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != ownPackage }
            .map { resolveInfo ->
                InstalledAppInfo(
                    packageName = resolveInfo.activityInfo.packageName,
                    appName = resolveInfo.loadLabel(pm).toString()
                )
            }
            .sortedBy { it.appName.lowercase() }
    }

    fun toggle(app: InstalledAppInfo, blocked: Boolean) {
        viewModelScope.launch {
            if (blocked) breakFreeApp.repository.addApp(app.packageName, app.appName)
            else breakFreeApp.repository.removeApp(app.packageName)
        }
    }
}
