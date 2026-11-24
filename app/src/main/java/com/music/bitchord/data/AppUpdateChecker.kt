package com.music.bitchord.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.music.bitchord.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import java.io.File

/**
 * BitChord ships as a sideloaded APK off GitHub Releases rather than through
 * a store, so there's nothing to push an update notice on its own — this
 * polls the repo's "latest release" once per launch and compares its tag
 * against the running build. Two channels: Release (stable tags) and Dev
 * (the rolling dev-mochy-ui pre-release, a separate app).
 *
 * The update itself is also handled here: the release's `.apk` asset is
 * downloaded into the app's cache and handed to the system package installer,
 * so the whole round trip stays inside the app instead of bouncing out to a
 * browser.
 */
object AppUpdateChecker {

    data class UpdateInfo(
        val version: String,
        val releaseUrl: String,
        val apkUrl: String?,
        /** The release's own Markdown body, shown as this update's "what's new". */
        val notes: String?,
        /** The asset's file name as uploaded, for the detail sheet. */
        val fileName: String? = null,
        /** The package the APK installs: the prod app, or the separate dev app. */
        val packageName: String = PROD_PACKAGE,
    )

    /** Which release feed the updater follows. */
    enum class UpdateChannel {
        /** Stable GitHub releases (the default). */
        RELEASE,
        /** The rolling dev-mochy-ui pre-release (the separate ChordWave Dev app). */
        DEV,
    }

    private const val CACHE_SUBDIR = "updates"

    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/Rikki53/ChordWave/releases/latest"

    private const val RELEASES_LIST_URL =
        "https://api.github.com/repos/Rikki53/ChordWave/releases?per_page=30"

    private const val DEV_RELEASE_URL =
        "https://api.github.com/repos/Rikki53/ChordWave/releases/tags/dev-mochy-ui"

    /** Dev APK assets are named app-dev-<abi>-debug.apk. */
    private const val DEV_APK_PREFIX = "app-dev-"

    const val PROD_PACKAGE = "com.riki.chordwave"
    const val DEV_PACKAGE = "com.riki.chordwave.dev"

    private val json = Json { ignoreUnknownKeys = true }

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available = _available.asStateFlow()

    /** Where this update's APK download currently stands, for the dialog's progress row. */
    sealed interface DownloadState {
        data object Idle : DownloadState
        data class Downloading(val fraction: Float) : DownloadState
        data class Ready(val file: File) : DownloadState
        data class Failed(val message: String) : DownloadState
    }

    private val _download = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val download = _download.asStateFlow()

    /** Set from the UI thread when the user cancels; polled between network reads. */
    @Volatile
    private var downloadCancelled = false

    suspend fun check() = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchRelease(LATEST_RELEASE_URL) ?: return@runCatching
            val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching
            val (fileName, apkUrl) = releaseApkAsset(release) ?: return@runCatching
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            val latest = tag.removePrefix("v")
            if (isNewer(latest, BuildConfig.VERSION_NAME)) {
                _available.value = UpdateInfo(
                    version = latest,
                    releaseUrl = url,
                    apkUrl = apkUrl,
                    notes = notes,
                    fileName = fileName,
                    packageName = PROD_PACKAGE,
                )
            }
        }
    }

    /**
     * The dev channel's check: the rolling pre-release has no version in its
     * tag ("dev-mochy-ui"), so "newer" means published after this install
     * was last updated. Returns null when there is no newer dev build.
     *
     * One blind spot: installing an old dev APK file by hand after a newer
     * build was published reads as up to date (the install is newer than
     * the publish). The next publish after that fixes the reading.
     */
    suspend fun checkDev(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val release = fetchRelease(DEV_RELEASE_URL) ?: return@runCatching null
            val publishedMs = release["published_at"]?.jsonPrimitive?.contentOrNull
                ?.let { parseGithubTime(it) } ?: return@runCatching null
            val installedMs = runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
            }.getOrDefault(0L)
            if (publishedMs <= installedMs) return@runCatching null
            val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
            val (fileName, apkUrl) = devApkAsset(release) ?: return@runCatching null
            val notes = release["body"]?.jsonPrimitive?.contentOrNull
            UpdateInfo(
                // the dev APK is built from this same version name
                version = "${BuildConfig.VERSION_NAME}-dev",
                releaseUrl = url,
                apkUrl = apkUrl,
                notes = notes,
                fileName = fileName,
                packageName = DEV_PACKAGE,
            )
        }.getOrNull()
    }

    /**
     * What "Downgrade to" offers, null when there's none: the newest stable
     * release older than the installed version (an older build of the same
     * version doesn't count). Dev builds have no previous release — the dev
     * pre-release is a single rolling build.
     */
    suspend fun getPreviousRelease(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val body = fetchText(RELEASES_LIST_URL) ?: return@runCatching null
            val installed = BuildConfig.VERSION_NAME
            json.parseToJsonElement(body).jsonArray
                .mapNotNull { it as? JsonObject }
                .filter { obj ->
                    obj["draft"]?.jsonPrimitive?.booleanOrNull != true &&
                        obj["prerelease"]?.jsonPrimitive?.booleanOrNull != true
                }
                .mapNotNull { obj ->
                    val tag = obj["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val version = tag.removePrefix("v")
                    if (compareVersions(version, installed) >= 0) return@mapNotNull null
                    obj to version
                }
                .maxWithOrNull(Comparator { a, b -> compareVersions(a.second, b.second) })
                ?.let { (obj, version) ->
                    val url = obj["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                    val (fileName, apkUrl) = releaseApkAsset(obj) ?: return@runCatching null
                    val notes = obj["body"]?.jsonPrimitive?.contentOrNull
                    UpdateInfo(
                        version = version,
                        releaseUrl = url,
                        apkUrl = apkUrl,
                        notes = notes,
                        fileName = fileName,
                        packageName = PROD_PACKAGE,
                    )
                }
        }.getOrNull()
    }

    private fun fetchText(url: String): String? = runCatching {
        val request = Request.Builder().url(url).build()
        Http.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body?.string()
        }
    }.getOrNull()

    private fun fetchRelease(url: String): JsonObject? {
        val body = fetchText(url) ?: return null
        return json.parseToJsonElement(body) as? JsonObject
    }

    /** GitHub's "2026-11-24T15:30:00Z" timestamps, as epoch millis. */
    private fun parseGithubTime(raw: String): Long? =
        runCatching { java.time.Instant.parse(raw).toEpochMilli() }.getOrNull()

    /**
     * Wipes any APK left over from a previous run. Called once at cold start
     * so a downloaded update is only ever "Install Now" for the session that
     * downloaded it — the next launch starts clean rather than trying to work
     * out whether a leftover file is still good.
     */
    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        File(context.cacheDir, CACHE_SUBDIR).listFiles()?.forEach { it.delete() }
    }

    /**
     * A release's uploaded `.apk` assets with the given file-name prefix, as
     * (file name as uploaded, download url). One release carries the phone
     * app and the TV app, each as a universal APK plus per-ABI splits:
     *
     *     ChordWave-v1.8.apk     ChordWave-v1.8_arm64-v8a.apk     ...
     *     ChordWaveTV-v1.8.apk   ChordWaveTV-v1.8_arm64-v8a.apk   ...
     *
     * so this app's APKs are the ones named with its own
     * [BuildConfig.UPDATE_APK_PREFIX] — "ChordWave-" never matches a TV file,
     * since the prefix ends at the hyphen.
     */
    private fun apkAssets(release: JsonObject, prefix: String): List<Pair<String, String>> = runCatching {
        release["assets"]?.jsonArray
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { asset ->
                val name = asset["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val url = asset["browser_download_url"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val ours = name.startsWith(prefix, ignoreCase = true) &&
                    name.endsWith(".apk", ignoreCase = true) &&
                    asset["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
                if (ours) name to url else null
            }
            .orEmpty()
    }.getOrDefault(emptyList())

    /**
     * The release APK to install: the universal build (no `_ABI` suffix)
     * first, as it installs anywhere; failing that, the split for this
     * device's ABI. A release with neither leaves [UpdateInfo.apkUrl] null
     * and the UI falls back to opening the releases page.
     */
    private fun releaseApkAsset(release: JsonObject): Pair<String, String>? {
        val apks = apkAssets(release, BuildConfig.UPDATE_APK_PREFIX)
        val base = { name: String -> name.removeSuffix(".apk").removeSuffix(".APK") }
        return apks.firstOrNull { (name, _) -> '_' !in base(name) }
            ?: Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
                apks.firstOrNull { (name, _) -> base(name).endsWith("_$abi") }
            }
    }

    /**
     * The dev APK to install: the split for this device's ABI first
     * (app-dev-<abi>-debug.apk, the ABI sits mid-name), anything dev as a
     * fallback. The universal dev build is last — it carries every ABI.
     */
    private fun devApkAsset(release: JsonObject): Pair<String, String>? {
        val apks = apkAssets(release, DEV_APK_PREFIX)
        return Build.SUPPORTED_ABIS.firstNotNullOfOrNull { abi ->
            apks.firstOrNull { (name, _) -> "_${abi}-" in name }
        } ?: apks.firstOrNull()
    }

    /**
     * Streams the current update's APK into the app cache, reporting progress
     * through [download]. A finished file survives a cancelled dialog: until
     * the state is reset, "Install Now" comes straight back without a second
     * download.
     */
    suspend fun downloadApk(context: Context): Unit = withContext(Dispatchers.IO) {
        val info = _available.value ?: return@withContext
        val url = info.apkUrl ?: return@withContext
        downloadCancelled = false
        _download.value = DownloadState.Downloading(0f)

        runCatching {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            // Drop anything left over from an earlier attempt.
            dir.listFiles()?.forEach { it.delete() }
            val target = File(dir, "bitchord-${info.version}.apk")

            val request = Request.Builder().url(url).build()
            Http.client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
                val body = response.body ?: error("Empty download body")
                val total = body.contentLength().takeIf { it > 0 }

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var readTotal = 0L
                        while (true) {
                            if (downloadCancelled) {
                                _download.value = DownloadState.Idle
                                return@withContext
                            }
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            total?.let {
                                _download.value =
                                    DownloadState.Downloading((readTotal.toFloat() / it).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            _download.value = DownloadState.Ready(target)
        }.onFailure { error ->
            _download.value = if (downloadCancelled) {
                DownloadState.Idle
            } else {
                DownloadState.Failed(error.message ?: "Download failed")
            }
        }
    }

    /** Stops an in-flight download; the next read loop sees this and bails. */
    fun cancelDownload() {
        downloadCancelled = true
    }

    /** Back to square one after a failure, so the dialog offers Download again. */
    fun resetDownload() {
        _download.value = DownloadState.Idle
    }

    /**
     * Hands a downloaded APK to the system installer.
     *
     * Sideloaded apps need the user's blessing per app ("install unknown apps");
     * without it the installer intent silently does nothing on most ROMs, so
     * the user is sent to that one switch first and taps Install again after.
     * Some TV builds have no such settings screen; there the installer is
     * launched anyway and shows its own "not allowed" prompt with a way in.
     */
    fun installApk(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val opened = runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        .setData(Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.isSuccess
            if (opened) return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        context.startActivity(
            Intent(Intent.ACTION_INSTALL_PACKAGE)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true)
                .addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_ACTIVITY_NEW_TASK,
                ),
        )
    }

    /** A version split into its numeric dotted parts and whether it carries a "-suffix" (e.g. "-beta2"). */
    private data class ParsedVersion(val parts: List<Int>, val isPreRelease: Boolean)

    private fun parseVersion(raw: String): ParsedVersion {
        val dash = raw.indexOf('-')
        val base = if (dash >= 0) raw.substring(0, dash) else raw
        return ParsedVersion(base.split(".").map { it.toIntOrNull() ?: 0 }, dash >= 0)
    }

    /** Numeric, dot-separated comparison — "1.10" outranks "1.9" — suffixes ignored. */
    private fun compareVersions(a: String, b: String): Int {
        val ap = parseVersion(a).parts
        val bp = parseVersion(b).parts
        for (i in 0 until maxOf(ap.size, bp.size)) {
            val x = ap.getOrElse(i) { 0 }
            val y = bp.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    /**
     * Numeric, dot-separated comparison — "1.10" outranks "1.9" — with one
     * extra rule: a "-betaN" build (see the debug build type's
     * `versionNameSuffix` in app/build.gradle.kts) is treated as older than a
     * plain release of the same numbers, since the beta by definition predates
     * the tag it was testing toward. Without this, a beta and the release it
     * matches compare equal and testers never get nudged onto the real build.
     */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        for (i in 0 until maxOf(l.parts.size, c.parts.size)) {
            val a = l.parts.getOrElse(i) { 0 }
            val b = c.parts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return c.isPreRelease && !l.isPreRelease
    }
}
