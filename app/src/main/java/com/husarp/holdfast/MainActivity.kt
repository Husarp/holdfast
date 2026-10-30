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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.*

class MainActivity : ComponentActivity() {
    private val appsViewModel: AppsViewModel by viewModels()

    override fun onResume() {
        super.onResume()
        val container = (application as HoldfastApplication).container
        container.scope.launch { com.husarp.holdfast.location.Places(applicationContext).restore(); container.copies.recover() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            com.husarp.holdfast.ui.HoldfastTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                val route = entry?.destination?.route ?: "Pinned"
                Scaffold(topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(title = { Text(if (route == "Stats") "Statistics" else route) },
                        navigationIcon = {
                            if (route !in listOf("Pinned", "Apps", "History", "Settings"))
                                TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                        })
                }, bottomBar = {
                    NavigationBar {
                        listOf("Pinned", "Apps", "History", "Settings").forEach { screen ->
                            NavigationBarItem(selected = route == screen || (screen == "Settings" && route !in listOf("Pinned", "Apps", "History")),
                                onClick = { nav.navigate(screen) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                icon = { Icon(com.husarp.holdfast.ui.screenIcon(screen), null) }, label = { Text(screen) })
                        }
                    }
                }) { padding ->
                    NavHost(nav, startDestination = "Pinned", modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                        composable("Apps") { AppsScreen(appsViewModel) }
                        composable("Pinned") { com.husarp.holdfast.ui.PinnedScreen { nav.navigate("Setup") } }
                        composable("History") { com.husarp.holdfast.ui.HistoryScreen() }
                        composable("Stats") { com.husarp.holdfast.ui.StatsScreen() }
                        composable("Places") { com.husarp.holdfast.ui.PlacesScreen() }
                        composable("Backup") { com.husarp.holdfast.ui.BackupScreen() }
                        composable("Profiles") { com.husarp.holdfast.ui.ProfilesScreen() }
                        composable("Rules") { com.husarp.holdfast.ui.RulesScreen() }
                        composable("Setup") { SetupScreen() }
                        composable("Timing") {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { com.husarp.holdfast.ui.TimingSettings() }
                        }
                        composable("Settings") { com.husarp.holdfast.ui.SettingsScreen { nav.navigate(it) } }
                    }
                }
            }
        }
    }
}
