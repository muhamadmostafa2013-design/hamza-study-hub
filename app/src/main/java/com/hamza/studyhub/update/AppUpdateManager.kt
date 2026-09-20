package com.hamza.studyhub.update

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.hamza.studyhub.BuildConfig
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.concurrent.thread

/**
 * In-app updater backed by the public GitHub Releases feed.
 *
 * Flow:
 * 1) check the latest release,
 * 2) download the APK directly into this app's cache,
 * 3) verify GitHub SHA-256 (when present), package id, version and signing certificate,
 * 4) hand the verified APK to Android's package installer.
 *
 * Android still owns the final Install/Update confirmation. No silent installation is attempted.
 */
object AppUpdateManager {
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/muhamadmostafa2013-design/hamza-study-hub/releases/latest"
    private const val PREFS = "hamza_app_update"
    private const val KEY_PENDING_APK = "pending_apk"

    data class Release(
        val version: String,
        val pageUrl: String,
        val apkUrl: String?,
        val sha256: String?
    )

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
                }.onFailure { error ->
                    if (!silentIfCurrent) {
                        showError(
                            activity,
                            "تعذر فحص التحديث",
                            error.message ?: "اتأكد من الإنترنت وحاول مرة تانية."
                        )
                    }
                }
            }
        }
    }

    /**
     * Called from HomeActivity.onResume().
     * After the user grants "Install unknown apps" once, returning to the app opens
     * Android's installer automatically without asking them to find the APK manually.
     */
    fun resumePendingInstall(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
        val path = prefs.getString(KEY_PENDING_APK, null) ?: return
        val apk = File(path)
        if (!apk.exists()) {
            prefs.edit().remove(KEY_PENDING_APK).apply()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            return
        }

        prefs.edit().remove(KEY_PENDING_APK).apply()
        launchInstaller(activity, apk)
    }

    private fun fetchLatest(): Release {
        val connection = (URL(LATEST_RELEASE_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 7_000
            readTimeout = 7_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Hamza-Study-Hub/${BuildConfig.VERSION_NAME}")
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("GitHub ${connection.responseCode}")
            }

            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = json.optString("tag_name").removePrefix("v")
            val page = json.getString("html_url")
            val assets = json.optJSONArray("assets")
            var apkUrl: String? = null
            var digest: String? = null

            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (!asset.optString("name").endsWith(".apk", ignoreCase = true)) continue

                    apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() }
                    digest = asset.optString("digest")
                        .removePrefix("sha256:")
                        .lowercase()
                        .takeIf { it.matches(Regex("[0-9a-f]{64}")) }
                    break
                }
            }

            return Release(tag, page, apkUrl, digest)
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdate(activity: Activity, release: Release) {
        AlertDialog.Builder(activity)
            .setTitle("🎉 تحديث جديد v${release.version}")
            .setMessage(
                "اضغط «تحديث الآن». التطبيق هيحمّل النسخة الجديدة بنفسه ويتأكد من التوقيع، " +
                    "وبعدها Android هيعرض زر Install/Update فقط."
            )
            .setNegativeButton("بعد كده", null)
            .setPositiveButton("⬇️ تحديث الآن") { _, _ ->
                if (release.apkUrl.isNullOrBlank()) {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl)))
                } else {
                    downloadAndInstall(activity, release)
                }
            }
            .show()
    }

    private fun downloadAndInstall(activity: Activity, release: Release) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("⬇️ جاري تحميل التحديث")
            .setMessage("بدأ التحميل…")
            .setCancelable(false)
            .create()
        dialog.show()

        thread {
            val result = runCatching {
                val updateDir = File(activity.cacheDir, "updates").apply { mkdirs() }
                updateDir.listFiles()?.filter { it.extension.equals("apk", true) || it.name.endsWith(".part") }
                    ?.forEach { it.delete() }

                val finalFile = File(updateDir, "Hamza-Study-Hub-v${release.version}.apk")
                val tempFile = File(updateDir, finalFile.name + ".part")

                downloadApk(release.apkUrl!!, tempFile) { percent ->
                    activity.runOnUiThread {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            dialog.setMessage(
                                if (percent >= 0) "جاري التحميل… $percent%"
                                else "جاري التحميل…"
                            )
                        }
                    }
                }

                val actualSha = sha256(tempFile)
                release.sha256?.let { expected ->
                    check(actualSha.equals(expected, ignoreCase = true)) {
                        "فشل التحقق من SHA-256 للنسخة الجديدة"
                    }
                }

                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }

                verifyApk(activity, finalFile, release.version)
                finalFile
            }

            activity.runOnUiThread {
                if (dialog.isShowing) dialog.dismiss()
                result.onSuccess { apk ->
                    requestInstallPermissionOrLaunch(activity, apk)
                }.onFailure { error ->
                    showError(
                        activity,
                        "تعذر تثبيت التحديث",
                        error.message ?: "حصل خطأ أثناء تنزيل أو فحص النسخة."
                    )
                }
            }
        }
    }

    private fun downloadApk(
        url: String,
        target: File,
        onProgress: (Int) -> Unit
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/vnd.android.package-archive, application/octet-stream, */*")
            setRequestProperty("User-Agent", "Hamza-Study-Hub/${BuildConfig.VERSION_NAME}")
        }

        try {
            if (connection.responseCode !in 200..299) {
                error("Download HTTP ${connection.responseCode}")
            }

            val total = connection.contentLengthLong
            var downloaded = 0L
            var lastPercent = -1

            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count

                        val percent = if (total > 0) {
                            ((downloaded * 100L) / total).toInt().coerceIn(0, 100)
                        } else {
                            -1
                        }

                        if (percent < 0 || percent >= lastPercent + 5 || percent == 100) {
                            lastPercent = percent
                            onProgress(percent)
                        }
                    }
                }
            }

            check(target.length() > 0) { "ملف التحديث اتنزّل فاضي" }
        } finally {
            connection.disconnect()
        }
    }

    private fun verifyApk(activity: Activity, apk: File, expectedVersion: String) {
        val pm = activity.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: error("Android مش قادر يقرأ ملف التحديث")

        check(archive.packageName == activity.packageName) {
            "اسم الحزمة في التحديث غير صحيح"
        }

        val archiveVersion = archive.versionName.orEmpty().removePrefix("v")
        check(archiveVersion == expectedVersion.removePrefix("v")) {
            "رقم إصدار APK لا يطابق الإصدار المنشور"
        }

        val installed = pm.getPackageInfo(activity.packageName, flags)
        check(versionCode(archive) > versionCode(installed)) {
            "النسخة المحمّلة ليست أحدث من النسخة المثبتة"
        }

        val archiveCerts = certificateDigests(archive)
        val installedCerts = certificateDigests(installed)
        check(archiveCerts.isNotEmpty() && archiveCerts == installedCerts) {
            "توقيع APK لا يطابق توقيع Hamza Study Hub الحالي"
        }
    }

    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }

    private fun certificateDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.toList().orEmpty()
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList().orEmpty()
        }
        return signatures.map { signature ->
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE * 4)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun requestInstallPermissionOrLaunch(activity: Activity, apk: File) {
        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_APK, apk.absolutePath)
            .apply()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !activity.packageManager.canRequestPackageInstalls()
        ) {
            AlertDialog.Builder(activity)
                .setTitle("🔐 إعداد مرة واحدة فقط")
                .setMessage(
                    "فعّل «السماح من هذا المصدر» لـ Hamza Study Hub. " +
                        "بعد ما ترجع للتطبيق، شاشة التثبيت هتفتح تلقائيًا."
                )
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("السماح") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:${activity.packageName}")
                    )
                    activity.startActivity(intent)
                }
                .show()
            return
        }

        activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
            .edit()
            .remove(KEY_PENDING_APK)
            .apply()
        launchInstaller(activity, apk)
    }

    private fun launchInstaller(activity: Activity, apk: File) {
        val uri = FileProvider.getUriForFile(
            activity,
            "${activity.packageName}.files",
            apk
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Hamza Study Hub update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        runCatching { activity.startActivity(intent) }
            .onFailure {
                showError(
                    activity,
                    "تعذر فتح شاشة التثبيت",
                    "Android مش قادر يفتح مثبت التطبيقات على الجهاز ده."
                )
            }
    }

    private fun showError(activity: Activity, title: String, message: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("تمام", null)
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
