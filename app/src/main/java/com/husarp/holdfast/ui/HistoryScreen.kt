package com.husarp.holdfast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.NotificationEvent
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@Composable
fun HistoryScreen() {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val history by container.repository.history.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    HistoryContent(history) { row -> scope.launch { container.copies.bringBack(row) } }
}

@Composable
fun HistoryContent(history: List<NotificationEvent>, bringBack: (NotificationEvent) -> Unit = {}) {
    var search by remember { mutableStateOf("") }
    var app by remember { mutableStateOf<Pair<String, Long>?>(null) }
    var expanded by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { OutlinedTextField(search, { search = it }, label = { Text("Search history") }, modifier = Modifier.fillMaxWidth()) }
        item {
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(history.find { it.packageName to it.userSerial == app }?.appLabel ?: "All apps") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("All apps") }, onClick = { app = null; expanded = false })
                    history.distinctBy { it.packageName to it.userSerial }.forEach { row ->
                        DropdownMenuItem(text = { Text(row.appLabel) }, leadingIcon = { AppIcon(row.packageName, row.userSerial) },
                            onClick = { app = row.packageName to row.userSerial; expanded = false })
                    }
                }
            }
        }
        if (history.isEmpty()) item { EmptyState("Notifications from your selected apps will appear here.", screenIcon("History")) }
        items(history.filter { (app == null || it.packageName to it.userSerial == app) && "${it.appLabel} ${it.title} ${it.text}".contains(search, true) }, key = { it.id }) { row ->
            Card {
                NotificationRow(row.packageName, row.userSerial, row.appLabel, row.title, row.text,
                    DateFormat.getDateTimeInstance().format(Date(row.capturedAt)))
                TextButton(onClick = { bringBack(row) }, modifier = Modifier.align(androidx.compose.ui.Alignment.End)) { Text("Bring back") }
            }
        }
    }
}

@Composable
fun StatsScreen() {
    val repository = (LocalContext.current.applicationContext as HoldfastApplication).container.repository
    val history by repository.history.collectAsState(initial = emptyList())
    StatsContent(history)
}

@Composable
fun StatsContent(history: List<NotificationEvent>) {
    var days by remember { mutableIntStateOf(7) }
    val cutoff = System.currentTimeMillis() - days * 86_400_000L
    val counts = history.filter { it.capturedAt >= cutoff }.groupBy { it.packageName to it.userSerial }
        .values.sortedByDescending { it.size }
    val maximum = counts.maxOfOrNull { it.size } ?: 1
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { listOf(7, 30).forEach { value ->
                FilterChip(selected = days == value, onClick = { days = value }, label = { Text("$value days") })
            } }
        }
        if (counts.isEmpty()) item { EmptyState("Capture notifications to see your activity.", screenIcon("Stats")) }
        items(counts) { rows ->
            Card {
                ListItem(headlineContent = { Text(rows.first().appLabel) },
                    leadingContent = { AppIcon(rows.first().packageName, rows.first().userSerial) },
                    trailingContent = { Text(rows.size.toString(), style = MaterialTheme.typography.titleLarge) })
                Box(Modifier.padding(16.dp).fillMaxWidth()) {
                    Box(Modifier.fillMaxWidth(rows.size.toFloat() / maximum).height(16.dp)
                        .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large))
                }
            }
        }
    }
}
