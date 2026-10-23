package app.vlauncher.helper

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import app.vlauncher.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdate(val tag: String, val apkUrl: String)

object Updater {
    private const val LATEST_URL = "https://api.github.com/repos/vaproh/vlauncher/releases/latest"
    const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /** True when [latest] is a newer version than [current]; both may carry a leading "v". */
    fun isNewer(current: String, latest: String): Boolean {
        val cur = current.trimStart('v', 'V').split('.')
        val lat = latest.trimStart('v', 'V').split('.')
        for (i in 0 until maxOf(cur.size, lat.size)) {
            val c = cur.getOrNull(i)?.toIntOrNull() ?: 0
            val l = lat.getOrNull(i)?.toIntOrNull() ?: 0
            if (l != c) return l > c
        }
        return false
    }

    fun parseRelease(json: String): AppUpdate? {
        val root = JSONObject(json)
        val tag = root.optString("tag_name")
        if (tag.isBlank()) return null
        val assets = root.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk")) {
                val url = asset.optString("browser_download_url")
                if (url.isNotBlank()) return AppUpdate(tag, url)
            }
        }
        return null
    }

    /** Network call; must run off the main thread. */
    fun checkLatest(): AppUpdate? {
        return try {
            val connection = URL(LATEST_URL).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                parseRelease(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Downloads the APK into the app cache; network call, must run off the main thread. */
    fun downloadApk(context: Context, update: AppUpdate): File? {
        return try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val file = File(dir, "vlauncher-${update.tag}.apk")
            if (file.exists() && file.length() > 0) return file
            val tmp = File(dir, "vlauncher-${update.tag}.apk.tmp")
            var ok = false
            try {
                val connection = URL(update.apkUrl).openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 15_000
                    connection.readTimeout = 30_000
                    if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
                    connection.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } finally {
                    connection.disconnect()
                }
                ok = tmp.renameTo(file)
            } finally {
                if (!ok) tmp.delete()
            }
            if (ok) file else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun installApk(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(
            context, "${BuildConfig.APPLICATION_ID}.fileprovider", apk
        )
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}