package com.hamza.studyhub.webuntis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class WebUntisConfigValidationTest {
    private fun uri(server: String): String =
        "untis://setschool?url=${java.net.URLEncoder.encode(server, "UTF-8")}&school=School&user=Student&key=BASE32KEY"

    @Test fun schoolQrExtractsOnlyTrustedHttpsHost() {
        val result = WebUntisConfigStore.parseQr(uri("https://school.webuntis.com/WebUntis/"))
        assertEquals("school.webuntis.com", result?.server)
    }

    @Test fun qrCannotRedirectTotpToLocalhostOrEmbeddedCredentials() {
        listOf(
            "http://school.webuntis.com",
            "https://localhost:8080",
            "https://127.0.0.1/WebUntis",
            "https://user@attacker.test/WebUntis",
            "https://attacker.test:8443/WebUntis",
            "javascript:alert(1)",
            "https://evil.test\\@webuntis.com"
        ).forEach { assertNull(it, WebUntisConfigStore.parseQr(uri(it))) }
    }
}
