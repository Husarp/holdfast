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
    var search by rememberSaveable { mutableStateOf("") }
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }
    val selected = pinned.filter { it.enabled }.map { it.packageName }.toSet()
    val filtered = apps.filter { it.label.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Apps", style = MaterialTheme.typography.headlineMedium)
        Text("Choose launchable apps whose notifications Holdfast should capture. Apps without a launcher are not listed.")
        OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search apps") },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (loading) CircularProgressIndicator()
        else if (filtered.isEmpty()) Text("No matching apps.")
        LazyColumn {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                Row(Modifier.fillMaxWidth().clickable { viewModel.setPinned(app.packageName, !checked) }
                    .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val icon = remember(app.icon) { app.icon.toBitmap(48, 48).asImageBitmap() }
                    Image(icon, contentDescription = null, modifier = Modifier.size(40.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(app.label)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                    }
                    Checkbox(checked = checked, onCheckedChange = { viewModel.setPinned(app.packageName, it) })
                }
            }
        }
    }
}
