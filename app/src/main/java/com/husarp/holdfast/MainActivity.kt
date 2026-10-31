package com.husarp.holdfast

import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.unit.dp
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import com.husarp.holdfast.ui.AppsViewModel
import com.husarp.holdfast.ui.AppsScreen
import com.husarp.holdfast.ui.SetupScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*

class MainActivity : ComponentActivity() {
    private val appsViewModel: AppsViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        com.husarp.holdfast.update.Updates.onResume(this)
        val container = (application as HoldfastApplication).container
        container.scope.launch { com.husarp.holdfast.location.Places(applicationContext).restore(); container.copies.recover() }
    }

    override fun onPause() { com.husarp.holdfast.update.Updates.onPause(); super.onPause() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.husarp.holdfast.ui.HoldfastTheme {
                val nav = rememberNavController()
                val prefs = remember { getSharedPreferences("guide", 0) }
                LaunchedEffect(Unit) { if (!prefs.getBoolean("done", false)) nav.navigate("Intro") }
                var help by remember { mutableStateOf(false) }
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    if (intent.getBooleanExtra("setup", false)) nav.navigate("Setup")
                    if (intent.getBooleanExtra("pause", false)) nav.navigate("Pause")
                }
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: "Pinned"
                Scaffold(topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(title = { Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(if (route == "Stats") "Statistics" else route.substringBefore("/")); TextButton(onClick = { help = true }) { Text("?") } } },
                        navigationIcon = {
                            if (route !in listOf("Pinned", "Apps", "When", "History", "Settings"))
                                TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                        })
                }, bottomBar = {
                    NavigationBar {
                        listOf("Pinned", "Apps", "When", "History", "Settings").forEach { screen ->
                            NavigationBarItem(selected = route == screen || (screen == "When" && route in listOf("Profiles", "Places")) ||
                                (screen == "Settings" && route !in listOf("Pinned", "Apps", "When", "History", "Profiles", "Places", "Intro")),
                                onClick = { nav.navigate(screen) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                icon = { Icon(com.husarp.holdfast.ui.screenIcon(screen), null) }, label = { Text(screen) })
                        }
                    }
                }) { padding ->
                    if (help) com.husarp.holdfast.ui.ScreenHelp(route) { help = false }
                    NavHost(nav, startDestination = "Pinned", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                        composable("Apps") { AppsScreen(appsViewModel) }
                        composable("Pinned") { com.husarp.holdfast.ui.PinnedScreen(openSetup = { nav.navigate("Setup") }, navigate = { nav.navigate(it) }) }
                        composable("When") { com.husarp.holdfast.ui.WhenScreen { nav.navigate(it) } }
                        composable("Intro") { com.husarp.holdfast.ui.IntroScreen({ nav.navigate(it) }, { prefs.edit().putBoolean("done", true).apply(); nav.popBackStack() }) }
                        composable("About") { Column(Modifier.padding(16.dp)) { Text("Holdfast ${BuildConfig.VERSION_NAME} · All data stays on this device."); TextButton(onClick = { nav.navigate("Intro") }) { Text("Show intro again") } } }
                        composable("History") { com.husarp.holdfast.ui.HistoryHub() }
                        composable("Places") { com.husarp.holdfast.ui.PlacesScreen() }
                        composable("Backup") { com.husarp.holdfast.ui.BackupScreen() }
                        composable("Profiles") { com.husarp.holdfast.ui.ProfilesScreen() }
                        composable("Pause") { com.husarp.holdfast.ui.PauseControls(initiallyExpanded = true) }
                        composable("Setup") { SetupScreen() }
                        composable("Timing") {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { com.husarp.holdfast.ui.TimingSettings() }
                        }
                        composable("Updates") { com.husarp.holdfast.update.UpdatesScreen() }
                        composable("What’s new") { com.husarp.holdfast.update.WhatsNewScreen() }
                        composable("Settings") { com.husarp.holdfast.ui.SettingsScreen { nav.navigate(it) } }
                    }
                }
            }
        }
    }
}
