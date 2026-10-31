package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

@Composable
fun AppPolicyEditor(packageName: String) {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val repo = container.repository
    val settings by repo.settings.collectAsState(Settings())
    val options by repo.options.collectAsState(emptyList())
    val types by repo.types.collectAsState(emptyList())
    val channels by repo.channels.collectAsState(emptyList())
    val saved = options.find { it.packageName == packageName && it.profileId == settings.activeProfileId } ?: AppOptions(packageName, settings.activeProfileId)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<TypePolicy?>(null) }
    var kind by remember { mutableStateOf("Title") }
    var value by remember { mutableStateOf("") }
    val type = editing
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsSection("Copy behaviour") {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(type?.let { "${it.kind}: ${it.value}" } ?: "App default", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
        HelpButton("Mode", "Type rules override the app mode. Text matches take priority over channels. Take over cancels the original and posts a silent copy.") }
        PolicyControls(type?.mode ?: saved.mode ?: CopyMode.SWIPE, type?.openAndFinish ?: saved.openAndFinish, type?.buttons ?: saved.buttons) { mode, finish, buttons ->
            scope.launch {
                if (type == null) container.database.dao().saveOptions(saved.copy(mode = mode, openAndFinish = finish, buttons = buttons))
                else { val updated = type.copy(mode = mode, openAndFinish = finish, buttons = buttons); container.database.dao().saveType(updated); editing = null }
                container.copies.recover()
            }
        }
        if (type != null) TextButton(onClick = { editing = null }) { Text("Back to app default") }
        }
        SettingsSection("Notification types") {
        channels.filter { it.packageName == packageName }.forEach { channel ->
            TextButton(onClick = { editing = types.find { it.packageName == packageName && it.profileId == settings.activeProfileId && it.kind == "Channel" && it.value == channel.channelId }
                ?: TypePolicy(packageName = packageName, profileId = settings.activeProfileId, value = channel.channelId) }) { Text(channel.name) }
        }
        if (channels.none { it.packageName == packageName }) Text("Types appear after this app sends a notification.")
        Row { listOf("Title", "Text").forEach { choice -> FilterChip(kind == choice, { kind = choice }, label = { Text(choice) }) } }
        OutlinedTextField(value, { value = it }, label = { Text("Contains (ignores case)") })
        Button(enabled = value.isNotBlank(), onClick = { editing = TypePolicy(packageName = packageName, profileId = settings.activeProfileId, kind = kind, value = value.trim()); value = "" }) { Text("Configure text type") }
        types.filter { it.packageName == packageName && it.profileId == settings.activeProfileId }.forEach { rule ->
            TextButton(onClick = { editing = rule }) { Text("${rule.kind}: ${rule.value} · ${rule.mode ?: "App default"}") }
            TextButton(onClick = { scope.launch { container.database.dao().removeType(rule.id); container.copies.recover() } }) { Text("Remove override") }
        }
        }
    }
}

@Composable
fun PolicyControls(savedMode: String, savedFinish: Boolean?, savedButtons: String, save: (String, Boolean?, String) -> Unit) {
    var mode by remember(savedMode) { mutableStateOf(savedMode) }
    var finish by remember(savedFinish, savedMode) { mutableStateOf(savedFinish ?: (savedMode !in listOf(CopyMode.TAKE_OVER, CopyMode.KEEP))) }
    var buttons by remember(savedButtons) { mutableStateOf(savedButtons.split('|').filter { it.isNotBlank() }) }
    CopyMode.choices.forEach { choice -> OptionRow(choice) { RadioButton(mode == choice, { mode = choice; finish = choice !in listOf(CopyMode.TAKE_OVER, CopyMode.KEEP) }) } }
    OptionRow(if (finish) "Tap copy: Open and finish" else "Tap copy: Open the app and keep it") { Switch(finish, { finish = it }) }
    ButtonEditor(buttons) { buttons = it }
    Button(enabled = buttons.all(::validButton), onClick = { save(mode, finish, buttons.joinToString("|")) }) { Text("Save mode & buttons") }
}

@Composable
fun ButtonEditor(buttons: List<String>, change: (List<String>) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text("Copy buttons", style = MaterialTheme.typography.titleMedium)
        HelpButton("Copy buttons", "Choose up to three visible buttons.\nUnavailable actions use the next fallback.\nReply and original actions need a live notification.")
    }
    buttons.forEachIndexed { index, button ->
        ListItem(headlineContent = { Text(buttonLabel(button)) }, trailingContent = {
            Row {
                IconButton(enabled = index > 0, onClick = { change(buttons.toMutableList().apply { set(index, buttons[index - 1]); set(index - 1, button) }) }) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                IconButton(enabled = index < buttons.lastIndex, onClick = { change(buttons.toMutableList().apply { set(index, buttons[index + 1]); set(index + 1, button) }) }) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                IconButton(onClick = { change(buttons.filterIndexed { i, _ -> i != index }) }) { Icon(Icons.Default.Close, "Remove button") }
            }
        })
        val update: (String) -> Unit = { value -> change(buttons.toMutableList().apply { set(index, button.substringBefore(':') + ":" + value) }) }
        if (button.startsWith("snooze:")) MinuteChoices("Snooze", button.substringAfter(':'), update)
        if (button.startsWith("until:") || button.startsWith("tomorrow:")) TimeChoice("Next occurrence", button.substringAfter(':'), update)
    }
    var menu by remember { mutableStateOf(false) }
    Box { Button(onClick = { menu = true }) { Icon(Icons.Default.Add, null); Text("Add button") }; DropdownMenu(menu, { menu = false }) {
        buttonChoices.forEach { button -> DropdownMenuItem(text = { Text(buttonLabel(button)) }, leadingIcon = { Icon(when (button.substringBefore(':')) { "snooze", "until", "tomorrow" -> Icons.Default.Schedule; "dismiss" -> Icons.Default.Close; "reply" -> Icons.AutoMirrored.Filled.Reply; else -> Icons.AutoMirrored.Filled.OpenInNew }, null) }, onClick = { change(buttons + button); menu = false }) }
    } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Live preview · Example notification")
            Text("Original title and message", style = MaterialTheme.typography.bodyMedium)
            Row { fillButtonSlots(buttons.joinToString("|"), true, 1).forEach { Text(buttonLabel(it), Modifier.weight(1f).padding(4.dp), style = MaterialTheme.typography.labelSmall) } }
        }
    }
}
