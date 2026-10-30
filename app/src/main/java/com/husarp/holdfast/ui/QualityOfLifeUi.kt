package com.husarp.holdfast.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

@Composable
fun FirstRunGuide(openSetup: () -> Unit, openApps: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as HoldfastApplication).container
    val pinned by container.repository.pinnedApps.collectAsState(emptyList())
    val prefs = remember { context.getSharedPreferences("guide", 0) }
    var done by remember { mutableStateOf(prefs.getBoolean("done", false)) }
    var access by remember { mutableStateOf(setupStatus(context)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        access = setupStatus(context)
        onPauseOrDispose { }
    }
    if (!done) Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Get started", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = openApps) { Text("1. Pick apps${if (pinned.any { it.enabled }) " ✓" else ""}") }
            TextButton(onClick = openSetup) { Text("2. Grant access") }
            Button(enabled = pinned.any { it.enabled } && access.listener && access.posting, onClick = {
                if (pinned.any { it.enabled } && setupStatus(context).let { it.listener && it.posting } && com.husarp.holdfast.notifications.sendTestNotification(context)) {
                    done = true; prefs.edit().putBoolean("done", true).apply()
                }
            }) { Text("3. Send test notification") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsSheet(app: LaunchableApp, close: () -> Unit, navigate: (String) -> Unit) {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val settings by container.repository.settings.collectAsState(Settings())
    val all by container.repository.options.collectAsState(emptyList())
    val saved = all.find { it.packageName == app.packageName && it.profileId == settings.activeProfileId } ?: AppOptions(app.packageName, settings.activeProfileId)
    var delay by remember(saved) { mutableStateOf(saved.returnDelayMinutes?.toString().orEmpty()) }
    var expiry by remember(saved) { mutableStateOf(saved.expireAfterMinutes?.toString().orEmpty()) }
    var override by remember(saved) { mutableStateOf(saved.overrideExpiry) }
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(delay, { delay = it }, label = { Text("Return delay (minutes)") }, supportingText = { Text("Blank uses global timing.") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Checkbox(override, { override = it }); Text("Own auto-expire setting") }
            OutlinedTextField(expiry, { expiry = it }, label = { Text("Auto-expire (minutes)") }, supportingText = { Text("Blank means never expire.") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = (delay.isBlank() || (delay.toIntOrNull() ?: -1) >= 0) && (expiry.isBlank() || (expiry.toIntOrNull() ?: 0) > 0), onClick = { scope.launch {
                container.database.dao().saveOptions(saved.copy(returnDelayMinutes = delay.toIntOrNull(), overrideExpiry = override, expireAfterMinutes = expiry.toIntOrNull()))
                container.copies.recover(); close()
            } }) { Text("Save") }
            TextButton(onClick = { close(); navigate("Rules/${android.net.Uri.encode(app.packageName)}") }) { Text("App rules") }
            TextButton(onClick = { close(); navigate("Profiles/${android.net.Uri.encode(app.packageName)}") }) { Text("VIP senders") }
        }
    }
}

@Composable
fun HealthBanner(openSetup: () -> Unit) {
    val context = LocalContext.current
    var unhealthy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            unhealthy = com.husarp.holdfast.notifications.listenerUnhealthy(context)
            kotlinx.coroutines.delay(30_000)
        }
    }
    if (unhealthy) TextButton(onClick = openSetup) { Text("Holdfast isn't receiving notifications · Setup") }
}
