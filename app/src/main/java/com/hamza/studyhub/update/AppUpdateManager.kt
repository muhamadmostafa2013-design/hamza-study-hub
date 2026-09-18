package com.hamza.studyhub.update

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import com.hamza.studyhub.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Free update checker backed by GitHub Releases.
 * Deliberately does NOT request REQUEST_INSTALL_PACKAGES: the final APK download/install
 * is handed to the browser/Android so Hamza Study Hub keeps a minimal permission surface.
 */
object AppUpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/muhamadmostafa2013-design/hamza-study-hub/releases/latest"

    data class Release(val version: String, val pageUrl: String, val apkUrl: String?)

    fun check(activity: Activity, silentIfCurrent: Boolean = true) {
        thread {
            val result = runCatching { fetchLatest() }
            activity.runOnUiThread {
                result.onSuccess { release ->
                    if (isNewer(release.version, BuildConfig.VERSION_NAME)) {
                        showUpdate(activity, release)
                    } else if (!silentIfCurrent) {
                        AlertDialog.Builder(activity)
                            .setTitle("✅ التطبيق محدّث")
                            .setMessage("عندك أحدث نسخة بالفعل: ${BuildConfig.VERSION_NAME}")
                            .setPositiveButton("تمام", null)
                            .show()
                    }
                }.onFailure {
                    if (!silentIfCurrent) {
                        AlertDialog.Builder(activity)
                            .setTitle("تعذر فحص التحديث")
                            .setMessage("اتأكد من الإنترنت وحاول مرة تانية.")
                            .setPositiveButton("تمام", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun fetchLatest(): Release {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7000
            readTimeout = 7000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Hamza-Study-Hub/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode !in 200..299) error("GitHub ${connection.responseCode}")
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            val page = json.getString("html_url")
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apk = asset.optString("browser_download_url")
                        break
                    }
                }
            }
            return Release(tag, page, apk)
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdate(activity: Activity, release: Release) {
        AlertDialog.Builder(activity)
            .setTitle("🎉 Neue Version verfügbar!")
            .setMessage("في تحديث جديد لـ Hamza Study Hub: v${release.version}\n\nاضغط تحديث، وهيفتح رابط النسخة الرسمية على GitHub. Android هيطلب منك تأكيد التحديث بنفسه.")
            .setNegativeButton("بعد كده", null)
            .setPositiveButton("⬇️ تحديث") { _, _ ->
                val url = release.apkUrl ?: release.pageUrl
                activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
            .show()
    }

    internal fun isNewer(remote: String, current: String): Boolean {
        fun parts(value: String) = value.removePrefix("v").substringBefore('-')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(remote)
        val b = parts(current)
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }
}
