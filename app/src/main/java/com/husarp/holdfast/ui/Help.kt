package com.husarp.holdfast.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

fun helpText(screen: String): String = when (screen.substringBefore("/")) {
    "Pinned" -> "Notifications Holdfast is holding appear here.\nSwipe a card or tap Dismiss to finish it.\nWaiting copies return at their scheduled time."
    "Apps", "App page" -> "Choose apps whose notifications you want to hold.\nOpen an app to change its mode, buttons, timing and rules.\nPinned apps appear first; settings use the active profile."
    "Mode", "Take-over" -> "Take over replaces the original with a silent Holdfast copy.\nSwipe mode waits until you swipe the original away.\nType rules override the app mode."
    "Copy buttons", "Buttons" -> "Choose up to three buttons for your copies.\nReorder them in the button editor.\nUnavailable live actions use the next fallback."
    "When", "Profiles" -> "Choose a profile or set quiet hours.\nGlobal quiet hours affect every app; app schedules live on the App page.\nVIP senders bypass quiet hours."
    "History", "Stats" -> "History records captured notifications on this device.\nSwitch to Statistics to see counts.\nBring a past notification back from its history card."
    "Rules" -> "Rules suppress matching Holdfast copies.\nChoose a channel or a phrase in the title or text.\nOriginal notifications are unaffected."
    "Places" -> "Quiet copies near saved places.\nLocation access is needed; no location leaves this device."
    "Timing" -> "Set default return delay and auto-expiry.\nApp timing can override these defaults."
    "Setup" -> "Grant notification access and permission to post copies.\nExact alarms improve timing; battery settings improve reliability."
    "Backup" -> "Export or import your settings and history locally.\nBackups contain notification text; store them privately."
    "Intro" -> "Learn how Holdfast keeps notifications.\nYou can skip and reopen this introduction from About."
    "Pause" -> "Pause copies temporarily or until you resume.\nHeld notifications remain saved."
    "Updates" -> "Check for an update using your browser.\nInstall only an update you trust."
    "What’s new" -> "Read changes in each Holdfast version.\nThe newest version is listed first."
    else -> "Holdfast stores notifications only on this device.\nUse Setup to check access and Apps to choose what to hold."
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenHelp(screen: String, explanation: String? = null, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(screen.substringBefore("/"), style = MaterialTheme.typography.titleLarge)
            HelpDiagram(screen)
            (explanation ?: helpText(screen)).split(Regex("\\n|(?<=\\.) +")).take(3).forEach { Text("• $it") }
            TextButton(onClick = close) { Text("Got it") }
        }
    }
}

@Composable
fun HelpButton(screen: String, explanation: String? = null) {
    var show by remember { mutableStateOf(false) }
    TextButton(onClick = { show = true }) { Text("?") }
    if (show) {
        ScreenHelp(screen, explanation) { show = false }
    }
}

@Composable
fun IntroScreen(navigate: (String) -> Unit = {}, finish: () -> Unit = {}) {
    var page by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("${page + 1} / 4", style = MaterialTheme.typography.labelLarge)
        Text(listOf("Keep what matters", "Pick your apps", "Grant access", "Try it out")[page], style = MaterialTheme.typography.headlineMedium)
        Text(listOf("Holdfast keeps a local copy when a notification disappears. You decide when it is finished.", "Choose apps in Apps, then open an app to adjust its mode and buttons.", "Allow notification access and posting so Holdfast can capture and show copies.", "Send a test notification, swipe it away, and look for its copy in Pinned.")[page])
        when (page) {
            1 -> Button(onClick = { navigate("Apps") }) { Text("Pick apps") }
            2 -> Button(onClick = { navigate("Setup") }) { Text("Grant access") }
            3 -> Button(onClick = { com.husarp.holdfast.notifications.sendTestNotification(context) }) { Text("Send test notification") }
        }
        Row { if (page > 0) TextButton(onClick = { page-- }) { Text("Previous") }; Button(onClick = { if (page < 3) page++ else finish() }) { Text(if (page < 3) "Next" else "Finish") }; TextButton(onClick = finish) { Text("Skip") } }
    }
}

@Composable
fun WhenScreen(navigate: (String) -> Unit) {
    val container = (LocalContext.current.applicationContext as com.husarp.holdfast.HoldfastApplication).container
    val settings by container.repository.settings.collectAsState(com.husarp.holdfast.data.Settings())
    val profiles by container.repository.profiles.collectAsState(emptyList())
    val quiet by container.repository.quiet.collectAsState(emptyList())
    val places by container.database.dao().observePlaces().collectAsState(emptyList())
    val global = quiet.filter { it.profileId == settings.activeProfileId && it.packageName.isEmpty() }
    Column(Modifier.padding(16.dp)) {
        listOf(
            Triple("Profiles", "Active profile: ${profiles.find { it.id == settings.activeProfileId }?.name ?: "…"}", "Profiles"),
            Triple("Quiet hours", quietHoursSummary(global), "Profiles"),
            Triple("Places", "${places.count { it.profileId == settings.activeProfileId }} places", "Places")
        ).forEachIndexed { index, (title, state, route) ->
            ListItem(
                headlineContent = { Text(title) },
                supportingContent = { Text(state, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                leadingContent = { Icon(screenIcon(if (title == "Quiet hours") "When" else title), null) },
                trailingContent = { Icon(Icons.Outlined.ChevronRight, null) },
                modifier = Modifier.fillMaxWidth().clickable { navigate(route) })
            if (index < 2) HorizontalDivider()
        }
    }
}

@Composable
fun HistoryHub() {
    var stats by remember { mutableStateOf(false) }
    Column {
        @OptIn(ExperimentalMaterial3Api::class)
        PrimaryTabRow(selectedTabIndex = if (stats) 1 else 0) {
            Tab(!stats, onClick = { stats = false }, text = { Text("History") })
            Tab(stats, onClick = { stats = true }, text = { Text("Statistics") })
        }
        SpamHints()
        Box(Modifier.weight(1f)) { if (stats) StatsScreen() else HistoryScreen() }
    }
}

@Composable
private fun HelpDiagram(screen: String) {
    val colors = MaterialTheme.colorScheme
    val topic = screen.substringBefore("/")
    val description = when (topic) {
        "Pinned" -> "A notification swiped right returns as a held copy"
        "Apps", "App page" -> "An app with its pinning switch on and two notification modes"
        "Mode", "Take-over" -> "A fading original replaced by a pinned Holdfast copy"
        "When", "Profiles", "Quiet hours", "Timing", "Digest" -> "A 24-hour timeline with a shaded quiet range, moon and seven day dots"
        "Copy buttons", "Buttons" -> "A notification with three labelled action slots"
        "History", "Stats" -> "Saved notifications with a bring-back arrow"
        else -> "A setup checklist with completed ticks"
    }
    Column(Modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Box(Modifier.fillMaxWidth().height(140.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            // Use a fixed drawing coordinate space so pictures scale to the sheet width.
            val sx = size.width / 320f
            val sy = size.height / 140f
            fun point(x: Float, y: Float) = Offset(x * sx, y * sy)
            fun box(x: Float, y: Float, w: Float, h: Float, color: androidx.compose.ui.graphics.Color) {
                drawRoundRect(color, point(x, y), Size(w * sx, h * sy), CornerRadius(8 * sx, 8 * sy))
            }
            fun line(x: Float, y: Float, x2: Float, y2: Float, color: androidx.compose.ui.graphics.Color = colors.primary) {
                drawLine(color, point(x, y), point(x2, y2), 3.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            }
            fun arrow(x: Float, y: Float, end: Float) {
                line(x, y, end, y)
                val back = if (end > x) end - 7 else end + 7
                line(back, y - 6, end, y); line(back, y + 6, end, y)
            }
            fun card(x: Float, y: Float, w: Float = 116f, faded: Boolean = false) {
                val alpha = if (faded) .35f else 1f
                box(x, y, w, 48f, colors.secondaryContainer.copy(alpha = alpha))
                box(x + 9, y + 10, 22f, 22f, colors.primary.copy(alpha = alpha))
                box(x + 39, y + 12, w - 49, 5f, colors.onSecondaryContainer.copy(alpha = alpha))
                box(x + 39, y + 25, w - 58, 4f, colors.onSecondaryContainer.copy(alpha = .45f * alpha))
            }
            fun pin(x: Float, y: Float) {
                box(x - 7, y, 14f, 5f, colors.primary)
                line(x - 4, y + 5, x - 4, y + 14); line(x + 4, y + 5, x + 4, y + 14)
                line(x - 9, y + 15, x + 9, y + 15); line(x, y + 15, x, y + 25)
            }
            when (topic) {
                "Pinned" -> {
                    card(12f, 20f, faded = true); arrow(80f, 78f, 143f)
                    card(188f, 65f); arrow(260f, 39f, 201f)
                    line(260f, 39f, 260f, 52f)
                }
                "Apps", "App page" -> {
                    box(30f, 35f, 64f, 64f, colors.primaryContainer)
                    pin(62f, 52f)
                    box(125f, 43f, 62f, 30f, colors.primary)
                    drawCircle(colors.onPrimary, 11 * sy, point(171f, 58f))
                    card(211f, 25f, 95f); arrow(214f, 94f, 250f); pin(282f, 81f)
                }
                "Mode", "Take-over" -> {
                    card(8f, 25f, faded = true); arrow(133f, 68f, 176f)
                    card(192f, 65f); pin(252f, 21f)
                }
                "When", "Profiles", "Quiet hours", "Timing", "Digest" -> {
                    box(12f, 51f, 296f, 20f, colors.surfaceContainerHighest)
                    box(12f, 51f, 92f, 20f, colors.primaryContainer)
                    drawCircle(colors.primary, 13 * sy, point(57f, 27f))
                    drawCircle(colors.surface, 12 * sy, point(65f, 22f))
                    repeat(7) { day -> drawCircle(if (day < 5) colors.primary else colors.outlineVariant, 6 * sy, point(52f + day * 36, 111f)) }
                }
                "Copy buttons", "Buttons" -> {
                    card(22f, 12f, 276f)
                    repeat(3) { box(22f + it * 94, 68f, 88f, 28f, colors.primaryContainer) }
                }
                "History", "Stats" -> {
                    card(25f, 10f, 210f, true); card(25f, 49f, 210f, true); card(25f, 88f, 210f)
                    line(283f, 34f, 283f, 107f); arrow(283f, 107f, 249f)
                }
                else -> repeat(3) { row ->
                    val y = 25f + row * 38
                    box(25f, y - 10, 25f, 25f, colors.primaryContainer)
                    line(31f, y + 1, 36f, y + 6); line(36f, y + 6, 45f, y - 4)
                    box(67f, y - 2, 225f - row * 25, 7f, colors.outlineVariant)
                }
            }
        }
        if (topic == "Copy buttons" || topic == "Buttons") {
            Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp).offset(y = 68.dp).height(28.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                listOf("Snooze", "Dismiss", "Open").forEach { Text(it, Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = colors.onPrimaryContainer, style = MaterialTheme.typography.labelMedium) }
            }
        }
        }
        if (topic in listOf("When", "Profiles", "Quiet hours", "Timing", "Digest")) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("00:00", "Quiet hours", "24:00").forEach { Text(it, style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}


internal fun quietHoursSummary(rows: List<com.husarp.holdfast.data.QuietSchedule>): String {
    if (rows.isEmpty()) return "Off"
    if (rows.size > 1) return "${rows.size} quiet schedules"
    val row = rows.single()
    val days = row.days.split(",").map(String::toInt).sorted()
    val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayText = if (days.size > 1 && days == (days.first()..days.last()).toList())
        "${labels[days.first() - 1]}–${labels[days.last() - 1]}"
    else days.joinToString(", ") { labels[it - 1] }
    return "$dayText ${formatMinute(row.startMinute)}–${formatMinute(row.endMinute)}"
}
