package com.hamza.studyhub.webuntis

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri

data class WebUntisConfig(
    val server: String,
    val school: String,
    val user: String,
    val secret: String,
    val schoolNumber: String?
)

object WebUntisConfigStore {
    private const val PREFS = "webuntis_sync"
    private const val KEY_QR = "qr_uri"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_HOMEWORK_COUNT = "homework_count"
    private const val KEY_TIMETABLE_COUNT = "timetable_count"

    fun parseQr(raw: String): WebUntisConfig? {
        val value = raw.trim()
        if (!value.startsWith("untis://setschool", ignoreCase = true)) return null

        val uri = runCatching { value.toUri() }.getOrNull() ?: return null
        val serverRaw = uri.getQueryParameter("url").orEmpty()
        val school = uri.getQueryParameter("school").orEmpty()
        val user = uri.getQueryParameter("user").orEmpty()
        val secret = uri.getQueryParameter("key").orEmpty()
        val schoolNumber = uri.getQueryParameter("schoolNumber")

        if (serverRaw.isBlank() || school.isBlank() || user.isBlank() || secret.isBlank()) return null

        val server = serverRaw
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .trim()

        if (server.isBlank()) return null

        return WebUntisConfig(
            server = server,
            school = school,
            user = user,
            secret = secret,
            schoolNumber = schoolNumber
        )
    }

    fun save(context: Context, qrUri: String): Boolean {
        if (parseQr(qrUri) == null) return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_QR, qrUri.trim())
            remove(KEY_LAST_ERROR)
        }
        return true
    }

    fun load(context: Context): WebUntisConfig? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_QR, null)
            ?: return null
        return parseQr(raw)
    }

    fun isConfigured(context: Context): Boolean = load(context) != null

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { clear() }
    }

    fun saveSyncSuccess(context: Context, atMillis: Long = System.currentTimeMillis()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SYNC, atMillis)
            remove(KEY_LAST_ERROR)
        }
    }

    fun saveSyncError(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_ERROR, message.take(500))
        }
    }

    fun saveCounts(context: Context, homeworkCount: Int? = null, timetableCount: Int? = null) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            homeworkCount?.let { putInt(KEY_HOMEWORK_COUNT, it) }
            timetableCount?.let { putInt(KEY_TIMETABLE_COUNT, it) }
        }
    }

    fun lastHomeworkCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_HOMEWORK_COUNT, -1)

    fun lastTimetableCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_TIMETABLE_COUNT, -1)

    fun lastSync(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_ERROR, null)
}
