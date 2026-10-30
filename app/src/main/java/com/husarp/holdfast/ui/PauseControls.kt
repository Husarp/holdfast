package com.husarp.holdfast.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.LocalContext
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

@Composable
fun PauseControls(initiallyExpanded: Boolean = false) {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val settings by container.repository.settings.collectAsState(Settings())
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(initiallyExpanded) }
    Box {
        TextButton(onClick = { menu = true }) { Text("Pause") }
        DropdownMenu(menu, { menu = false }) {
            (listOf("1 hour", "Tomorrow at 08:00", "Until resumed", "Resume")).forEach { choice ->
                DropdownMenuItem(text = { Text(choice) }, onClick = {
                    menu = false
                    scope.launch {
                        container.repository.saveSettings(settings.copy(pausedUntil = if (choice == "Resume") null else pauseDeadline(choice, System.currentTimeMillis())))
                        container.copies.recover()
                    }
                })
            }
        }
    }
}
