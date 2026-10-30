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
    AppsContent(apps, pinned, loading, error, viewModel::setPinned)
}

@Composable
fun AppsContent(apps: List<LaunchableApp>, pinned: List<com.husarp.holdfast.data.PinnedApp>,
    loading: Boolean = false, error: String? = null, setPinned: (String, Boolean, Long) -> Unit = { _, _, _ -> }) {
    var search by rememberSaveable { mutableStateOf("") }
    val selected = pinned.filter { it.enabled }.map { it.packageName to it.userSerial }.toSet()
    val filtered = apps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Choose launchable apps whose notifications Holdfast should capture. Apps without a launcher are not listed.")
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
                        modifier = Modifier.clickable { setPinned(app.packageName, !checked, app.userSerial) },
                        leadingContent = {
                            val icon = remember(app.icon) { app.icon.toBitmap(80, 80).asImageBitmap() }
                            Image(icon, null, Modifier.size(40.dp))
                        },
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = { Checkbox(checked, { setPinned(app.packageName, it, app.userSerial) }) })
                }
            }
        }
    }
}
