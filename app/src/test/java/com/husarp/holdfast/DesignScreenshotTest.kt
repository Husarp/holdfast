package com.husarp.holdfast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.compose.setContent
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
        val activity = org.robolectric.Robolectric.buildActivity(androidx.activity.ComponentActivity::class.java).setup().visible()
        activity.get().setContent {
            HoldfastTheme(dark = dark, dynamic = false) {
                Scaffold(topBar = { MediumTopAppBar(title = { Text(screen) }) }, bottomBar = {
                    NavigationBar {
                        listOf("Pinned", "Apps", "History", "Settings").forEach { title ->
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
                            "First-run" -> FirstRunGuide({})
                            "App-settings" -> AppSettingsSheet(LaunchableApp("com.android.messaging", "Messages", context.packageManager.defaultActivityIcon), {}, {})
                            "Pause" -> Column(Modifier.padding(16.dp)) { Text("Paused until tomorrow, 08:00"); PauseControls(initiallyExpanded = true) }
                            "Icon" -> IconPreview(false)
                            "Icon-themed" -> IconPreview(true)
                            "Apps" -> AppsContent(history.map {
                                LaunchableApp(it.packageName, it.appLabel, context.packageManager.defaultActivityIcon, it.userSerial)
                            }, listOf(PinnedApp("com.android.messaging")))
                            "History" -> HistoryContent(history)
                            "Stats" -> StatsContent(history)
                            "Settings" -> SettingsScreen {}
                            "Rules" -> RulesScreen()
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
        fun cases(): List<Array<Any>> = listOf("Pinned", "Apps", "History", "Settings", "Stats", "Rules", "Profiles", "Places", "Backup", "Setup", "Timing", "Updates", "What's new", "First-run", "App-settings", "Pause", "Icon", "Icon-themed")
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
