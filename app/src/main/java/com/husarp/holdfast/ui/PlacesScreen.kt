package com.husarp.holdfast.ui

import android.Manifest
import android.content.Intent
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.QuietPlace
import com.husarp.holdfast.location.*
import kotlinx.coroutines.launch

@Composable
fun PlacesScreen() {
    val context = LocalContext.current
    val c = (context.applicationContext as HoldfastApplication).container
    val dao = c.database.dao()
    val places by dao.observePlaces().collectAsState(emptyList())
    val settings by c.repository.settings.collectAsState(com.husarp.holdfast.data.Settings())
    val apps by c.repository.pinnedApps.collectAsState(emptyList())
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var name by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("200") }
    var message by remember { mutableStateOf("") }
    var permitted by remember { mutableStateOf(locationPermitted(context)) }
    val locationRequest = remember { android.os.CancellationSignal() }
    DisposableEffect(Unit) { onDispose { locationRequest.cancel() } }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) permitted = locationPermitted(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permitted = locationPermitted(context)
        message = if (permitted) "Location enabled" else "Location quiet is off. Precise and background location are required."
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsSection("Location access") {
            Text(if (permitted) "Location quiet is on" else "Location quiet is off")
            Text("Precise location saves your current place. Background location lets Android report entry and exit while Holdfast is closed. Choose Allow all the time in app permissions. Location stays on this device. Alerts may be delayed.")
            Button(onClick = { permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }) { Text("Allow precise location") }
            if (Build.VERSION.SDK_INT >= 29) TextButton(onClick = {
                if (Build.VERSION.SDK_INT == 29) permission.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
            }) { Text("Background location settings") }
            }
            SettingsSection("Add a place to this profile") {
            OutlinedTextField(name, { name = it }, label = { Text("Place name") })
            OutlinedTextField(radius, { radius = it }, label = { Text("Radius in meters") })
            apps.filter { it.enabled && it.profileId == settings.activeProfileId }.forEach { app ->
                ListItem(headlineContent = { Text(app.packageName) }, leadingContent = { AppIcon(app.packageName, app.userSerial) }, trailingContent = {
                    Checkbox(app.packageName in selected, { checked -> selected = if (checked) selected + app.packageName else selected - app.packageName })
                })
            }
            Button(enabled = permitted && name.isNotBlank() && selected.isNotEmpty() && (radius.toFloatOrNull()?.let { it.isFinite() && it > 0 } == true), onClick = {
                val manager = context.getSystemService(LocationManager::class.java)
                try {
                    val provider = manager.getProviders(true).firstOrNull { it == LocationManager.GPS_PROVIDER }
                        ?: manager.getProviders(true).firstOrNull()
                    if (provider == null) message = "Enable device location first."
                    else {
                        val savedName = name.trim()
                        val savedRadius = radius.toFloat()
                        val savedPackages = selected.joinToString("\n")
                        message = "Getting current location…"
                        androidx.core.location.LocationManagerCompat.getCurrentLocation(
                            manager, provider, locationRequest,
                            androidx.core.content.ContextCompat.getMainExecutor(context)
                        ) { location ->
                            if (location == null) message = "No current location. Enable location and try outdoors."
                            else scope.launch {
                                dao.savePlace(QuietPlace(name = savedName, latitude = location.latitude, longitude = location.longitude,
                                    radius = savedRadius, packages = savedPackages, inside = true, profileId = settings.activeProfileId))
                                val registered = Places(context).restore()
                                c.copies.recover()
                                message = if (registered) "Place saved" else "Place saved, but proximity alerts are unavailable. Check location permissions and device location."
                            }
                        }
                    }
                } catch (_: SecurityException) { permitted = false; message = "Location permission denied." }
            }) { Text("Save current location") }
            }
            Text("Saved places", style = MaterialTheme.typography.titleMedium)
            ActionMessage(message)
            if (places.isEmpty()) EmptyState("Save a place to pause notifications while you are there.", screenIcon("Places"))
            places.filter { it.profileId == settings.activeProfileId }.forEach { place ->
                SettingsSection(place.name) {
                Text("${place.name}: ${place.radius.toInt()} m — ${if (permitted && place.inside) "inside" else "outside / off"}")
                TextButton(onClick = { scope.launch {
                    Places(context).remove(place.id); dao.removePlace(place.id); c.copies.recover()
                } }) { Text("Remove") }
                }
            }
            }
        }
    }
}
