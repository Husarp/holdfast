package com.husarp.holdfast

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.compose.setContent
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.husarp.holdfast.data.*
import com.husarp.holdfast.ui.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap

@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w412dp-h892dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignScreenshotTest(private val screen: String, private val dark: Boolean) {
    @get:Rule val compose = createEmptyComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test fun render() {
        val context = ApplicationProvider.getApplicationContext<HoldfastApplication>()
        kotlinx.coroutines.runBlocking {
            context.container.repository.ensureProfiles()
            context.container.repository.saveSettings(Settings())
            context.container.repository.setPinned("com.android.messaging", true)
            context.container.database.dao().addRule(MuteRule(packageName = "com.android.messaging", kind = "Title", value = "Special offer"))
        }
        val now = System.currentTimeMillis()
        val history = listOf(
            NotificationEvent(1, 1, "com.android.messaging", "Messages", "Alex", "See you at the harbor at six.", now),
            NotificationEvent(2, 2, "com.android.calendar", "Calendar", "Weekend plans", "A little time for yourself.", now),
            NotificationEvent(3, 3, "com.android.messaging", "Messages · Work", "Design team", "Your review is ready.", now, 10)
        )
        kotlinx.coroutines.runBlocking { history.forEach { context.container.database.dao().saveEvent(it) } }
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            HoldfastTheme(dark = dark, dynamic = false) {
                val title = when (screen) {
                    "Button-editor", "Help-copy-buttons" -> "Copy buttons"
                    "App-page" -> "Messages"
                    "First-run" -> "Welcome"
                    "Help-when", "Day-chips", "Time-picker" -> "When"
                    "Stats", "Stats-with-data" -> "Statistics"
                    "Schedule-editor" -> "Quiet hours"
                    "Icon", "Icon-themed" -> "Holdfast"
                    else -> screen
                }
                Scaffold(topBar = { TopAppBar(title = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(title); HelpButton(title) } }) }, bottomBar = {
                    NavigationBar {
                        listOf("Pinned", "Apps", "When", "History", "Settings").forEach { title ->
                            NavigationBarItem(selected = screen == title || (title == "Settings" && screen !in listOf("Pinned", "Apps", "History")), onClick = {},
                                icon = { Icon(screenIcon(title), null) }, label = { Text(title) })
                        }
                    }
                }) { padding ->
                    Box(Modifier.padding(padding)) {
                        when (screen) {
                            "Pinned" -> PinnedContent(history.map { row ->
                                CapturedNotification(id = row.id, packageName = row.packageName, appLabel = row.appLabel,
                                    sourceKey = row.id.toString(), channelId = null, title = row.title, text = row.text,
                                    postedAt = now - 7_200_000, updatedAt = now, returnCount = 3, state = NotificationState.COPY_VISIBLE, userSerial = row.userSerial)
                            }, false)
                            "Updates" -> com.husarp.holdfast.update.UpdatesContent(
                                com.husarp.holdfast.update.Updates.State(latest = com.husarp.holdfast.update.Updates.Latest("0.2.0", "Holdfast-0.2.0.apk", "", 5_242_880, ""), phase = com.husarp.holdfast.update.Updates.Phase.Downloading(40, 2_097_152, 5_242_880, 800_000)), "0.1.0")
                            "What's new" -> com.husarp.holdfast.update.WhatsNewContent(com.husarp.holdfast.update.WhatsNew.load(context))
                            "First-run" -> IntroScreen()
                            "Help-pinned" -> ScreenHelp("Pinned") {}
                            "Help-when" -> ScreenHelp("When") {}
                            "Help-take-over" -> ScreenHelp("Take-over") {}
                            "Help-apps" -> ScreenHelp("Apps") {}
                            "Help-app-page" -> ScreenHelp("App page") {}
                            "Help-history" -> ScreenHelp("History") {}
                            "Help-settings" -> ScreenHelp("Settings") {}
                            "Help-copy-buttons" -> ScreenHelp("Copy buttons", "Choose up to three visible buttons.\nReorder slots with the arrows.\nUnavailable actions use the next fallback.") {}
                            "Time-picker" -> ClockDialog("18:30", {}, {})
                            "Day-chips" -> Column(Modifier.padding(16.dp)) { Text("Quiet hours"); DayChips("1,2,3,4,5") {} }
                            "When" -> WhenScreen {}
                            "About" -> Column(Modifier.padding(16.dp)) { Text("Holdfast 0.2.0"); Text("All data stays on this device."); TextButton(onClick = {}) { Text("Show intro again") } }
                            "Button-editor" -> Column(Modifier.padding(16.dp)) { ButtonEditor(listOf("snooze:60", "dismiss", "reply")) {} }
                            "App-page" -> AppPage(LaunchableApp("com.android.messaging", "Messages", context.packageManager.defaultActivityIcon), {})
                            "Pause" -> Column(Modifier.padding(16.dp)) { Text("Paused until tomorrow, 08:00"); PauseControls(initiallyExpanded = true) }
                            "Icon" -> IconPreview(false)
                            "Icon-themed" -> IconPreview(true)
                            "Apps" -> AppsContent(history.map {
                                LaunchableApp(it.packageName, it.appLabel, context.packageManager.defaultActivityIcon, it.userSerial)
                            }, listOf(PinnedApp("com.android.messaging")))
                            "History" -> HistoryHub()
                            "Stats", "Stats-with-data" -> StatsContent(history + (4L..18L).map { id -> history.first().copy(id = id, capturedAt = now - (id % 7) * 86_400_000, heldAsCopy = id % 2L == 0L) }, listOf(CopyOpen(packageName = "com.android.messaging", openedAt = now)))
                            "Settings" -> SettingsScreen {}
                            "Rules" -> RulesScreen()
                            "Schedule-editor" -> Column(Modifier.padding(16.dp).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                WeeklyScheduleEditor(listOf(QuietSchedule(packageName = "", days = "1", startMinute = 900, endMinute = 960), QuietSchedule(packageName = "", days = "1", startMinute = 1200, endMinute = 1260), QuietSchedule(packageName = "", days = "2", startMinute = 1320, endMinute = 480))) { _, _ -> }
                            }
                            "Profiles" -> ProfilesScreen()
                            "Places" -> PlacesScreen()
                            "Backup" -> BackupScreen()
                            "Setup" -> SetupScreen()
                            "Timing" -> Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { TimingSettings() }
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        if (screen.startsWith("Help-")) compose.onNodeWithText("Got it").assertExists()

        if (screen == "Timing") compose.waitUntil(10_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText("Save timing")).fetchSemanticsNodes().isNotEmpty()
        }
        File("screenshots").mkdirs()
        compose.onAllNodes(androidx.compose.ui.test.isRoot()).onLast().captureRoboImage("screenshots/${screen.lowercase().replace("'", "").replace(" ", "-")}-${if (dark) "dark" else "light"}.png")
        activity.pause().stop().destroy()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> = listOf("Schedule-editor", "Stats-with-data", "Pinned", "Apps", "History", "Settings", "Stats", "Rules", "Profiles", "Places", "Backup", "Setup", "Timing", "Updates", "What's new", "First-run", "App-page", "Help-pinned", "When", "About", "Button-editor", "Pause", "Icon", "Icon-themed", "Help-when", "Help-copy-buttons", "Help-take-over", "Help-apps", "Help-app-page", "Help-history", "Help-settings", "Time-picker", "Day-chips")
            .flatMap { screen -> listOf(false, true).map { dark -> arrayOf(screen, dark) } }
    }
}

@androidx.compose.runtime.Composable
private fun IconPreview(themed: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val drawable = androidx.compose.runtime.remember { context.getDrawable(R.mipmap.ic_launcher) as android.graphics.drawable.AdaptiveIconDrawable }
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        if (themed) Surface(Modifier.size(144.dp), shape = androidx.compose.foundation.shape.CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            val mono = androidx.compose.runtime.remember { (drawable.monochrome ?: drawable.foreground).toBitmap(144, 144).asImageBitmap() }
            Icon(mono, "Themed Holdfast launcher icon", Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        } else {
            val bitmap = androidx.compose.runtime.remember { drawable.toBitmap(144, 144) }
            androidx.compose.foundation.Image(bitmap.asImageBitmap(), "Holdfast launcher icon", Modifier.size(144.dp))
        }
    }
}
