package com.husarp.holdfast.data

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.first

data class Backup(
    val version: Int = 1,
    val settings: Settings,
    val profiles: List<Profile>,
    val pinned: List<PinnedApp>,
    val rules: List<MuteRule>,
    val quiet: List<QuietSchedule>,
    val options: List<AppOptions>,
    val history: List<NotificationEvent>? = null,
    val types: List<TypePolicy> = emptyList(),
)

object BackupFormat {
    fun encode(backup: Backup): String = Gson().toJson(backup)
    fun decode(text: String): Backup = try {
        val root = JsonParser.parseString(text).asJsonObject
        require(root.get("version").asInt == 1) { "Unsupported backup version" }
        listOf("settings", "profiles", "pinned", "rules", "quiet", "options").forEach { require(root.has(it) && !root[it].isJsonNull) }
        fun fields(value: com.google.gson.JsonObject, vararg names: String) {
            names.forEach { require(value.has(it) && !value[it].isJsonNull) { "Missing field: $it" } }
        }
        fields(root["settings"].asJsonObject, "id", "activeProfileId", "returnDelayMinutes")
        mapOf(
            "profiles" to listOf("id", "name"),
            "pinned" to listOf("packageName", "enabled", "profileId"),
            "rules" to listOf("id", "packageName", "profileId", "kind", "value"),
            "quiet" to listOf("id", "profileId", "packageName", "days", "startMinute", "endMinute"),
            "options" to listOf("packageName", "profileId", "vipSenders", "digestOnly", "digestTimes"),
            "history" to listOf("id", "notificationId", "packageName", "appLabel", "title", "text", "capturedAt"),
        ).forEach { (key, names) ->
            if (root.has(key) && !root[key].isJsonNull) root[key].asJsonArray.forEach { fields(it.asJsonObject, *names.toTypedArray()) }
        }
        if (!root.has("types")) root.add("types", com.google.gson.JsonArray())
        root["options"].asJsonArray.forEach { element ->
            val option = element.asJsonObject
            if (!option.has("buttons")) option.addProperty("buttons", "snooze:60|dismiss|reply")
        }
        val b = Gson().fromJson(root, Backup::class.java)
        b.settings.copy()
        require(b.profiles.isNotEmpty() && b.profiles.map { it.id }.distinct().size == b.profiles.size)
        require(b.profiles.all { it.id > 0 && it.name.isNotBlank() })
        val ids = b.profiles.map { it.id }
        require(b.settings.activeProfileId in ids)
        b.pinned.forEach { require(it.packageName.isNotBlank() && it.profileId in ids) }
        require(b.rules.map { it.id }.distinct().size == b.rules.size)
        require(b.quiet.map { it.id }.distinct().size == b.quiet.size)
        require(b.pinned.map { Triple(it.packageName, it.profileId, it.userSerial) }.distinct().size == b.pinned.size)
        require(b.options.map { it.packageName to it.profileId }.distinct().size == b.options.size)
        b.rules.forEach { it.copy(); require(it.packageName.isNotBlank() && it.profileId in ids) }
        b.quiet.forEach { it.copy(); require(it.profileId in ids) }
        b.options.forEach { it.copy(); require(it.packageName.isNotBlank() && it.profileId in ids) }
        b.types.forEach { it.copy(); require(it.profileId in ids && it.packageName.isNotBlank()); require(it.buttons == null || it.buttons.isEmpty() || it.buttons.split('|').all(::validButton)) }
        b.history?.forEach { require(it.packageName.isNotBlank() && it.capturedAt >= 0) }
        b
    } catch (e: Exception) { throw IllegalArgumentException("Invalid backup: ${e.message ?: "malformed JSON"}", e) }
}

class BackupStore(private val database: HoldfastDatabase) {
    suspend fun export(history: Boolean): String {
        val d = database.dao()
        return database.withTransaction {
            BackupFormat.encode(Backup(settings = d.observeSettings().first() ?: Settings(),
                profiles = d.observeProfiles().first().ifEmpty { listOf(Profile(1, "Work")) },
                pinned = d.observePinnedApps().first(), rules = d.rules(), quiet = d.observeQuiet().first(),
                options = d.observeOptions().first(), types = d.types(), history = if (history) d.observeHistory().first() else null))
        }
    }
    suspend fun replace(b: Backup) {
        database.withTransaction {
            val d = database.dao()
            d.clearTypes(); d.clearPinned(); d.clearRules(); d.clearQuiet(); d.clearOptions(); d.clearProfiles()
            b.profiles.forEach { d.saveProfile(it) }
            b.pinned.forEach { d.savePinnedApp(it) }
            b.rules.forEach { d.addRule(it) }
            b.quiet.forEach { d.addQuiet(it) }
            b.types.forEach { d.saveType(it) }
            b.options.forEach { d.saveOptions(it) }
            d.saveSettings(b.settings)
            b.history?.let { rows -> d.clearEvents(); rows.forEach { d.saveEvent(it.copy(id = 0, notificationId = -1)) } }
        }
    }
}
