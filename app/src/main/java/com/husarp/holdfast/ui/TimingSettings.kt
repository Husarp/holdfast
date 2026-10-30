package com.husarp.holdfast.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.Settings
import kotlinx.coroutines.launch

@Composable
fun TimingSettings() {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val settings by container.repository.settings.collectAsState(initial = null)
    val saved = settings ?: return
    var delay by rememberSaveable(saved.returnDelayMinutes) { mutableStateOf(saved.returnDelayMinutes.toString()) }
    var expiry by rememberSaveable(saved.expireAfterMinutes) { mutableStateOf(saved.expireAfterMinutes?.toString().orEmpty()) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Text("Timing", style = MaterialTheme.typography.headlineMedium)
    OutlinedTextField(delay, { delay = it; message = "" }, label = { Text("Return delay (minutes, 0 = immediate)") }, singleLine = true)
    OutlinedTextField(expiry, { expiry = it; message = "" }, label = { Text("Auto-expire (minutes, empty = off)") }, singleLine = true)
    Text("Auto-expire starts when a copy is first created. Changes apply to new copies and future swipes.")
    Button(onClick = {
        val minutes = delay.toIntOrNull()
        val expire = expiry.toIntOrNull()
        if (minutes == null || minutes < 0 || (expiry.isNotBlank() && (expire == null || expire <= 0))) {
            message = "Enter whole minutes: delay 0 or more; expiry greater than 0, or empty for off."
        } else scope.launch {
            container.repository.saveSettings(saved.copy(returnDelayMinutes = minutes, expireAfterMinutes = expire))
            message = "Saved"
        }
    }) { Text("Save timing") }
    if (message.isNotEmpty()) Text(message)
}
