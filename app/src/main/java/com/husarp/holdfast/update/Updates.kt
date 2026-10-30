package com.husarp.holdfast.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object Updates {
    private const val API = "https://api.github.com/repos/Husarp/holdfast/releases?per_page=20"
    const val RELEASES_PAGE = "https://github.com/Husarp/holdfast/releases"
    private const val TIMEOUT_MS = 10_000

    data class Latest(val version: String, val apkName: String, val apkUrl: String, val apkSize: Long, val page: String)

    sealed interface Phase {
        data object Idle : Phase
        data class Downloading(val percent: Int, val done: Long = 0, val total: Long = 0, val bytesPerSecond: Long = 0) : Phase
        data object NeedsPermission : Phase      // Android's "install unknown apps" screen is open
        data object Ready : Phase                // downloaded while the app was in the background; installs on return
        data object Installing : Phase           // handed to Android's installer
        data class Failed(val why: String) : Phase
    }

    data class State(
        val latest: Latest? = null,              // newest version found on GitHub (may equal the current one)
        val pageUrl: String = RELEASES_PAGE,     // the page of the release carrying the newest APK, for GITHUB
        val checking: Boolean = false,
        val message: String? = null,             // answer to a manual CHECK NOW
        val bannerHidden: Boolean = false,
        val phase: Phase = Phase.Idle,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = UpdateLogic.BannerGate()
    @Volatile private var lastCheckMs = 0L
    @Volatile private var pendingApk: File? = null
    @Volatile private var front: WeakReference<Activity>? = null

    fun enabled(ctx: Context) = ctx.getSharedPreferences("updates", 0).getBoolean("enabled", true)
    fun setEnabled(ctx: Context, enabled: Boolean) { ctx.getSharedPreferences("updates", 0).edit().putBoolean("enabled", enabled).apply() }

    fun current(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" }.getOrDefault("?")

    fun bannerVisible(s: State, current: String): Boolean = gate.visible(s.latest?.version, current)

    fun available(s: State, current: String): Boolean = s.latest != null && UpdateLogic.isNewer(s.latest.version, current)

    fun onAppStart(ctx: Context) {
        gate.onAppStart()
        _state.update { it.copy(bannerHidden = false) }
        // A permission screen can restart the process; retain its pending install.
        _state.update { if (it.phase is Phase.Failed || it.phase == Phase.Installing) it.copy(phase = Phase.Idle) else it }
        if (_state.value.phase is Phase.Downloading) return   // the process outlived the screen mid-download
        lastCheckMs = ctx.getSharedPreferences("updates", 0).getLong("lastCheck", 0)
        val cur = current(ctx)
        scope.launch {
            dir(ctx).listFiles()?.forEach { f ->
                val v = UpdateLogic.apkVersion(f.name)
                if (f.name != "pending-install" && (v == null || !UpdateLogic.isNewer(v, cur))) f.delete()   // also drops unfinished .part files
            }
        }
    }

    fun onResume(activity: Activity) {
        front = WeakReference(activity)
        val app = activity.applicationContext
        val phase = _state.value.phase
        val waiting = phase == Phase.NeedsPermission || (phase == Phase.Idle && marker(app).exists())
        when {
            waiting -> {
                val apk = pendingApk ?: runCatching { File(dir(app), marker(app).readText().trim()) }.getOrNull()
                marker(app).delete()
                if (canInstall(app) && apk != null && apk.isFile) install(app, apk)
                else fail("Holdfast isn't allowed to install apps yet. Allow \"Install unknown apps\" for Holdfast, then TRY AGAIN.")
            }
            phase == Phase.Ready -> pendingApk?.takeIf { it.isFile }?.let { install(app, it) } ?: fail("The downloaded update is gone.")
            phase == Phase.Installing -> fail("The update wasn't installed. If Android said it couldn't install it, the update " +
                "was signed with a different key than this app - get it from GitHub.")
        }
        autoCheck(app)
    }

    fun onPause() { front = null }

    fun dismissBanner() { gate.dismiss(); _state.update { it.copy(bannerHidden = true) } }

    fun autoCheck(ctx: Context) {
        if (!enabled(ctx) || _state.value.checking) return
        if (!UpdateLogic.mayCheck(System.currentTimeMillis(), lastCheckMs)) return
        check(ctx.applicationContext, manual = false)
    }

    fun checkNow(ctx: Context) {
        if (_state.value.checking) return
        if (!UpdateLogic.mayCheck(System.currentTimeMillis(), lastCheckMs)) {
            _state.update { it.copy(message = "Checked recently. Please wait up to five minutes before checking again.") }; return
        }
        check(ctx.applicationContext, manual = true)
    }

    private fun check(app: Context, manual: Boolean) {
        lastCheckMs = System.currentTimeMillis()
        app.getSharedPreferences("updates", 0).edit().putLong("lastCheck", lastCheckMs).apply()
        _state.update { it.copy(checking = true, message = if (manual) "Checking…" else it.message) }
        val cur = current(app)
        scope.launch {
            val job = scope.async { runCatching { fetchLatest() } }
            val res = withTimeoutOrNull(TIMEOUT_MS.toLong()) { job.await() }
                ?: Result.failure(SocketTimeoutException())
            res.onSuccess { (latest, page) ->
                _state.update {
                    it.copy(checking = false, latest = latest, pageUrl = page,
                        message = if (!manual) it.message
                        else if (latest != null && UpdateLogic.isNewer(latest.version, cur)) "Holdfast ${latest.version} is available."
                        else "You're up to date ($cur).")
                }
            }.onFailure { e ->
                _state.update { it.copy(checking = false, message = if (manual) describe(e, download = false) else it.message) }
            }
        }
    }

    private class HttpStatus(val code: Int) : IOException("HTTP $code")

    private fun fetchLatest(): Pair<Latest?, String> {
        val c = open(API).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (c.responseCode != 200) throw HttpStatus(c.responseCode)
            val list = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            val releases = (0 until list.length()).map { i ->
                val j = list.getJSONObject(i)
                val arr = j.optJSONArray("assets")
                val assets = (0 until (arr?.length() ?: 0)).map { k ->
                    val a = arr!!.getJSONObject(k)
                    UpdateLogic.Asset(a.getString("name"), a.getString("browser_download_url"), a.optLong("size", -1))
                }
                UpdateLogic.Release(j.optString("html_url", RELEASES_PAGE), assets, j.optBoolean("draft"), j.optBoolean("prerelease"), j.optString("tag_name"))
            }
            val (rel, a, v) = UpdateLogic.newestApk(releases) ?: return null to RELEASES_PAGE
            return Latest(v, a.name, a.url, a.size, rel.page) to rel.page
        } finally { c.disconnect() }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS; readTimeout = TIMEOUT_MS
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Holdfast")
    }

    fun update(ctx: Context) {
        val app = ctx.applicationContext
        if (_state.value.phase is Phase.Downloading) return
        val latest = _state.value.latest ?: run {
            pendingApk?.takeIf { it.isFile }?.let { install(app, it) } ?: checkNow(app)
            return
        }
        val file = File(dir(app), "Holdfast-${latest.version}.apk")
        if (file.exists() && (latest.apkSize <= 0 || file.length() == latest.apkSize)) { install(app, file); return }
        _state.update { it.copy(phase = Phase.Downloading(0)) }
        scope.launch {
            runCatching { download(latest, file) }
                .onSuccess { withContext(Dispatchers.Main) { install(app, file) } }
                .onFailure { fail(describe(it, download = true)) }
        }
    }

    private fun download(latest: Latest, file: File) {
        val part = File(file.parentFile, file.name + ".part")
        val c = open(latest.apkUrl)
        try {
            if (c.responseCode != 200) throw HttpStatus(c.responseCode)
            val total = c.contentLengthLong.takeIf { it > 0 } ?: latest.apkSize
            var done = 0L; var shown = -1
            val started = android.os.SystemClock.elapsedRealtime()
            c.inputStream.use { inp ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = inp.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        val pct = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else 0
                        if (pct != shown) { shown = pct; _state.update { it.copy(phase = Phase.Downloading(pct, done, total, done * 1000 / (android.os.SystemClock.elapsedRealtime() - started).coerceAtLeast(1))) } }
                    }
                }
            }
            if (total > 0 && done != total) throw IOException("incomplete")
            if (!part.renameTo(file)) throw IOException("rename")
        } catch (e: Throwable) {
            part.delete(); throw e
        } finally { c.disconnect() }
    }

    private fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    private fun install(app: Context, apk: File) {
        pendingApk = apk
        val act = front?.get()
        if (act == null) { _state.update { it.copy(phase = Phase.Ready) }; return }
        if (!canInstall(app)) {
            _state.update { it.copy(phase = Phase.NeedsPermission) }
            runCatching { marker(app).writeText(apk.name) }
            runCatching {
                act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")))
            }.onFailure {
                marker(app).delete()
                fail("Couldn't open Android's \"Install unknown apps\" setting. Allow it for Holdfast in Settings, then TRY AGAIN.")
            }
            return
        }
        runCatching {
            val uri = FileProvider.getUriForFile(app, "${app.packageName}.files", apk)
            act.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            _state.update { it.copy(phase = Phase.Installing) }
        }.onFailure { fail("Couldn't open Android's installer.") }
    }

    private fun fail(why: String) = _state.update { it.copy(phase = Phase.Failed(why)) }

    private fun dir(ctx: Context) = File(ctx.cacheDir, "updates").apply { mkdirs() }

    private fun marker(ctx: Context) = File(dir(ctx), "pending-install")

    private fun describe(e: Throwable, download: Boolean): String = when {
        e is UnknownHostException -> "Can't reach GitHub. Check that the phone is online - or a firewall (like AFWall) may be blocking Holdfast."
        e is SocketTimeoutException -> "GitHub didn't answer within 10 seconds. The connection may be slow or blocked."
        e is SSLException -> "Couldn't open a secure connection to GitHub. The network may be intercepting it, or the phone's date is wrong."
        e is HttpStatus && (e.code == 403 || e.code == 429) -> "GitHub's limit of checks per hour was reached. Try again in a while."
        e is HttpStatus && e.code == 404 -> if (download) "The update file is no longer on GitHub." else "No release found on GitHub."
        download && e.message == "incomplete" -> "The download stopped before it finished."
        download && (e.message == "rename" || e.message?.contains("ENOSPC") == true) -> "Couldn't save the update - is the phone's storage full?"
        download -> "The download failed - the connection to GitHub dropped."
        else -> "Couldn't check for updates - the connection to GitHub failed."
    }
}
