package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen() {
    val repository = (LocalContext.current.applicationContext as HoldfastApplication).container.repository
    val history by repository.history.collectAsState(initial = emptyList())
    var app by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("History", style = MaterialTheme.typography.headlineMedium)
            Box {
                TextButton(onClick = { expanded = true }) { Text(app ?: "All apps") }
                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("All apps") }, onClick = { app = null; expanded = false })
                    history.distinctBy { it.packageName }.forEach { row ->
                        DropdownMenuItem(text = { Text("${row.appLabel} (${row.packageName})") },
                            onClick = { app = row.packageName; expanded = false })
                    }
                }
            }
            if (history.isEmpty()) Text("No captured notifications.")
        }
        items(history.filter { app == null || it.packageName == app }, key = { it.id }) { row ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(row.appLabel, style = MaterialTheme.typography.titleMedium)
                Text(row.title)
                Text(row.text)
                Text(DateFormat.getDateTimeInstance().format(Date(row.capturedAt)))
            }
        }
    }
}

@Composable
fun StatsScreen() {
    val repository = (LocalContext.current.applicationContext as HoldfastApplication).container.repository
    val history by repository.history.collectAsState(initial = emptyList())
    var days by remember { mutableIntStateOf(7) }
    val cutoff = System.currentTimeMillis() - days * 86_400_000L
    val counts = history.filter { it.capturedAt >= cutoff }.groupBy { it.packageName }
        .values.sortedByDescending { it.size }
    val maximum = counts.maxOfOrNull { it.size } ?: 1
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Statistics", style = MaterialTheme.typography.headlineMedium)
            Row { listOf(7, 30).forEach { value ->
                TextButton(onClick = { days = value }) { Text(if (days == value) "$value days ✓" else "$value days") }
            } }
            if (counts.isEmpty()) Text("No captures in this period.")
        }
        items(counts) { rows ->
            Text("${rows.first().appLabel}: ${rows.size}")
            LinearProgressIndicator(progress = { rows.size.toFloat() / maximum }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
        }
    }
}
