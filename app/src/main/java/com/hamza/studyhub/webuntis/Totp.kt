package com.hamza.studyhub.webuntis

import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object Totp {
    private const val PERIOD_SECONDS = 30L
    private const val DIGITS = 6

    fun generate(base32Secret: String, timeMillis: Long = System.currentTimeMillis()): String {
        val key = decodeBase32(base32Secret)
        val counter = (timeMillis / 1000L) / PERIOD_SECONDS
        val data = ByteBuffer.allocate(8).putLong(counter).array()

        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key, "HmacSHA1"))
        val hash = mac.doFinal(data)

        val offset = hash.last().toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)

        val modulo = 1_000_000
        return (binary % modulo).toString().padStart(DIGITS, '0')
    }

    private fun decodeBase32(input: String): ByteArray {
        val clean = input.uppercase()
            .filter { it in 'A'..'Z' || it in '2'..'7' }

        var buffer = 0
        var bitsLeft = 0
        val out = ArrayList<Byte>()

        for (c in clean) {
            val value = when (c) {
                in 'A'..'Z' -> c.code - 'A'.code
                in '2'..'7' -> c.code - '2'.code + 26
                else -> continue
            }

            buffer = (buffer shl 5) or value
            bitsLeft += 5

            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out.add(((buffer shr bitsLeft) and 0xFF).toByte())
            }
        }

        return out.toByteArray()
    }
}
