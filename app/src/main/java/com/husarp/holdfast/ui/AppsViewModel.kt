package com.husarp.holdfast.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.husarp.holdfast.HoldfastApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LaunchableApp(val packageName: String, val label: String, val icon: Drawable, val userSerial: Long = 0)

class AppsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HoldfastApplication).container.repository
    val pinnedApps = repository.pinnedApps
    private val _apps = MutableStateFlow<List<LaunchableApp>>(emptyList())
    val apps = _apps.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _apps.value = withContext(Dispatchers.IO) {
                    val app = getApplication<Application>()
                    val pm = app.packageManager
                    val launcher = app.getSystemService(android.content.pm.LauncherApps::class.java)
                    val users = app.getSystemService(android.os.UserManager::class.java)
                    launcher.profiles.flatMap { user ->
                        launcher.getActivityList(null, user).distinctBy { it.componentName.packageName }
                            .filter { it.componentName.packageName != app.packageName }
                            .map { LaunchableApp(it.componentName.packageName,
                                pm.getUserBadgedLabel(it.label, user).toString(),
                                it.getBadgedIcon(0), users.getSerialNumberForUser(user)) }
                    }
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
                }
                _error.value = null
            } catch (_: RuntimeException) {
                _error.value = "Could not load apps. Reopen this screen to retry."
            } finally {
                _loading.value = false
            }
        }
    }

    fun setPinned(packageName: String, enabled: Boolean, userSerial: Long = 0) {
        viewModelScope.launch {
            try {
                repository.setPinned(packageName, enabled, userSerial)
                (getApplication<Application>() as HoldfastApplication).container.copies.recover()
                _error.value = null
            } catch (_: RuntimeException) {
                _error.value = "Could not save selection. Please try again."
            }
        }
    }
}
