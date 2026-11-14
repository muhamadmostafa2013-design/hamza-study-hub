package com.hamza.studyhub.webuntis

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore AES-GCM: the WebUntis QR token never persists as plaintext.
 * The app-local key is non-exportable and intentionally not cloud-backed.
 */
internal object WebUntisSecretCipher {
    private const val KEY_ALIAS = "hamza.webuntis.qr.aes.v1"
    private const val PREFIX = "v1:"
    private const val IV_SIZE = 12
    private const val MIN_TAG_SIZE = 16

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(encrypted, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String {
        require(value.startsWith(PREFIX)) { "Unsupported encrypted secret version" }
        val bytes = Base64.decode(value.removePrefix(PREFIX), Base64.DEFAULT)
        require(bytes.size >= IV_SIZE + MIN_TAG_SIZE) { "Invalid encrypted payload" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateKey(),
            GCMParameterSpec(128, bytes.copyOfRange(0, IV_SIZE))
        )
        return cipher.doFinal(bytes.copyOfRange(IV_SIZE, bytes.size))
            .toString(Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
