package com.husarp.holdfast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.activity.compose.setContent
import androidx.compose.ui.test.onRoot
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
                Scaffold(topBar = { TopAppBar(title = { Text(screen) }) }, bottomBar = {
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
                                    postedAt = now, updatedAt = now, state = NotificationState.COPY_VISIBLE, userSerial = row.userSerial)
                            }, false)
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
        compose.onRoot().captureRoboImage("screenshots/${screen.lowercase()}-${if (dark) "dark" else "light"}.png")
        activity.pause().stop().destroy()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun cases(): List<Array<Any>> = listOf("Pinned", "Apps", "History", "Settings", "Stats", "Rules", "Profiles", "Places", "Backup", "Setup", "Timing")
            .flatMap { screen -> listOf(false, true).map { dark -> arrayOf(screen, dark) } }
    }
}
