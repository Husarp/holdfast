package com.husarp.holdfast.notifications

import com.husarp.holdfast.data.CapturedNotification
import com.husarp.holdfast.data.NotificationState
import com.husarp.holdfast.BuildConfig

// Plain fields keep filtering and mapping testable without Android notification objects.
data class NotificationFields(
    val packageName: String,
    val appLabel: String,
    val sourceKey: String,
    val channelId: String?,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val debugSource: Boolean = false,
    val ongoing: Boolean = false,
    val foregroundService: Boolean = false,
    val groupSummary: Boolean = false,
    val sender: String? = null,
    val shortcutId: String? = null,
    val userSerial: Long = 0,
)

fun mapNotification(
    fields: NotificationFields,
    ownPackage: String,
    pinned: Boolean,
    now: Long,
    existing: CapturedNotification? = null,
): CapturedNotification? {
    val debug = BuildConfig.DEBUG && fields.debugSource && fields.packageName == ownPackage && fields.channelId == DEBUG_CHANNEL
    if ((!pinned && !debug) || (fields.packageName == ownPackage && !debug) || fields.ongoing || fields.foregroundService || fields.groupSummary) return null
    val newOccurrence = existing != null && (existing.state == NotificationState.DONE || existing.state == NotificationState.EXPIRED) && fields.postedAt > existing.updatedAt
    val initial = existing?.takeUnless { newOccurrence } ?: CapturedNotification(
        id = existing?.id ?: 0, actionVersion = (existing?.actionVersion ?: -1) + 1,
        packageName = fields.packageName, appLabel = fields.appLabel, sourceKey = fields.sourceKey,
        channelId = fields.channelId, title = fields.title.orEmpty(), text = fields.text.orEmpty(),
        postedAt = fields.postedAt, updatedAt = now, userSerial = fields.userSerial,
    )
    return initial.copy(appLabel = fields.appLabel, channelId = fields.channelId,
        title = fields.title.orEmpty(), text = fields.text.orEmpty(), sender = fields.sender, shortcutId = fields.shortcutId, updatedAt = now)
}
