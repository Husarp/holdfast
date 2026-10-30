package com.husarp.holdfast.ui

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.platform.LocalContext
import com.husarp.holdfast.HoldfastApplication
import kotlinx.coroutines.launch

@Composable
fun SpamHints() {
    val context = LocalContext.current
    val c = (context.applicationContext as HoldfastApplication).container
    val hints by c.repository.spam.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("spam-check", 0)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last", 0) >= 86_400_000) {
            c.repository.checkSpam(now)
            prefs.edit().putLong("last", now).apply()
        }
    }
    hints.filter { !it.answered }.forEach { hint ->
        ListItem(headlineContent = { Text(hint.appLabel) }, leadingContent = { AppIcon(hint.packageName) },
            supportingContent = { Text("${hint.count} notifications you didn't open. Mute?") })
        TextButton(onClick = { scope.launch { c.repository.answerSpam(hint, true); c.copies.recover() } }) { Text("Mute") }
        TextButton(onClick = { scope.launch { c.repository.answerSpam(hint, false) } }) { Text("Keep") }
    }
}
