package com.hamza.studyhub.webuntis

import android.content.Context
import androidx.core.content.edit
import androidx.core.net.toUri
import java.net.URI
import java.util.Locale

data class WebUntisConfig(
    val server: String,
    val school: String,
    val user: String,
    val secret: String,
    val schoolNumber: String?
)

object WebUntisConfigStore {
    private const val PREFS = "webuntis_sync"
    private const val KEY_QR = "qr_uri" // legacy plaintext key, removed on migration
    private const val KEY_QR_ENCRYPTED = "qr_uri_encrypted_v1"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_LAST_ERROR = "last_error"

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

        val server = validatedHost(serverRaw) ?: return null

        return WebUntisConfig(
            server = server,
            school = school,
            user = user,
            secret = secret,
            schoolNumber = schoolNumber
        )
    }

    /**
     * Encrypt the full QR configuration (including its TOTP seed) before persistence.
     * A failed keystore write must never fall back to storing a plaintext secret.
     */
    fun save(context: Context, qrUri: String): Boolean {
        if (parseQr(qrUri) == null) return false
        val encrypted = runCatching { WebUntisSecretCipher.encrypt(qrUri.trim()) }.getOrNull()
            ?: return false
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_QR_ENCRYPTED, encrypted)
            remove(KEY_QR)
            remove(KEY_LAST_ERROR)
        }
        return true
    }

    fun load(context: Context): WebUntisConfig? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(KEY_QR_ENCRYPTED, null)
        if (encrypted != null) {
            // No plaintext fallback if the encrypted value cannot be decrypted.
            return runCatching { WebUntisSecretCipher.decrypt(encrypted) }
                .getOrNull()?.let(::parseQr)
        }
        val legacyPlaintext = prefs.getString(KEY_QR, null) ?: return null
        val parsed = parseQr(legacyPlaintext) ?: return null
        // Migrate in place. If encryption fails, don't continue using a
        // permanently unencrypted credential.
        return if (save(context, legacyPlaintext)) parsed else null
    }

    /**
     * Accept a public HTTPS school host only. Never downgrade an untrusted QR
     * hostname to a plaintext connection or accept embedded user info / ports.
     */
    private fun validatedHost(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http://", ignoreCase = true)) return null
        val candidate = if (trimmed.startsWith("https://", ignoreCase = true)) trimmed else "https://$trimmed"
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.userInfo != null || uri.port !in listOf(-1, 443)) return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val validDnsHost = Regex("^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        return host.takeIf(validDnsHost::matches)
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

    fun lastSync(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0L)

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_ERROR, null)
}
