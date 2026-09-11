package com.financebrain.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.financebrain.BuildConfig
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

data class AvailableUpdate(val versionName: String, val versionCode: Int, val apkUrl: String, val notes: String)

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class Available(val update: AvailableUpdate) : UpdateState()
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState()
    data class ReadyToInstall(val update: AvailableUpdate, val file: File) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * Self-update over GitHub Releases. Every push to the repository publishes a release tagged
 * `fb-v<version>.<code>` with a `finance-brain.apk` asset; this compares the code against the
 * installed build and installs the newer one through the system package installer.
 */
class UpdateManager(private val context: Context) {

    private val apiUrl = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases?per_page=10"
    private val tagRe = Regex("""^fb-v(\d+\.\d+\.\d+)\.(\d+)$""")

    suspend fun check(): UpdateState = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "FinanceBrain/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != 200) return@withContext UpdateState.Failed("GitHub responded ${conn.responseCode}")
            val arr = JSONArray(conn.inputStream.bufferedReader().readText())
            var best: AvailableUpdate? = null
            for (i in 0 until arr.length()) {
                val r = arr.getJSONObject(i)
                if (r.optBoolean("draft")) continue
                val m = tagRe.find(r.optString("tag_name")) ?: continue
                val code = m.groupValues[2].toInt()
                if (best != null && code <= best.versionCode) continue
                val assets = r.optJSONArray("assets") ?: continue
                var url: String? = null
                for (j in 0 until assets.length()) {
                    val a = assets.getJSONObject(j)
                    if (a.optString("name") == "finance-brain.apk") url = a.optString("browser_download_url")
                }
                if (url == null) continue
                best = AvailableUpdate("${m.groupValues[1]}.$code", code, url, r.optString("body"))
            }
            val b = best
            if (b != null && b.versionCode > BuildConfig.VERSION_CODE) UpdateState.Available(b) else UpdateState.UpToDate
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Could not reach GitHub")
        }
    }

    suspend fun download(update: AvailableUpdate, onProgress: (Float) -> Unit): UpdateState = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "finance-brain-${update.versionCode}.apk")
            val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 60_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FinanceBrain/${BuildConfig.VERSION_NAME}")
            }
            if (conn.responseCode != 200) return@withContext UpdateState.Failed("Download failed (${conn.responseCode})")
            val total = conn.contentLengthLong.coerceAtLeast(1)
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            UpdateState.ReadyToInstall(update, out)
        } catch (e: Exception) {
            UpdateState.Failed(e.message ?: "Download failed")
        }
    }

    fun install(file: File) {
        val uri: Uri = FileProvider.getUriForFile(context, "com.financebrain.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission() {
        context.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
