package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.MuteRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock

@Composable
fun RulesScreen(initialApp: String = "") {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val repository = container.repository
    val rules by repository.rules.collectAsState(initial = emptyList())
    val channels by repository.channels.collectAsState(initial = emptyList())
    val pinned by repository.pinnedApps.collectAsState(initial = emptyList())
    val history by repository.history.collectAsState(initial = emptyList())
    val apps = (pinned.map { it.packageName } + channels.map { it.packageName } + history.map { it.packageName }).distinct().sorted()
    var message by remember { mutableStateOf("") }
    var app by remember(initialApp) { mutableStateOf(initialApp) }
    var kind by remember { mutableStateOf("Title") }
    var value by remember { mutableStateOf("") }
    var chooseApp by remember { mutableStateOf(false) }
    var chooseChannel by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Rules suppress Holdfast copies. Originals and captured history are kept.")
            Box {
                TextButton(onClick = { chooseApp = true }) { Text(app.ifEmpty { "Choose app" }) }
                DropdownMenu(chooseApp, onDismissRequest = { chooseApp = false }) {
                    apps.forEach { name -> DropdownMenuItem(text = { Text(name) }, leadingIcon = { AppIcon(name) }, onClick = { app = name; value = ""; chooseApp = false }) }
                }
            }
            if (apps.isEmpty()) Text("Select an app in Apps first.")
            Row { listOf("Channel", "Title", "Text").forEach { option ->
                TextButton(onClick = { kind = option; value = "" }) { Text(if (kind == option) "$option ✓" else option) }
            } }
            if (kind == "Channel") {
                Box {
                    TextButton(onClick = { chooseChannel = true }) { Text(value.ifEmpty { "Choose captured channel" }) }
                    DropdownMenu(chooseChannel, onDismissRequest = { chooseChannel = false }) {
                        channels.filter { it.packageName == app }.forEach { channel ->
                            DropdownMenuItem(text = { Text(channel.channelId) }, onClick = { value = channel.channelId; chooseChannel = false })
                        }
                    }
                }
                if (channels.none { it.packageName == app }) Text("No channels captured for this app yet.")
            } else OutlinedTextField(value, onValueChange = { value = it }, label = { Text("$kind contains (ignores case)") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = app.isNotEmpty() && value.isNotBlank(), onClick = {
                val rule = MuteRule(packageName = app, kind = kind, value = value)
                value = ""
                scope.launch {
                    repository.notificationMutex.withLock { container.database.dao().addRule(rule.copy(profileId = repository.activeProfile())) }
                    container.copies.recover(false)
                    message = "Rule added"
                }
            }) { Text("Add rule") }
            ActionMessage(message)
            if (rules.isEmpty()) EmptyState("Add a rule to silence matching copies.", screenIcon("Rules"))
            }
        }
        items(rules, key = { it.id }) { rule ->
            Column(Modifier.padding(vertical = 8.dp)) {
                ListItem(headlineContent = { Text(rule.packageName) }, leadingContent = { AppIcon(rule.packageName) })
                Text("${rule.kind}: ${rule.value}")
                TextButton(onClick = { scope.launch { container.database.dao().removeRule(rule.id); message = "Rule removed" } }) { Text("Remove") }
            }
        }
    }
}
