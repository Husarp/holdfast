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
fun AppPage(app: LaunchableApp, close: () -> Unit) {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val settings by container.repository.settings.collectAsState(Settings())
    val all by container.repository.options.collectAsState(emptyList())
    val saved = all.find { it.packageName == app.packageName && it.profileId == settings.activeProfileId }
        ?: AppOptions(app.packageName, settings.activeProfileId)
    var delay by remember(saved) { mutableStateOf(saved.returnDelayMinutes?.toString().orEmpty()) }
    var expiry by remember(saved) { mutableStateOf(saved.expireAfterMinutes?.toString().orEmpty()) }
    var override by remember(saved) { mutableStateOf(saved.overrideExpiry) }
    var section by remember { mutableStateOf("Mode & buttons") }
    val scope = rememberCoroutineScope()
    androidx.compose.ui.window.Dialog(onDismissRequest = close,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(app.label, Modifier.weight(1f).padding(16.dp), style = MaterialTheme.typography.titleLarge)
                    HelpButton("App page")
                    TextButton(onClick = close) { Text("Close") }
                }
                val pinned by container.repository.pinnedApps.collectAsState(emptyList())
                OptionRow("Hold notifications") {
                    Switch(pinned.any { it.packageName == app.packageName && it.userSerial == app.userSerial && it.enabled }, { enabled ->
                        scope.launch { container.repository.setPinned(app.packageName, enabled, app.userSerial); container.copies.recover() }
                    })
                }
                val sections = listOf("Mode & buttons", "Timing", "Types & rules", "VIP & schedules")
                androidx.compose.foundation.lazy.LazyRow {
                    items(sections.size) { index ->
                        val title = sections[index]
                        FilterChip(section == title, { section = title }, label = { Text(title) }, modifier = Modifier.padding(4.dp))
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (section) {
                        "Types & rules" -> RulesScreen(app.packageName)
                        "VIP & schedules" -> ProfilesScreen(app.packageName)
                        "Mode & buttons" -> AppPolicyEditor(app.packageName)
                        else -> Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SettingsSection("App timing") {
                            MinuteChoices("Return delay", delay, { delay = it }, emptyLabel = "Global", allowZero = true)
                            OptionRow("Own auto-expire setting", "Override global expiry; Off keeps copies until you finish them.") { Checkbox(override, { override = it }) }
                            MinuteChoices("Auto-expire", expiry, { expiry = it }, emptyLabel = "Off")
                            Button(enabled = (delay.isBlank() || (delay.toIntOrNull() ?: -1) >= 0) && (expiry.isBlank() || (expiry.toIntOrNull() ?: 0) > 0), onClick = {
                                scope.launch {
                                    container.database.dao().saveOptions(saved.copy(returnDelayMinutes = delay.toIntOrNull(), overrideExpiry = override, expireAfterMinutes = expiry.toIntOrNull()))
                                    container.copies.recover()
                                }
                            }) { Text("Save timing") }
                            }
                        }
                    }
                }
            }
        }
    }
}
