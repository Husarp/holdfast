package com.husarp.holdfast.notifications

import android.app.Notification
import android.app.Person
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import com.google.gson.Gson
import com.husarp.holdfast.data.CapturedNotification
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal data class SavedMessage(val text: String, val time: Long, val sender: String?, val avatar: String?)
internal data class Appearance(
    val title: String?, val text: String?, val bigText: String?, val subText: String?,
    val lines: List<String>?, val conversation: String?, val group: Boolean,
    val messages: List<SavedMessage>, val picture: String?, val largeIcon: String?,
    val color: Int, val category: String?, val smallIcon: String?,
    val userName: String?, val userAvatar: String?,
)

class OriginalAppearance(private val context: Context) {
    val originals = ConcurrentHashMap<String, Notification>()
    private val gson = Gson()
    private val directory get() = File(context.filesDir, "appearance").apply { mkdirs() }
    private fun name(key: String) = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun bitmap(key: String, bitmap: Bitmap?): String? {
        if (bitmap == null) return null
        val file = File(directory, "${name(key)}.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.name
    }
    private fun readBitmap(file: String?) = file?.let { BitmapFactory.decodeFile(File(directory, it).path) }
    private fun Appearance.files() = listOfNotNull(picture, largeIcon, smallIcon, userAvatar) + messages.mapNotNull { it.avatar }

    @Synchronized
    fun delete(key: String) {
        val files = load(key)?.files().orEmpty()
        originals.remove(key)
        File(directory, "${name(key)}.json").delete()
        val shared = directory.listFiles().orEmpty().filter { it.extension == "json" }.flatMap { file ->
            try { gson.fromJson(file.readText(), Appearance::class.java).files() } catch (_: Exception) { emptyList() }
        }.toSet()
        files.filter { it !in shared }.forEach { File(directory, File(it).name).delete() }
    }

    @Synchronized
    fun sweep(liveKeys: Set<String>) {
        originals.keys.retainAll(liveKeys)
        val keep = liveKeys.flatMap { key -> listOf("${name(key)}.json") + load(key)?.files().orEmpty() }.toSet()
        directory.listFiles().orEmpty().filter { it.name !in keep }.forEach { it.delete() }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    fun save(row: CapturedNotification, notification: Notification) {
        originals[row.sourceKey] = notification
        try {
            val source = context.createPackageContext(row.packageName, 0)
            val small = notification.smallIcon?.loadDrawable(source)?.toBitmap(48, 48)
            val extras = notification.extras
            val style = androidx.core.app.NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
            val messages = style?.messages.orEmpty().mapIndexed { index, message ->
                SavedMessage(message.text.toString(), message.timestamp, message.person?.name?.toString(),
                    bitmap("${row.sourceKey}:avatar:$index", message.person?.icon?.loadDrawable(source)?.toBitmap(64, 64)))
            }
            val saved = Appearance(extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(), extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
                extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(), extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
                extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }, style?.conversationTitle?.toString(), style?.isGroupConversation ?: false, messages,
                bitmap("${row.sourceKey}:picture", (extras.getParcelable(Notification.EXTRA_PICTURE) as? Bitmap) ?: (extras.getParcelable("android.pictureIcon") as? Icon)?.loadDrawable(source)?.toBitmap()),
                bitmap("${row.sourceKey}:large", notification.getLargeIcon()?.loadDrawable(source)?.toBitmap()), notification.color, notification.category,
                bitmap("${row.packageName}:${row.userSerial}:small", small), style?.user?.name?.toString(),
                bitmap("${row.sourceKey}:user", style?.user?.icon?.loadDrawable(source)?.toBitmap(64, 64)))
            File(directory, "${name(row.sourceKey)}.json").writeText(gson.toJson(saved))
        } catch (_: RuntimeException) { } catch (_: java.io.IOException) { } catch (_: android.content.pm.PackageManager.NameNotFoundException) { }
    }

    internal fun load(key: String): Appearance? = try {
        gson.fromJson(File(directory, "${name(key)}.json").readText(), Appearance::class.java)
    } catch (_: Exception) { null }

    @Suppress("DEPRECATION")
    fun builder(row: CapturedNotification, channel: String): Notification.Builder {
        val saved = load(row.sourceKey)
        val builder = try {
            originals[row.sourceKey]?.let { Notification.Builder.recoverBuilder(context.createPackageContext(row.packageName, 0), it.clone().apply { fullScreenIntent = null }) }
        } catch (_: RuntimeException) { null } catch (_: android.content.pm.PackageManager.NameNotFoundException) { null }
        val result = builder ?: Notification.Builder(context, channel).apply {
            setContentTitle(saved?.title ?: row.title); setContentText(saved?.text ?: row.text)
            if (saved != null) {
                setSubText(saved.subText); setColor(saved.color); setCategory(saved.category)
                when {
                    saved.messages.isNotEmpty() -> {
                        val style = if (android.os.Build.VERSION.SDK_INT >= 28) {
                            val user = Person.Builder().setName(saved.userName ?: "You").apply { readBitmap(saved.userAvatar)?.let { setIcon(Icon.createWithBitmap(it)) } }.build()
                            Notification.MessagingStyle(user)
                        } else Notification.MessagingStyle(saved.userName ?: "You")
                        style.setConversationTitle(saved.conversation)
                        if (android.os.Build.VERSION.SDK_INT >= 28) style.setGroupConversation(saved.group)
                        saved.messages.forEach { message ->
                            if (android.os.Build.VERSION.SDK_INT >= 28) {
                                val person = Person.Builder().setName(message.sender ?: saved.userName ?: "You").apply { readBitmap(message.avatar)?.let { setIcon(Icon.createWithBitmap(it)) } }.build()
                                style.addMessage(message.text, message.time, person)
                            } else style.addMessage(message.text, message.time, message.sender)
                        }; setStyle(style)
                    }
                    saved.picture != null -> setStyle(Notification.BigPictureStyle().bigPicture(readBitmap(saved.picture)))
                    saved.lines != null -> setStyle(Notification.InboxStyle().apply { saved.lines.forEach { addLine(it) } })
                    else -> setStyle(Notification.BigTextStyle().bigText(saved.bigText ?: row.text))
                }
                readBitmap(saved.largeIcon)?.let { setLargeIcon(it) }
            } else setStyle(Notification.BigTextStyle().bigText(row.text))
        }
        result.setChannelId(channel).setActions().setGroup(null).setGroupSummary(false).setTimeoutAfter(0)
            .setOngoing(false).setAutoCancel(false)
        val small = readBitmap(saved?.smallIcon)
        if (small != null) result.setSmallIcon(Icon.createWithBitmap(small)) else result.setSmallIcon(com.husarp.holdfast.R.drawable.ic_holdfast)
        return result
    }
}
