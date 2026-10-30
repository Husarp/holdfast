package com.husarp.holdfast.ui

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.NotificationState
import kotlinx.coroutines.launch

@Composable
fun PinnedScreen() {
    val container = (LocalContext.current.applicationContext as HoldfastApplication).container
    val rows by container.repository.notifications.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item { Text("Pinned", style = MaterialTheme.typography.headlineMedium) }
        val copies = rows.filter { it.state == NotificationState.COPY_VISIBLE || it.state == NotificationState.COPY_WAITING }
        if (copies.isEmpty()) item { Text("No active copies.") }
        items(copies, key = { it.id }) { row ->
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(row.appLabel, style = MaterialTheme.typography.titleMedium)
                Text(row.title)
                Text(row.text)
                if (row.state == NotificationState.COPY_WAITING) Text("Waiting to return")
                Button(onClick = { scope.launch { container.copies.complete(row.id)?.let { container.contentIntents.remove(it.sourceKey) } } }) { Text("Dismiss") }
            }
        }
    }
}
