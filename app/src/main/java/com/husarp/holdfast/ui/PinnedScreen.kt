package com.husarp.holdfast.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.*
import kotlinx.coroutines.launch

@Composable
fun PinnedScreen(openSetup: () -> Unit = {}, navigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as HoldfastApplication).container
    val rows by container.repository.notifications.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        ready = setupStatus(context).let { it.listener && it.posting && it.exact && it.battery && it.copies }
        onPauseOrDispose { }
    }
    val settings by container.repository.settings.collectAsState(Settings())
    var confirm by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    Column {
        if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Dismiss all pinned notifications?") },
            confirmButton = { TextButton(onClick = { confirm = false; scope.launch { rows.forEach { container.copies.complete(it.id) } } }) { Text("Dismiss all") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } })
        com.husarp.holdfast.update.UpdateCards(navigate)
        FirstRunGuide(openSetup) { navigate("Apps") }
        HealthBanner(openSetup)
        val paused = settings.pausedUntil?.takeIf { it > System.currentTimeMillis() }
        if (paused != null) Text(if (paused == Long.MAX_VALUE) "Paused until resumed" else "Paused until ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(paused))}")
        TextButton(onClick = { confirm = true }, enabled = rows.any { it.state == NotificationState.COPY_VISIBLE || it.state == NotificationState.COPY_WAITING }) { Text("Dismiss all") }
        Box(Modifier.weight(1f)) {
        PinnedContent(rows.filter { it.state == NotificationState.COPY_VISIBLE || it.state == NotificationState.COPY_WAITING },
            ready, openSetup, dismiss = { row ->
            scope.launch {
                val snapshots = rows.filter { it.state == row.state && groupingKey(it) == groupingKey(row) && (row.state == NotificationState.COPY_VISIBLE || it.nextShowAt == row.nextShowAt) }
                if (container.copies.complete(row.id) != null) {
                    if (snackbar.showSnackbar("Notification dismissed", "Undo") == SnackbarResult.ActionPerformed) container.copies.undo(snapshots)
                    else snapshots.forEach { container.contentIntents.remove(it.sourceKey) }
                }
            }
        }, dismissApp = { row -> scope.launch {
            rows.filter { it.packageName == row.packageName && it.userSerial == row.userSerial }.forEach { container.copies.complete(it.id) }
        } })
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
        }
    }
}

@Composable
fun PinnedContent(rows: List<CapturedNotification>, setupReady: Boolean, openSetup: () -> Unit = {}, dismiss: (CapturedNotification) -> Unit = {}, dismissApp: (CapturedNotification) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!setupReady) item {
            Card {
                ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent), headlineContent = { Text("Finish setting up Holdfast") },
                    supportingContent = { Text("Enable notification access and reminders.") },
                    leadingContent = { Icon(screenIcon("Setup"), null) })
                TextButton(onClick = openSetup, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Open checklist") }
            }
        }
        if (rows.isEmpty()) item { EmptyState("Dismiss a notification from a selected app to keep it here.") }
        items(rows, key = { it.id }) { row ->
            key(row.actionVersion) {
            val state = rememberSwipeToDismissBoxState()
            LaunchedEffect(state.currentValue) {
                if (state.currentValue != SwipeToDismissBoxValue.Settled) dismiss(row)
            }
            SwipeToDismissBox(state, backgroundContent = {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                    Box(Modifier.padding(16.dp)) { Text("Dismiss") }
                }
            }) {
                Card(Modifier.fillMaxWidth()) {
                    NotificationRow(row.packageName, row.userSerial, row.appLabel, row.title, row.text,
                        (if (row.state == NotificationState.COPY_WAITING) "Waiting · " else "") + relativeAge(row.postedAt) + " · back ${row.returnCount}x")
                    var menu by remember { mutableStateOf(false) }
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.End) {
                        Box {
                            TextButton(onClick = { menu = true }) { Text("More") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("Dismiss all from this app") }, onClick = { menu = false; dismissApp(row) })
                            }
                        }
                        FilledTonalButton(onClick = { dismiss(row) }) { Text("Dismiss") }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun NotificationRow(pkg: String, serial: Long, label: String, title: String, text: String, detail: String) {
    ListItem(colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
        leadingContent = { AppIcon(pkg, serial) },
        overlineContent = { Text(label) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        })
}
