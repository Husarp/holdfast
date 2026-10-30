package com.husarp.holdfast.ui

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserManager

fun profileIcon(context: Context, pkg: String, serial: Long): Drawable {
    val user = context.getSystemService(UserManager::class.java).getUserForSerialNumber(serial)
    return try {
        context.getSystemService(LauncherApps::class.java).getActivityList(pkg, user)
            .firstOrNull()?.getBadgedIcon(0) ?: context.packageManager.getApplicationIcon(pkg)
    } catch (_: RuntimeException) {
        context.packageManager.defaultActivityIcon
    } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
        context.packageManager.defaultActivityIcon
    }
}
