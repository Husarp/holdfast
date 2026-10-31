package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen(initialApp: String = "") {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val repo = container.repository
    val dao = container.database.dao()
    val profiles by repo.profiles.collectAsState(emptyList())
    val settings by repo.settings.collectAsState(Settings())
    val quiet by repo.quiet.collectAsState(emptyList())
    val options by repo.options.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    val app = initialApp
    var vip by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var digest by remember { mutableStateOf(false) }
    var times by remember { mutableStateOf("09:00,18:00") }
    LaunchedEffect(app, settings.activeProfileId, options) {
        val saved = options.find { it.packageName == app && it.profileId == settings.activeProfileId }
        digest = saved?.digestOnly ?: false; times = saved?.digestTimes ?: "09:00,18:00"
        vip = saved?.vipSenders.orEmpty(); repeat = saved?.repeatMinutes?.toString().orEmpty()
    }
    LaunchedEffect(Unit) { repo.ensureProfiles() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (initialApp.isEmpty()) {
            val pinned by repo.pinnedApps.collectAsState(emptyList())
            val places by dao.observePlaces().collectAsState(emptyList())
            profiles.forEachIndexed { index, profile ->
                Card(onClick = { scope.launch { repo.saveSettings(settings.copy(activeProfileId = profile.id)); container.copies.recover() } }) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("●", color = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.secondary)[index % 3])
                            Text(profile.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            if (profile.id == settings.activeProfileId) Text("Active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { editing = profile; name = profile.name; naming = true }) { Icon(Icons.Outlined.Edit, "Rename ${profile.name}") }
                        }
                        Text("${pinned.count { it.profileId == profile.id && it.enabled }} apps · Quiet hours: ${if (quiet.any { it.profileId == profile.id }) "yes" else "no"} · ${places.count { it.profileId == profile.id }} places")
                        Text(if (profile.id == settings.activeProfileId) "Selected for apps and quiet hours" else "Tap to activate", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedButton(onClick = { editing = null; name = ""; naming = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Add profile")
            }
            Text("App selection and mute rules belong to the active profile.")
            }
            Text("Quiet hours", style = MaterialTheme.typography.titleLarge)
            if (initialApp.isEmpty()) Text("Global quiet hours apply to all apps.")
            ActionMessage(message)
            if (initialApp.isNotEmpty()) {
            SettingsSection("VIP and repeats") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text("Priority senders"); HelpButton("VIP", "VIP sender names match exactly, ignoring case, and bypass quiet hours and expiry.") }
            OutlinedTextField(vip, { vip = it }, label = { Text("VIP sender names, one per line (exact, ignores case)") })
            MinuteChoices("Repeat", repeat, { repeat = it }, emptyLabel = "Off")
            }
            SettingsSection("Digest") {
            OptionRow("Digest only", "Collect notifications and show a summary at the listed local times.") { Checkbox(digest, { digest = it }) }
            SettingsSection("Digest delivery times") { WeeklyDigestEditor(times) { times = it } }
            Button(enabled = app.isNotBlank() && (!digest || digestEntries(times).isNotEmpty()) && (repeat.isBlank() || (repeat.toIntOrNull() ?: 0) > 0), onClick = { scope.launch {
                dao.saveOptions((options.find { it.packageName == app && it.profileId == settings.activeProfileId } ?: AppOptions(app, settings.activeProfileId)).copy(vipSenders = vip, repeatMinutes = repeat.toIntOrNull(), digestOnly = digest, digestTimes = times)); container.copies.recover(); message = "App options saved"
            } }) { Text("Save app options") }
            }
            }
            WeeklyScheduleEditor(quiet.filter { it.profileId == settings.activeProfileId && it.packageName == app }) { day, rows ->
                scope.launch {
                    dao.replaceQuietDay(settings.activeProfileId, app, day, rows)
                    container.copies.recover()
                }
            }

            }
        }
    }
    if (naming) AlertDialog(onDismissRequest = { naming = false },
        title = { Text(if (editing == null) "Add profile" else "Rename profile") },
        text = { OutlinedTextField(name, { name = it }, label = { Text("Profile name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            val profile = editing
            val newName = name.trim()
            scope.launch {
                if (profile == null) dao.insertProfile(Profile(name = newName))
                else dao.saveProfile(profile.copy(name = newName))
            }
            naming = false
        }) { Text(if (editing == null) "Add" else "Save") } },
        dismissButton = { TextButton(onClick = { naming = false }) { Text("Cancel") } })

}

fun parseMinute(value: String): Int? = try {
    val time = java.time.LocalTime.parse(value.trim())
    time.hour * 60 + time.minute
} catch (_: java.time.format.DateTimeParseException) { null }
fun formatMinute(value: Int) = "%02d:%02d".format(value / 60, value % 60)
