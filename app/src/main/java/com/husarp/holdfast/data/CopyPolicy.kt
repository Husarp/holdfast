package com.husarp.holdfast.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import android.service.notification.NotificationListenerService as Listener

object CopyMode {
    const val OFF = "Off"
    const val SWIPE = "Bring back when I swipe it"
    const val TAKE_OVER = "Take over right away"
    const val KEEP = "Keep after it's opened or removed"
    val choices = listOf(OFF, SWIPE, TAKE_OVER, KEEP)
}

@Entity
data class TypePolicy(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String, val profileId: Long = 1,
    val kind: String = "Channel", val value: String,
    val mode: String? = null, val openAndFinish: Boolean? = null, val buttons: String? = null,
) {
    init { require(kind in listOf("Channel", "Title", "Text")); require(value.isNotBlank()); require(mode == null || mode in CopyMode.choices) }
    fun matches(row: CapturedNotification) = packageName == row.packageName && when (kind) {
        "Channel" -> row.channelId == value
        "Title" -> row.title.contains(value, true)
        else -> row.text.contains(value, true)
    }
}

data class ResolvedPolicy(val mode: String, val openAndFinish: Boolean, val buttons: String)
fun resolvePolicy(row: CapturedNotification, types: List<TypePolicy>, app: AppOptions, settings: Settings): ResolvedPolicy {
    // Text rules are more specific than a channel; newest rule wins within each kind.
    val matches = types.filter { it.profileId == settings.activeProfileId && it.matches(row) }
        .sortedWith(compareBy<TypePolicy> { if (it.kind == "Channel") 1 else 0 }.thenByDescending { it.id })
    val mode = matches.firstNotNullOfOrNull { it.mode } ?: app.mode ?: if (settings.holdEverything) CopyMode.TAKE_OVER else CopyMode.SWIPE
    return ResolvedPolicy(mode, matches.firstNotNullOfOrNull { it.openAndFinish } ?: app.openAndFinish ?: (mode != CopyMode.TAKE_OVER && mode != CopyMode.KEEP),
        matches.firstNotNullOfOrNull { it.buttons } ?: app.buttons)
}

fun copiesOnRemoval(mode: String, reason: Int): Boolean = when (mode) {
    CopyMode.SWIPE -> reason == Listener.REASON_CANCEL || reason == Listener.REASON_CANCEL_ALL
    CopyMode.KEEP -> reason in listOf(Listener.REASON_CANCEL, Listener.REASON_CANCEL_ALL, Listener.REASON_CLICK, Listener.REASON_APP_CANCEL, Listener.REASON_APP_CANCEL_ALL)
    else -> false
}
