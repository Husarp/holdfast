package com.husarp.holdfast

import android.app.Notification
import android.app.Person
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.test.core.app.ApplicationProvider
import com.husarp.holdfast.data.CapturedNotification
import com.husarp.holdfast.notifications.OriginalAppearance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OriginalAppearanceTest {
    private val app = ApplicationProvider.getApplicationContext<HoldfastApplication>()
    private val row = CapturedNotification(packageName = app.packageName, appLabel = "Holdfast", sourceKey = "appearance", channelId = "source", title = "Fallback", text = "Fallback text", postedAt = 1, updatedAt = 1)
    private fun builder() = Notification.Builder(app, "source").setSmallIcon(R.drawable.ic_holdfast).setContentTitle("Title").setContentText("Text")
    @Test fun deleteRemovesSavedImagesAndMemory() {
        val store = OriginalAppearance(app)
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val avatar = Icon.createWithBitmap(image)
        val style = Notification.MessagingStyle(Person.Builder().setName("Me").setIcon(avatar).build())
            .addMessage("Hello", 1, Person.Builder().setName("Alex").setIcon(avatar).build())
        store.save(row, builder().setLargeIcon(image).setStyle(style).build())
        assertTrue(java.io.File(app.filesDir, "appearance").listFiles()!!.isNotEmpty())
        store.delete(row.sourceKey)
        assertFalse(store.originals.containsKey(row.sourceKey))
        assertTrue(java.io.File(app.filesDir, "appearance").listFiles()!!.isEmpty())
        store.save(row, builder().setStyle(Notification.BigPictureStyle().bigPicture(image)).build())
        OriginalAppearance(app).delete(row.sourceKey)
        assertTrue(java.io.File(app.filesDir, "appearance").listFiles()!!.isEmpty())
    }

    @Test fun sweepRemovesUnknownFilesAndCompletedAppearancesButKeepsLiveFiles() {
        val store = OriginalAppearance(app)
        store.save(row, builder().build())
        store.save(row.copy(sourceKey = "finished"), builder().build())
        val unknown = java.io.File(app.filesDir, "appearance/unknown.png").apply { writeText("orphan") }
        val unknownJson = java.io.File(app.filesDir, "appearance/unknown.json").apply { writeText("broken") }
        store.sweep(setOf(row.sourceKey))
        assertFalse(unknown.exists())
        assertFalse(unknownJson.exists())
        assertNull(store.load("finished"))
        assertNotNull(store.load(row.sourceKey))
        assertNotNull(OriginalAppearance(app).builder(row, "copies").build().smallIcon)
        store.delete(row.sourceKey)
        assertTrue(java.io.File(app.filesDir, "appearance").listFiles()!!.isEmpty())
    }

    @Test fun deletingOneNotificationPreservesSharedSmallIcon() {
        val store = OriginalAppearance(app)
        store.save(row, builder().build())
        store.save(row.copy(sourceKey = "other"), builder().build())
        val icon = store.load("other")!!.smallIcon!!
        store.delete(row.sourceKey)
        assertTrue(java.io.File(app.filesDir, "appearance/$icon").exists())
        store.delete("other")
        assertTrue(java.io.File(app.filesDir, "appearance").listFiles()!!.isEmpty())
    }

    @Test fun bigTextIconsAndMetadataSurviveNewStore() {
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val original = builder().setLargeIcon(bitmap).setSubText("Original subtext").setColor(Color.BLUE).setCategory(Notification.CATEGORY_REMINDER)
            .setStyle(Notification.BigTextStyle().bigText("Full text")).build()
        OriginalAppearance(app).save(row, original)
        val rebuilt = OriginalAppearance(app).builder(row, "copies").build()
        assertEquals("Title", rebuilt.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals("Full text", rebuilt.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        assertEquals("Original subtext", rebuilt.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
        assertEquals(Color.BLUE, rebuilt.color); assertEquals(Notification.CATEGORY_REMINDER, rebuilt.category)
        assertEquals(Icon.TYPE_BITMAP, rebuilt.smallIcon.type); assertNotNull(rebuilt.getLargeIcon())
    }
    @Test fun inboxAndPictureSurviveWithoutLiveOriginal() {
        val store = OriginalAppearance(app)
        store.save(row, builder().setStyle(Notification.InboxStyle().addLine("One").addLine("Two")).build())
        assertArrayEquals(arrayOf("One", "Two"), OriginalAppearance(app).builder(row, "copies").build().extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES))
        store.save(row, builder().setStyle(Notification.BigPictureStyle().bigPicture(Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888))).build())
        assertNotNull(OriginalAppearance(app).builder(row, "copies").build().extras.getParcelable<Bitmap>(Notification.EXTRA_PICTURE))
    }
    @Test fun conversationSendersAndAvatarsSurvive() {
        val avatar = Icon.createWithBitmap(Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888))
        val style = Notification.MessagingStyle(Person.Builder().setName("Me").build()).setConversationTitle("Study group").setGroupConversation(true)
            .addMessage("Cards due", 100, Person.Builder().setName("Alex").setIcon(avatar).build())
        OriginalAppearance(app).save(row, builder().setStyle(style).build())
        val recovered = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(OriginalAppearance(app).builder(row, "copies").build())!!
        assertEquals("Study group", recovered.conversationTitle)
        assertEquals("Alex", recovered.messages.single().person?.name)
        assertNotNull(recovered.messages.single().person?.icon)
    }
    @Test fun brokenSourceUsesManualFallbackAndClearsSourceActions() {
        val store = OriginalAppearance(app)
        val missing = row.copy(packageName = "missing.package", sourceKey = "missing")
        store.originals[missing.sourceKey] = builder().build()
        val rebuilt = store.builder(missing, "copies").build()
        assertEquals("Fallback", rebuilt.extras.getCharSequence(Notification.EXTRA_TITLE))
        assertEquals(R.drawable.ic_holdfast, rebuilt.smallIcon.resId)
        assertTrue(rebuilt.actions.isNullOrEmpty()); assertNull(rebuilt.group)
        assertNull(rebuilt.extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }
    @Test fun liveRecoveryKeepsOriginalStyleButRemovesActionsAndGroup() {
        val store = OriginalAppearance(app)
        store.save(row, builder().setGroup("original").setGroupSummary(true).setTimeoutAfter(100)
            .setStyle(Notification.BigTextStyle().bigText("Expanded")).build().apply {
                fullScreenIntent = android.app.PendingIntent.getActivity(app, 20, android.content.Intent(app, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE)
            })
        val rebuilt = store.builder(row, "copies").build()
        assertEquals("Expanded", rebuilt.extras.getCharSequence(Notification.EXTRA_BIG_TEXT))
        assertNull(rebuilt.group); assertNull(rebuilt.fullScreenIntent); assertEquals(0L, rebuilt.timeoutAfter); assertTrue(rebuilt.actions.isNullOrEmpty())
    }
}
