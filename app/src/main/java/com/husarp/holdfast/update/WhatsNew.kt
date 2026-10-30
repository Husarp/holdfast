package com.husarp.holdfast.update

import android.content.Context
import com.husarp.holdfast.R

object WhatsNew {
    data class Item(val text: String, val path: String? = null)
    data class Version(val version: String, val items: List<Item>, val fixes: List<Item> = emptyList())

    enum class Action { SHOW, NONE, MARK_SEEN }

    private const val PREFS = "updates"
    private const val KEY_SEEN = "whatsNewSeen"

    fun parse(text: String): List<Version> {
        val out = ArrayList<Version>()
        var name: String? = null
        var items = ArrayList<Item>()
        var fixes = ArrayList<Item>()
        var inFixes = false
        fun close() { name?.let { out += Version(it, items, fixes) }; items = ArrayList(); fixes = ArrayList(); inFixes = false }
        for (raw in text.lines()) {
            val line = raw.trim()
            when {
                line.startsWith("### ") && name != null -> inFixes = true
                line.startsWith("## ") -> { close(); name = line.removePrefix("## ").trim() }
                line.startsWith("- ") && name != null -> {
                    val body = line.removePrefix("- ").replace("**", "").trim()
                    val at = body.lastIndexOf(" @ ")
                    (if (inFixes) fixes else items) += if (at > 0) Item(body.substring(0, at).trim(), body.substring(at + 3).trim()) else Item(body)
                }
            }
        }
        close()
        return out.filter { it.items.isNotEmpty() || it.fixes.isNotEmpty() }.sortedWith { a, b -> UpdateLogic.compare(b.version, a.version) }
    }

    fun upTo(all: List<Version>, current: String): List<Version> =
        all.filter { UpdateLogic.compare(it.version, current) <= 0 }.sortedWith { a, b -> UpdateLogic.compare(b.version, a.version) }

    fun unseen(all: List<Version>, lastSeen: String?, current: String): List<Version> =
        upTo(all, current).filter { lastSeen == null || UpdateLogic.isNewer(it.version, lastSeen) }

    fun decide(lastSeen: String?, current: String, wasUpdated: Boolean): Action = when {
        lastSeen == null -> if (wasUpdated) Action.SHOW else Action.MARK_SEEN
        UpdateLogic.isNewer(current, lastSeen) -> Action.SHOW
        else -> Action.NONE
    }

    fun changes(n: Int): String = if (n == 1) "1 change" else "$n changes"

    fun load(ctx: Context): List<Version> =
        runCatching { ctx.resources.openRawResource(R.raw.whats_new).bufferedReader().use { parse(it.readText()) } }.getOrDefault(emptyList())

    fun lastSeen(ctx: Context): String? = ctx.getSharedPreferences(PREFS, 0).getString(KEY_SEEN, null)

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun listen(l: () -> Unit) { listeners.add(l) }
    fun unlisten(l: () -> Unit) { listeners.remove(l) }

    fun markSeen(ctx: Context, current: String = Updates.current(ctx)) {
        ctx.getSharedPreferences(PREFS, 0).edit().putString(KEY_SEEN, current).apply()
        listeners.forEach { it() }
    }

    private fun wasUpdated(ctx: Context): Boolean = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        pi.lastUpdateTime - pi.firstInstallTime > 10_000L
    }.getOrDefault(false)

    fun cardPending(ctx: Context): Boolean {
        val cur = Updates.current(ctx)
        if (cur == "?") return false
        val seen = lastSeen(ctx)
        return when (decide(seen, cur, wasUpdated(ctx))) {
            Action.NONE -> false
            Action.MARK_SEEN -> { markSeen(ctx, cur); false }
            Action.SHOW -> if (unseen(load(ctx), seen, cur).isEmpty()) { markSeen(ctx, cur); false } else true
        }
    }
}
