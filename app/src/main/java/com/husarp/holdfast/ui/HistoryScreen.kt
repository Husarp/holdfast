package com.husarp.holdfast.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
    val opens by (LocalContext.current.applicationContext as HoldfastApplication).container.database.dao().observeOpens().collectAsState(emptyList())
    StatsContent(history, opens)
}

@Composable
fun StatsContent(history: List<NotificationEvent>, opens: List<com.husarp.holdfast.data.CopyOpen> = emptyList()) {
    var days by remember { mutableIntStateOf(7) }
    val stats = com.husarp.holdfast.data.activityStats(history, opens, days)
    val counts = stats.events.groupBy { it.packageName to it.userSerial }.values.sortedByDescending { it.size }
    val maximum = counts.maxOfOrNull { it.size } ?: 1
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { listOf(7, 30).forEach { value ->
                FilterChip(selected = days == value, onClick = { days = value }, label = { Text("$value days") })
            } }
        }
        item {
            SettingsSection("Period summary") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Captured" to stats.events.size, "Held as copies" to stats.held, "Opened" to stats.opened).forEach { (label, count) ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) { Text("$count", style = MaterialTheme.typography.headlineSmall); Text(label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
                Text("Opened counts taps on copies during this period.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (counts.isEmpty()) item { EmptyState("Capture notifications to see your activity.", screenIcon("Stats")) }
        else {
            item { SettingsSection("Captured per day") { DailyChart(stats.daily) } }
            item { Text("By app", style = MaterialTheme.typography.titleMedium) }
        }
        items(counts) { rows ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppIcon(rows.first().packageName, rows.first().userSerial)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(rows.first().appLabel, style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f).height(6.dp).background(MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.shapes.large)) {
                            Box(Modifier.fillMaxWidth(appBarFraction(rows.size, maximum)).height(6.dp)
                                .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large))
                        }
                        Text("${rows.size}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyChart(daily: List<Pair<java.time.LocalDate, Int>>) {
    val maximum = daily.maxOf { it.second }.coerceAtLeast(1)
    Text("Maximum: ${daily.maxOf { it.second }}", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().height(120.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = androidx.compose.ui.Alignment.Bottom) {
        daily.forEach { (date, count) ->
            Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    modifier = Modifier.semantics { contentDescription = "$date: $count captured" }) {
                    if (count == maximum) Text("$count", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Box(Modifier.fillMaxWidth().height((count * 96f / maximum).dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
    HorizontalDivider()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(daily.first().first, daily[daily.size / 2].first, daily.last().first).forEach { Text(it.format(java.time.format.DateTimeFormatter.ofPattern("MMM d")), style = MaterialTheme.typography.labelSmall) }
    }
}

internal fun appBarFraction(count: Int, maximum: Int): Float =
    if (maximum <= 0) 0f else (count.toFloat() / maximum).coerceIn(0f, 1f)
