package com.husarp.holdfast.location

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import com.husarp.holdfast.HoldfastApplication
import com.husarp.holdfast.data.QuietPlace
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

fun locationPermitted(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
        (Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED)

class Places(private val context: Context) {
    private val manager get() = context.getSystemService(LocationManager::class.java)
    private fun pending(id: Long) = PendingIntent.getBroadcast(context, 0,
        Intent(context, PlaceReceiver::class.java).setData(Uri.parse("holdfast://place/$id")).putExtra("id", id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
    fun remove(id: Long) {
        try { manager.removeProximityAlert(pending(id)) } catch (_: SecurityException) { }
    }
    fun register(place: QuietPlace): Boolean {
        if (!locationPermitted(context)) return false
        return try {
            manager.addProximityAlert(place.latitude, place.longitude, place.radius, -1, pending(place.id)); true
        } catch (_: SecurityException) { false } catch (_: IllegalArgumentException) { false }
    }
    suspend fun restore(): Boolean {
        val c = (context.applicationContext as HoldfastApplication).container
        return c.database.dao().observePlaces().first().map { register(it) }.all { it }
    }
}

class PlaceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!locationPermitted(context) || !intent.hasExtra(LocationManager.KEY_PROXIMITY_ENTERING)) return
        val pending = goAsync()
        val c = (context.applicationContext as HoldfastApplication).container
        c.scope.launch {
            try {
                val d = c.database.dao()
                val place = d.observePlaces().first().find { it.id == intent.getLongExtra("id", -1) } ?: return@launch
                d.savePlace(place.copy(inside = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)))
                c.copies.recover()
            } finally { pending.finish() }
        }
    }
}
