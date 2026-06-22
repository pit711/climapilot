package com.climapilot.free

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * EN: A small self-updater for the **GitHub / sideload build only**. It checks the project's GitHub
 *     Releases for a newer APK, downloads it, verifies it was signed with the same key as the running
 *     app, and hands it to the system package installer. A fully silent update is impossible for a
 *     normally-installed app — Android always shows its install confirmation — so this is a
 *     "check → one-tap download → confirm install" flow, the standard for F-Droid-style distribution.
 *
 *     ⚠️ This must NOT ship in the Google Play (paid) variant — Play policy forbids apps updating
 *     themselves outside the Store. The Play build is a separate project, so it simply never includes
 *     this file.
 *
 * DE: Ein kleiner Selbst-Updater **nur für den GitHub-/Sideload-Build**. Er prüft die GitHub-Releases des
 *     Projekts auf ein neueres APK, lädt es herunter, verifiziert, dass es mit demselben Schlüssel wie
 *     die laufende App signiert ist, und übergibt es dem System-Installer. Ein vollständig stiller
 *     Update ist bei einer normal installierten App nicht möglich — Android zeigt immer seine
 *     Installations-Bestätigung — daher ist dies ein „Prüfen → ein Tipp zum Laden → Installation
 *     bestätigen"-Ablauf, der Standard für F-Droid-artige Verteilung.
 *
 *     ⚠️ Dies darf NICHT in die Google-Play-(Paid-)Variante gelangen — die Play-Richtlinie verbietet
 *     Selbst-Updates außerhalb des Stores. Der Play-Build ist ein eigenes Projekt und enthält diese
 *     Datei daher schlicht nicht.
 */
object UpdateChecker {
    // EN: The public GitHub repo the free build is released from. DE: Das öffentliche GitHub-Repo, aus dem der Free-Build veröffentlicht wird.
    private const val API_URL = "https://api.github.com/repos/pit711/climapilot/releases/latest"
    // EN: SHA-256 of the signing certificate every GitHub release uses (the project debug key). A
    //     downloaded APK whose signer differs is refused — a guard against a tampered/MITM'd or
    //     wrong-key file (which would fail to install anyway). DE: SHA-256 des Signatur-Zertifikats, das
    //     jedes GitHub-Release nutzt (der Projekt-Debug-Key). Ein geladenes APK mit abweichendem Signierer
    //     wird abgelehnt — Schutz gegen eine manipulierte/MITM- oder Falsch-Key-Datei (die ohnehin nicht
    //     installierbar wäre).
    private const val EXPECTED_CERT_SHA256 =
        "6ca6ec754a57b41e4186f358315766ea85802827c055aa6a1e9639910bba2f09"

    /** EN: A newer release found on GitHub. DE: Ein neueres auf GitHub gefundenes Release. */
    data class Release(
        val versionName: String,
        val tag: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    /** EN: Outcome of a check. DE: Ergebnis einer Prüfung. */
    sealed interface CheckResult {
        data class Available(val release: Release) : CheckResult
        object UpToDate : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /** EN: The running app's versionName (e.g. "0.6"). DE: versionName der laufenden App (z. B. „0.6"). */
    fun installedVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
            .getOrNull().orEmpty()

    /**
     * EN: Query GitHub for the latest release and decide whether it is newer than what's installed.
     * DE: GitHub nach dem neuesten Release fragen und entscheiden, ob es neuer als das Installierte ist.
     */
    suspend fun check(ctx: Context): CheckResult = withContext(Dispatchers.IO) {
        val release = runCatching { fetchLatest() }
            .getOrElse { return@withContext CheckResult.Failed(it.message ?: "network error") }
            ?: return@withContext CheckResult.Failed("no release/apk found")
        if (isNewer(release.versionName, installedVersion(ctx))) CheckResult.Available(release)
        else CheckResult.UpToDate
    }

    /** EN: Raw GitHub API call → parsed [Release], or null if there's no .apk asset. DE: Roher GitHub-API-Aufruf → geparstes [Release], oder null, wenn kein .apk-Asset existiert. */
    private fun fetchLatest(): Release? {
        val conn = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            // EN: GitHub requires a User-Agent or returns 403. DE: GitHub verlangt einen User-Agent, sonst 403.
            setRequestProperty("User-Agent", "ClimaPilot-Updater")
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            if (conn.responseCode != 200) throw IllegalStateException("HTTP ${conn.responseCode}")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name").ifBlank { return null }
            val notes = o.optString("body", "")
            val assets = o.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (name.endsWith(".apk", ignoreCase = true)) {
                    val url = a.optString("browser_download_url").ifBlank { return null }
                    return Release(
                        versionName = tag.removePrefix("v").trim(),
                        tag = tag,
                        notes = notes,
                        apkUrl = url,
                        sizeBytes = a.optLong("size", 0L),
                    )
                }
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    /**
     * EN: Compare dotted versions numerically (e.g. "0.10" > "0.9", "1.0" > "0.9.5"). Returns true when
     *     [remote] is strictly newer than [local]. Unparseable parts are treated as 0.
     * DE: Punkt-Versionen numerisch vergleichen (z. B. „0.10" > „0.9", „1.0" > „0.9.5"). Liefert true,
     *     wenn [remote] strikt neuer als [local] ist. Nicht parsbare Teile werden als 0 behandelt.
     */
    fun isNewer(remote: String, local: String): Boolean {
        if (local.isBlank()) return remote.isNotBlank()
        val r = remote.split('.', '-', '_').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        val l = local.split('.', '-', '_').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /**
     * EN: Download the release APK into app-specific external storage, reporting 0–100 % progress.
     *     Old downloads are cleared first. Returns the file, or null on failure.
     * DE: Das Release-APK in den app-spezifischen externen Speicher laden und 0–100 % Fortschritt melden.
     *     Alte Downloads werden zuvor entfernt. Liefert die Datei, oder null bei Fehler.
     */
    suspend fun download(ctx: Context, release: Release, onProgress: (Int) -> Unit): File? =
        withContext(Dispatchers.IO) {
            val dir = File(ctx.getExternalFilesDir(null), "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "climapilot-${release.versionName}.apk")
            val conn = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
                setRequestProperty("User-Agent", "ClimaPilot-Updater")
                instanceFollowRedirects = true
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            try {
                if (conn.responseCode !in 200..299) return@withContext null
                val total = release.sizeBytes.takeIf { it > 0 } ?: conn.contentLengthLong
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read: Int
                        var done = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } >= 0) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = ((done * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                            }
                        }
                    }
                }
                file
            } catch (_: Exception) {
                file.delete()
                null
            } finally {
                conn.disconnect()
            }
        }

    /**
     * EN: Verify the downloaded APK is our package and was signed with [EXPECTED_CERT_SHA256] before we
     *     ask the system to install it. DE: Vor der Installation prüfen, dass das geladene APK unser Paket
     *     ist und mit [EXPECTED_CERT_SHA256] signiert wurde.
     */
    fun verifyApk(ctx: Context, file: File): Boolean {
        val pm = ctx.packageManager
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = pm.getPackageArchiveInfo(file.path, flags) ?: return false
        if (info.packageName != ctx.packageName) return false
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION") info.signatures
        } ?: return false
        val md = MessageDigest.getInstance("SHA-256")
        return signatures.any { sig ->
            md.digest(sig.toByteArray()).joinToString("") { "%02x".format(it) }
                .equals(EXPECTED_CERT_SHA256, ignoreCase = true)
        }
    }

    /** EN: Whether the user has allowed this app to install packages. DE: Ob der Nutzer dieser App das Installieren von Paketen erlaubt hat. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ctx.packageManager.canRequestPackageInstalls()

    /** EN: Open the "install unknown apps" settings for this app. DE: Die „Unbekannte Apps installieren"-Einstellung für diese App öffnen. */
    fun requestInstallPermission(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** EN: Launch the system installer for the downloaded [file]. DE: Den System-Installer für die geladene [file] starten. */
    fun installApk(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { ctx.startActivity(intent) }
    }
}
