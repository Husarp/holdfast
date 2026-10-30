package com.husarp.holdfast

import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import com.husarp.holdfast.ui.AppsViewModel
import com.husarp.holdfast.ui.AppsScreen
import com.husarp.holdfast.ui.SetupScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
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
        setContent {
            MaterialTheme {
                val nav = rememberNavController()
                val entry by nav.currentBackStackEntryAsState()
                Scaffold(bottomBar = {
                    NavigationBar {
                        listOf("Apps", "Pinned", "Settings").forEach { screen ->
                            NavigationBarItem(selected = entry?.destination?.route == screen,
                                onClick = { nav.navigate(screen) { popUpTo(nav.graph.startDestinationId) { saveState = true }; launchSingleTop = true; restoreState = true } },
                                icon = { Text(screen.take(1)) }, label = { Text(screen) })
                        }
                    }
                }) { padding ->
                    NavHost(nav, startDestination = "Apps", modifier = Modifier.padding(padding)) {
                        composable("Apps") { AppsScreen(appsViewModel) }
                        composable("Pinned") { com.husarp.holdfast.ui.PinnedScreen() }
                        composable("History") { com.husarp.holdfast.ui.HistoryScreen() }
                        composable("Stats") { com.husarp.holdfast.ui.StatsScreen() }
                        composable("Places") { com.husarp.holdfast.ui.PlacesScreen() }
                        composable("Backup") { com.husarp.holdfast.ui.BackupScreen() }
                        composable("Profiles") { com.husarp.holdfast.ui.ProfilesScreen() }
                        composable("Rules") { com.husarp.holdfast.ui.RulesScreen() }
                        composable("Settings") {
                            Column {
                                com.husarp.holdfast.ui.SpamHints()
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    listOf("History", "Stats", "Rules", "Profiles", "Backup", "Places").forEach { screen ->
                                        TextButton(onClick = { nav.navigate(screen) }) { Text(screen) }
                                    }
                                }
                                Box(Modifier.weight(1f)) { SetupScreen() }
                            }
                        }
                    }
                }
            }
        }
    }
}
