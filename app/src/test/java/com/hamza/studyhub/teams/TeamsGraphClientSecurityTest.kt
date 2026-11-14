package com.hamza.studyhub.teams

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TeamsGraphClientSecurityTest {
    private val client = TeamsGraphClient("not-a-real-token")

    @Test fun allowsOnlySameMicrosoftGraphApiRoot() {
        assertEquals(
            "graph.microsoft.com",
            client.trustedGraphUrl("https://graph.microsoft.com/v1.0/education/me/assignments?%24skiptoken=abc").host
        )
    }

    @Test fun rejectsBearerTokenExfiltrationThroughNextLink() {
        listOf(
            "https://attacker.example/v1.0/education/me/assignments",
            "http://graph.microsoft.com/v1.0/education/me/assignments",
            "https://graph.microsoft.com.evil.test/v1.0/education/me/assignments",
            "https://graph.microsoft.com/beta/education/me/assignments",
            "https://graph.microsoft.com@evil.test/v1.0/education/me/assignments",
            "https://graph.microsoft.com:8443/v1.0/education/me/assignments",
            "https://graph.microsoft.com/v1.0/../beta/education/me/assignments"
        ).forEach { url ->
            assertThrows(url, TeamsGraphException::class.java) { client.trustedGraphUrl(url) }
        }
    }
}
