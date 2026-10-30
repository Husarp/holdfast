package com.husarp.holdfast.update

object UpdateLogic {
    const val MIN_CHECK_GAP_MS = 5 * 60_000L

    data class Asset(val name: String, val url: String, val size: Long)

    fun compare(a: String, b: String): Int {
        val x = parts(a); val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val c = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun parts(v: String): List<Int> =
        v.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
            .split('.').map { p -> p.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }

    fun apkVersion(name: String): String? {
        if (!name.endsWith(".apk", ignoreCase = true)) return null
        return Regex("""\d+(?:\.\d+)+""").findAll(name.dropLast(4)).lastOrNull()?.value
    }

    fun pickApk(assets: List<Asset>): Pair<Asset, String>? =
        assets.mapNotNull { a -> apkVersion(a.name)?.let { a to it } }
            .maxWithOrNull { p, q -> compare(p.second, q.second) }

    data class Release(val page: String, val assets: List<Asset>, val draft: Boolean = false, val prerelease: Boolean = false, val tag: String? = null)

    fun newestApk(releases: List<Release>): Triple<Release, Asset, String>? =
        releases.filter { !it.draft && !it.prerelease }
            .mapNotNull { r -> (pickApk(r.assets) ?: r.tag?.let { tag ->
                Regex("""\d+(?:\.\d+)+""").find(tag)?.value?.let { version ->
                    r.assets.firstOrNull { it.name.endsWith(".apk", true) }?.let { it to version }
                }
            })?.let { (a, v) -> Triple(r, a, v) } }
            .maxWithOrNull { p, q -> compare(p.third, q.third) }

    fun mayCheck(nowMs: Long, lastMs: Long, gapMs: Long = MIN_CHECK_GAP_MS): Boolean =
        lastMs == 0L || nowMs - lastMs >= gapMs || nowMs < lastMs

    class BannerGate {
        var dismissed = false
            private set

        fun dismiss() { dismissed = true }

        fun onAppStart() { dismissed = false }

        fun visible(latest: String?, current: String): Boolean =
            !dismissed && latest != null && isNewer(latest, current)
    }
}
