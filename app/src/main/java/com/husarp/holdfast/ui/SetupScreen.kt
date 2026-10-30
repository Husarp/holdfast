package com.husarp.holdfast.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

internal data class SetupStatus(val listener: Boolean, val posting: Boolean, val exact: Boolean, val battery: Boolean, val copies: Boolean)

internal fun setupStatus(context: Context): SetupStatus {
    val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val listener = listeners.split(':').mapNotNull(ComponentName::unflattenFromString)
        .any { it.packageName == context.packageName && it.className == "${context.packageName}.notifications.HoldfastListenerService" }
    return SetupStatus(listener,
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        context.getSystemService(NotificationManager::class.java).getNotificationChannel("copies")?.importance != NotificationManager.IMPORTANCE_NONE)
}

@Composable
fun SetupScreen() {
    val context = LocalContext.current
    val container = (context.applicationContext as com.husarp.holdfast.HoldfastApplication).container
    val connected by container.listenerConnected.collectAsState()
    var status by remember { mutableStateOf(setupStatus(context)) }
    var requestedPosting by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val postingPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        status = setupStatus(context)
    }
    LifecycleResumeEffect(Unit) {
        status = setupStatus(context)
        onPauseOrDispose { }
    }
    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
            error = null
        } catch (_: android.content.ActivityNotFoundException) {
            error = "This settings screen is unavailable. Open Android Settings manually."
        } catch (_: SecurityException) {
            error = "Android blocked this settings screen. Open Android Settings manually."
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Notification access lets Holdfast read and store full available titles and text from selected apps. All data stays on this device. Turn access off in Android Settings at any time.")
        SetupCard("Notification access", status.listener, "Open notification access") {
            open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        Text(if (connected) "Listener connected" else "Listener disconnected: new notifications cannot be captured until Android reconnects it.")
        if (status.listener && !connected) Button(onClick = {
            try { android.service.notification.NotificationListenerService.requestRebind(ComponentName(context,
                com.husarp.holdfast.notifications.HoldfastListenerService::class.java)) }
            catch (_: SecurityException) { error = "Re-enable notification access in Android Settings." }
        }) { Text("Reconnect listener") }
        SetupCard("Post notifications", status.posting, if (Build.VERSION.SDK_INT >= 33) "Allow notifications" else "Open notification settings") {
            if (Build.VERSION.SDK_INT >= 33 && !requestedPosting && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestedPosting = true
                postingPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }
        SetupCard("Exact alarms", status.exact, "Open alarm settings",
            if (Build.VERSION.SDK_INT < 31) "No special access needed on this Android version." else "Without access, reminders will use approximate timing.") {
            if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
        SetupCard("Battery optimization exemption", status.battery, "Open battery settings",
            "Find Holdfast and choose unrestricted use if needed. Device battery controls can still delay notifications.") {
            open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        if (!status.copies) {
            Text("Pinned copies channel is blocked. Enable it in notification settings.")
        }
        Text("If posting is blocked, copies stay in Pinned. Enable notifications and reopen Holdfast to restore them.")
        Button(onClick = {
            error = if (com.husarp.holdfast.notifications.sendTestNotification(context)) null
                else "Notifications or the Test source channel are blocked. Enable them in Android Settings."
        }) { Text("Send test notification") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun SetupCard(title: String, granted: Boolean, button: String, description: String? = null, action: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(if (granted) "Enabled" else "Not enabled")
            description?.let { Text(it) }
            Button(onClick = action) { Text(button) }
        }
    }
}
