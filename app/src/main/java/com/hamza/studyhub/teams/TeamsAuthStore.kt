package com.hamza.studyhub.teams

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Stores only non-secret Microsoft app-registration metadata.
 * Access/refresh tokens stay inside MSAL's encrypted token cache.
 */
object TeamsAuthStore {
    private const val PREFS = "teams_auth"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val KEY_LAST_ERROR = "last_error"
    private const val KEY_ACCOUNT = "account"

    fun saveClientId(context: Context, clientId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_CLIENT_ID, clientId.trim())
        }
    }

    fun clientId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CLIENT_ID, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

    fun isConfigured(context: Context): Boolean = clientId(context) != null

    fun saveAccount(context: Context, username: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_ACCOUNT, username.orEmpty())
        }
    }

    fun account(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT, null)
            ?.takeIf { !it.isNullOrBlank() }

    fun saveSyncSuccess(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putLong(KEY_LAST_SYNC, System.currentTimeMillis())
            remove(KEY_LAST_ERROR)
        }
    }

    fun saveSyncError(context: Context, message: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
            putString(KEY_LAST_ERROR, message.take(300))
        }
    }

    fun lastSync(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_SYNC, 0L)

    fun lastError(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_ERROR, null)
            ?.takeIf { !it.isNullOrBlank() }

    /** Raw Base64 SHA-1 signing-certificate hash used by Android/MSAL redirect URIs. */
    fun signatureHash(context: Context): String {
        val certificate = signingCertificate(context)
        val sha1 = MessageDigest.getInstance("SHA-1").digest(certificate)
        return Base64.encodeToString(sha1, Base64.NO_WRAP)
    }

    /** URL-encoded redirect URI suitable for MSAL config and Entra Android platform setup. */
    fun redirectUri(context: Context): String =
        "msauth://${context.packageName}/${Uri.encode(signatureHash(context))}"

    /**
     * Builds MSAL JSON dynamically so Hamza Study Hub never needs a secret in the APK.
     * The client ID is public app metadata and can be pasted once from Entra App Registration.
     */
    fun buildMsalConfigFile(context: Context): File {
        val clientId = requireNotNull(clientId(context)) { "Teams client ID is not configured" }
        val config = JSONObject().apply {
            put("client_id", clientId)
            put("authorization_user_agent", "BROWSER")
            put("redirect_uri", redirectUri(context))
            put("account_mode", "SINGLE")
            put("broker_redirect_uri_registered", false)
            put("authorities", JSONArray().put(JSONObject().apply {
                put("type", "AAD")
                put("default", true)
                put("audience", JSONObject().apply {
                    put("type", "AzureADMultipleOrgs")
                })
            }))
            put("logging", JSONObject().apply {
                put("pii_enabled", false)
                put("log_level", "WARNING")
            })
        }

        return File(context.filesDir, "teams_msal_config.json").apply {
            writeText(config.toString())
        }
    }

    @Suppress("DEPRECATION")
    private fun signingCertificate(context: Context): ByteArray {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } else {
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        }

        val signature = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners?.firstOrNull()
        } else {
            info.signatures?.firstOrNull()
        } ?: error("No signing certificate found")

        return signature.toByteArray()
    }
}
