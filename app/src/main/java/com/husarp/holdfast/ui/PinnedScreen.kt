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
fun PinnedScreen(openSetup: () -> Unit = {}) {
    val context = LocalContext.current
    val container = (context.applicationContext as HoldfastApplication).container
    val rows by container.repository.notifications.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var ready by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        ready = setupStatus(context).let { it.listener && it.posting && it.exact && it.battery && it.copies }
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    Box {
        PinnedContent(rows.filter { it.state == NotificationState.COPY_VISIBLE || it.state == NotificationState.COPY_WAITING },
            ready, openSetup) { row ->
            scope.launch {
                container.copies.complete(row.id)?.let { container.contentIntents.remove(it.sourceKey) }
                snackbar.showSnackbar("Notification dismissed")
            }
        }
        SnackbarHost(snackbar, Modifier.align(androidx.compose.ui.Alignment.BottomCenter))
    }
}

@Composable
fun PinnedContent(rows: List<CapturedNotification>, setupReady: Boolean, openSetup: () -> Unit = {}, dismiss: (CapturedNotification) -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!setupReady) item {
            Card {
                ListItem(headlineContent = { Text("Finish setting up Holdfast") },
                    supportingContent = { Text("Enable notification access, posting, alarms and battery access.") },
                    leadingContent = { Icon(screenIcon("Setup"), null) })
                TextButton(onClick = openSetup, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Open checklist") }
            }
        }
        if (rows.isEmpty()) item { EmptyState("Dismiss a notification from a selected app to keep it here.") }
        items(rows, key = { it.id }) { row ->
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
                        if (row.state == NotificationState.COPY_WAITING) "Waiting to return" else "Pinned copy")
                    TextButton(onClick = { dismiss(row) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Dismiss") }
                }
            }
        }
    }
}

@Composable
fun NotificationRow(pkg: String, serial: Long, label: String, title: String, text: String, detail: String) {
    ListItem(leadingContent = { AppIcon(pkg, serial) },
        overlineContent = { Text(label) },
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        })
}
