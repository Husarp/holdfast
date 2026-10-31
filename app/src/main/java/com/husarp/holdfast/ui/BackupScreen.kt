package com.husarp.holdfast.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val c = (context.applicationContext as HoldfastApplication).container
    val store = remember { BackupStore(c.database) }
    val scope = rememberCoroutineScope()
    var history by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<Backup?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            message = try {
                withContext(Dispatchers.IO) {
                    val text = store.export(history)
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(text) }
                        ?: error("Cannot write file")
                }; "Exported"
            } catch (e: Exception) { "Export failed: ${e.message}" }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                pending = withContext(Dispatchers.IO) {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { reader ->
                        val chars = CharArray(5_000_001)
                        var length = 0
                        while (length < chars.size) {
                            val n = reader.read(chars, length, chars.size - length)
                            if (n < 0) break
                            length += n
                        }
                        require(length <= 5_000_000) { "File too large (maximum 5 MB)" }
                        String(chars, 0, length)
                    } ?: error("Cannot read file")
                    BackupFormat.decode(text)
                }
            } catch (e: Exception) { message = e.message ?: "Invalid backup" }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsSection("Export a local backup") {
        Icon(screenIcon("Backup"), null)
        OptionRow("Include history") { Checkbox(history, { history = it }) }
        Text("Every export includes app selections, rules and VIP names. Optional history includes private notification titles and text. Anyone with the file can read them.")
        Button(onClick = { scope.launch { c.repository.ensureProfiles(); export.launch("holdfast.json") } }) { Text("Export JSON") }
        }
        SettingsSection("Restore a backup") {
        Button(onClick = { import.launch(arrayOf("application/json", "text/plain")) }) { Text("Import JSON") }
        }
        ActionMessage(message)
    }
    pending?.let { backup ->
        AlertDialog(onDismissRequest = { pending = null }, title = { Text("Replace current rules?") },
            text = { Text("This replaces profiles, app selection, rules and settings. Included history also replaces history.") },
            confirmButton = { TextButton(onClick = { scope.launch {
                try { withContext(Dispatchers.IO) { c.repository.notificationMutex.withLock { store.replace(backup) } }; c.copies.recover(); message = "Imported" }
                catch (e: Exception) { message = "Import failed: ${e.message}" }
                pending = null
            } }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } })
    }
}
