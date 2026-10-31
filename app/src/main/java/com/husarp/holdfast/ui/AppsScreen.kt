package com.husarp.holdfast.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppsScreen(viewModel: AppsViewModel) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val pinned by viewModel.pinnedApps.collectAsStateWithLifecycle(emptyList())
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val container = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.husarp.holdfast.HoldfastApplication).container
    val history by container.repository.history.collectAsState(emptyList())
    val options by container.repository.options.collectAsState(emptyList())
    val rules by container.repository.rules.collectAsState(emptyList())
    val types by container.repository.types.collectAsState(emptyList())
    val settings by container.repository.settings.collectAsState(com.husarp.holdfast.data.Settings())
    var app by remember { mutableStateOf<LaunchableApp?>(null) }
    AppsContent(apps, pinned, loading, error, viewModel::setPinned, history, { app = it }, summaries = options.filter { it.profileId == settings.activeProfileId }.associate { option ->
        option.packageName to ((if (option.mode == com.husarp.holdfast.data.CopyMode.TAKE_OVER) "Takes over" else option.mode ?: "On swipe") +
            " · ${rules.count { it.packageName == option.packageName } + types.count { it.packageName == option.packageName && it.profileId == settings.activeProfileId }} rules · " +
            option.buttons.split('|').take(3).joinToString(", ") { com.husarp.holdfast.data.buttonLabel(it) })
    })
    app?.let { AppPage(it, { app = null }) }
}

@Composable
fun AppsContent(apps: List<LaunchableApp>, pinned: List<com.husarp.holdfast.data.PinnedApp>,
    loading: Boolean = false, error: String? = null, setPinned: (String, Boolean, Long) -> Unit = { _, _, _ -> },
    history: List<com.husarp.holdfast.data.NotificationEvent> = emptyList(), openApp: (LaunchableApp) -> Unit = {}, summaries: Map<String, String> = emptyMap()) {
    var search by rememberSaveable { mutableStateOf("") }
    val selected = pinned.filter { it.enabled }.map { it.packageName to it.userSerial }.toSet()
    var pinnedOnly by rememberSaveable { mutableStateOf(false) }
    val recent = history.filter { it.capturedAt >= System.currentTimeMillis() - 7 * 86_400_000L }.map { it.packageName to it.userSerial }.toSet()
    val filtered = apps.filter { !pinnedOnly || (it.packageName to it.userSerial) in selected }.sortedWith(compareBy<LaunchableApp> {
        com.husarp.holdfast.data.appSortRank((it.packageName to it.userSerial) in selected, (it.packageName to it.userSerial) in recent)
    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }).filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Choose apps to keep close.")
        FilterChip(pinnedOnly, { pinnedOnly = !pinnedOnly }, label = { Text("Pinned only") })
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search apps") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        else if (filtered.isEmpty()) EmptyState("Choose apps to keep their notifications close.", screenIcon("Apps"))
        LazyColumn {
            items(filtered, key = { it.packageName + ":" + it.userSerial }) { app ->
                val checked = (app.packageName to app.userSerial) in selected
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    ListItem(
                        modifier = Modifier.clickable { openApp(app) },
                        leadingContent = {
                            val icon = remember(app.icon) { app.icon.toBitmap(80, 80).asImageBitmap() }
                            Image(icon, null, Modifier.size(40.dp))
                        },
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(if (checked) summaries[app.packageName] ?: "On swipe · Snooze 60m, Dismiss" else app.packageName, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { Checkbox(checked, { setPinned(app.packageName, it, app.userSerial) }) })
                }
            }
        }
    }
}
