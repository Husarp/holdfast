package com.husarp.holdfast.update

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.util.Locale

private fun mb(bytes: Long) = String.format(Locale.US, "%.1f", bytes.coerceAtLeast(0) / 1_048_576.0)

@Composable
fun UpdatesScreen() {
    val context = LocalContext.current
    val state by Updates.state.collectAsState()
    var enabled by remember { mutableStateOf(Updates.enabled(context)) }
    UpdatesContent(state, Updates.current(context), enabled,
        { enabled = it; Updates.setEnabled(context, it) }, { Updates.checkNow(context) }, { Updates.update(context) },
        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(state.pageUrl))) })
}

@Composable
fun UpdatesContent(state: Updates.State, current: String, enabled: Boolean = true,
    toggle: (Boolean) -> Unit = {}, check: () -> Unit = {}, update: () -> Unit = {}, github: () -> Unit = {}) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ListItem(headlineContent = { Text("Check for updates") }, supportingContent = { Text("Only updates use the internet.") },
            trailingContent = { Switch(enabled, toggle) }) }
        item { Text("Current version $current"); TextButton(onClick = check, enabled = !state.checking) { Text(if (state.checking) "Checking…" else "CHECK NOW") } }
        state.message?.let { item { Text(it) } }
        if (Updates.available(state, current)) item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Update ${state.latest!!.version} · ${if (state.latest.apkSize > 0) mb(state.latest.apkSize) + " MB" else "Size unavailable"}")
                    Button(onClick = update, enabled = state.phase !is Updates.Phase.Downloading && state.phase != Updates.Phase.NeedsPermission) { Text("GET UPDATE") }
                }
            }
        }
        item {
            when (val phase = state.phase) {
                is Updates.Phase.Downloading -> {
                    LinearProgressIndicator(progress = { phase.percent / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("${phase.percent}% · ${mb(phase.done)} / ${mb(phase.total)} MB · ${mb(phase.bytesPerSecond)} MB/s")
                }
                is Updates.Phase.Failed -> { Text(phase.why); Button(onClick = update) { Text("TRY AGAIN") } }
                Updates.Phase.NeedsPermission -> Text("Allow Install unknown apps for Holdfast, then return here to continue.")
                Updates.Phase.Ready -> Text("The update is ready to install.")
                Updates.Phase.Installing -> Text("Confirm the update in Android's installer.")
                else -> Unit
            }
            TextButton(onClick = github) { Text("GITHUB") }
        }
    }
}

@Composable
fun UpdateCards(navigate: (String) -> Unit) {
    val context = LocalContext.current
    val state by Updates.state.collectAsState()
    var pending by remember { mutableStateOf(WhatsNew.cardPending(context)) }
    if (Updates.bannerVisible(state, Updates.current(context))) Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ListItem(headlineContent = { Text("Holdfast ${state.latest!!.version} available") }, trailingContent = {
            Row {
                TextButton(onClick = { navigate("Updates"); Updates.update(context) }) { Text("UPDATE") }
                TextButton(onClick = { Updates.dismissBanner() }) { Text("Close") }
            }
        })
    }
    if (pending) Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Holdfast ${Updates.current(context)} installed", Modifier.padding(16.dp))
        Row {
            TextButton(onClick = { WhatsNew.markSeen(context); pending = false; navigate("What’s new") }) { Text("WHAT'S NEW") }
            TextButton(onClick = { WhatsNew.markSeen(context); pending = false }) { Text("Close") }
        }
    }
}

@Composable
fun WhatsNewScreen() {
    val context = LocalContext.current
    val versions = remember { WhatsNew.upTo(WhatsNew.load(context), Updates.current(context)) }
    LaunchedEffect(Unit) { WhatsNew.markSeen(context) }
    WhatsNewContent(versions)
}

@Composable
fun WhatsNewContent(versions: List<WhatsNew.Version>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        versions.forEach { version -> item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(version.version, style = MaterialTheme.typography.titleLarge)
                (version.items + version.fixes).forEach { Text("• ${it.text}") }
            } }
        } }
    }
}
