package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

@Composable
fun ProfilesScreen() {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val repo = container.repository
    val dao = container.database.dao()
    val profiles by repo.profiles.collectAsState(emptyList())
    val settings by repo.settings.collectAsState(Settings())
    val quiet by repo.quiet.collectAsState(emptyList())
    val options by repo.options.collectAsState(emptyList())
    val pinned by repo.pinnedApps.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var app by remember { mutableStateOf("") }
    var vip by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var digest by remember { mutableStateOf(false) }
    var times by remember { mutableStateOf("09:00,18:00") }
    var days by remember { mutableStateOf("1,2,3,4,5") }
    var start by remember { mutableStateOf("18:00") }
    var end by remember { mutableStateOf("08:00") }
    LaunchedEffect(app, settings.activeProfileId, options) {
        val saved = options.find { it.packageName == app && it.profileId == settings.activeProfileId }
        digest = saved?.digestOnly ?: false; times = saved?.digestTimes ?: "09:00,18:00"
        vip = saved?.vipSenders.orEmpty(); repeat = saved?.repeatMinutes?.toString().orEmpty()
    }
    LaunchedEffect(Unit) { repo.ensureProfiles() }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Profiles", style = MaterialTheme.typography.headlineMedium)
            profiles.forEach { profile ->
                TextButton(onClick = { scope.launch {
                    repo.saveSettings(settings.copy(activeProfileId = profile.id)); container.copies.recover()
                } }) { Text(profile.name + if (profile.id == settings.activeProfileId) " ✓" else "") }
            }
            OutlinedTextField(name, { name = it }, label = { Text("Profile name") })
            Row {
                Button(enabled = name.isNotBlank(), onClick = { scope.launch { dao.insertProfile(Profile(name = name.trim())); name = "" } }) { Text("Add") }
                TextButton(enabled = name.isNotBlank(), onClick = { scope.launch { dao.saveProfile(Profile(settings.activeProfileId, name.trim())); name = "" } }) { Text("Rename active") }
            }
            Text("App selection and mute rules belong to the active profile.")
            Text("Quiet hours", style = MaterialTheme.typography.titleLarge)
            pinned.filter { it.enabled }.forEach { row -> TextButton(onClick = { app = row.packageName }) { Text(row.packageName + if (app == row.packageName) " ✓" else "") } }
            Text("VIP and repeats for selected app")
            OutlinedTextField(vip, { vip = it }, label = { Text("VIP sender names, one per line (exact, ignores case)") })
            OutlinedTextField(repeat, { repeat = it }, label = { Text("Repeat minutes (blank disables)") })
            Row { Checkbox(digest, { digest = it }); Text("Digest only") }
            OutlinedTextField(times, { times = it }, label = { Text("Digest times HH:mm, comma separated") })
            Button(enabled = app.isNotBlank() && parseDigestTimes(times).isNotEmpty() && (repeat.isBlank() || (repeat.toIntOrNull() ?: 0) > 0), onClick = { scope.launch {
                dao.saveOptions(AppOptions(app, settings.activeProfileId, vip, repeat.toIntOrNull(), digest, times)); container.copies.recover()
            } }) { Text("Save app options") }
            OutlinedTextField(days, { days = it }, label = { Text("Days: Monday=1 … Sunday=7, comma separated") })
            OutlinedTextField(start, { start = it }, label = { Text("Start HH:mm") })
            OutlinedTextField(end, { end = it }, label = { Text("End HH:mm (same time means 24 hours)") })
            val parsedDays = days.split(",").mapNotNull { it.trim().toIntOrNull() }
            val valid = parsedDays.isNotEmpty() && parsedDays.size == days.split(",").size && parsedDays.all { it in 1..7 }
            Button(enabled = app.isNotBlank() && valid && parseMinute(start) != null && parseMinute(end) != null, onClick = {
                scope.launch { dao.addQuiet(QuietSchedule(profileId = settings.activeProfileId, packageName = app,
                    days = parsedDays.joinToString(","), startMinute = parseMinute(start)!!, endMinute = parseMinute(end)!!)); container.copies.recover() }
            }) { Text("Add quiet schedule") }
            quiet.filter { it.profileId == settings.activeProfileId }.forEach { row ->
                Text("${row.packageName}: days ${row.days}, ${formatMinute(row.startMinute)}–${formatMinute(row.endMinute)}")
                TextButton(onClick = { scope.launch { dao.removeQuiet(row.id); container.copies.recover() } }) { Text("Remove") }
            }
        }
    }
}

fun parseMinute(value: String): Int? = try {
    val time = java.time.LocalTime.parse(value.trim())
    time.hour * 60 + time.minute
} catch (_: java.time.format.DateTimeParseException) { null }
fun formatMinute(value: Int) = "%02d:%02d".format(value / 60, value % 60)
