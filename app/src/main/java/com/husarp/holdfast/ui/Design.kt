package com.husarp.holdfast.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

@Composable
fun HoldfastTheme(dark: Boolean = isSystemInDarkTheme(), dynamic: Boolean = true, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = Color(0xFF80D5C5), onPrimary = Color(0xFF00382F), secondary = Color(0xFFB1CCC5), onSecondary = Color(0xFF1C352D),
            primaryContainer = Color(0xFF005045), onPrimaryContainer = Color(0xFF9CF2E0),
            secondaryContainer = Color(0xFF334C45), onSecondaryContainer = Color(0xFFD3E9E0),
            tertiary = Color(0xFFA5CDE0), tertiaryContainer = Color(0xFF244C5D),
            background = Color(0xFF101D1A), surface = Color(0xFF101D1A),
            surfaceContainer = Color(0xFF1A2B25), surfaceContainerLow = Color(0xFF162620),
            surfaceContainerHigh = Color(0xFF24352F), surfaceContainerHighest = Color(0xFF2D3E37),
            surfaceVariant = Color(0xFF3D4943), onSurfaceVariant = Color(0xFFBCCBC3),
            surfaceTint = Color(0xFF80D5C5))
        else -> lightColorScheme(primary = Color(0xFF006B5C), onPrimary = Color.White, secondary = Color(0xFF4B635D), onSecondary = Color.White,
            primaryContainer = Color(0xFF9CF2E0), onPrimaryContainer = Color(0xFF002019),
            secondaryContainer = Color(0xFFD3E9E0), onSecondaryContainer = Color(0xFF102C24),
            tertiary = Color(0xFF3E6374), tertiaryContainer = Color(0xFFC1E9FD),
            background = Color(0xFFF4FBF7), surface = Color(0xFFF4FBF7),
            surfaceContainer = Color(0xFFE8F2EC), surfaceContainerLow = Color(0xFFEEF7F1),
            surfaceContainerHigh = Color(0xFFE0ECE5), surfaceContainerHighest = Color(0xFFD8E6DE),
            surfaceVariant = Color(0xFFDCE6DE), onSurfaceVariant = Color(0xFF404A44),
            surfaceTint = Color(0xFF006B5C))
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

fun screenIcon(screen: String): ImageVector = when (screen) {
    "Pinned" -> Icons.Outlined.PushPin
    "Apps" -> Icons.Outlined.Apps
    "History" -> Icons.Outlined.History
    "Stats" -> Icons.Outlined.BarChart
    "Rules" -> Icons.Outlined.FilterAlt
    "Profiles" -> Icons.Outlined.WorkOutline
    "Places" -> Icons.Outlined.Place
    "Backup" -> Icons.Outlined.SaveAlt
    "When" -> Icons.Outlined.Schedule
    "Timing" -> Icons.Outlined.Schedule
    "Setup" -> Icons.Outlined.CheckCircleOutline
    else -> Icons.Outlined.Settings
}

@Composable
fun AppIcon(pkg: String, serial: Long = 0) {
    val context = LocalContext.current
    val bitmap = remember(pkg, serial) { profileIcon(context, pkg, serial).toBitmap(80, 80).asImageBitmap() }
    Image(bitmap, null, Modifier.size(48.dp))
}

@Composable
fun EmptyState(help: String, icon: ImageVector = Icons.Outlined.NotificationsNone) {
    Card(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Text(help, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun SettingsScreen(navigate: (String) -> Unit) {
    androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Make Holdfast yours", style = MaterialTheme.typography.titleLarge) }
        item {
            SettingsSection("Setup and updates") {
                listOf("Setup", "Timing", "Updates", "What’s new").forEach { SettingsRow(it, navigate) }
            }
        }
        item { SettingsSection("Data and app") { listOf("Pause", "Backup", "About").forEach { SettingsRow(it, navigate) } } }
    }
}

@Composable
private fun SettingsRow(title: String, navigate: (String) -> Unit) {
    ListItem(headlineContent = { Text(if (title == "Stats") "Statistics" else title) },
        leadingContent = { Icon(screenIcon(title), null) },
        trailingContent = { IconButton(onClick = { navigate(title) }) { Icon(Icons.Outlined.ChevronRight, "Open $title") } },
        modifier = Modifier.fillMaxWidth().clickable { navigate(title) })
}

@Composable
fun ActionMessage(message: String) {
    val host = remember { SnackbarHostState() }
    LaunchedEffect(message) { if (message.isNotBlank()) host.showSnackbar(message) }
    SnackbarHost(host)
}
