package com.hamza.studyhub.teams

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Read-only Microsoft Graph client for the school Teams / Assignments surface.
 *
 * Authentication is deliberately injected. The Android APK must never contain a
 * client secret. A future MSAL-backed token provider can supply delegated school
 * account tokens without changing this client.
 */
class TeamsGraphClient(
    private val accessToken: String,
    private val baseUrl: String = "https://graph.microsoft.com/v1.0"
) {
    data class AssignmentSummary(
        val id: String,
        val classId: String,
        val displayName: String,
        val dueDateTime: String?,
        val createdDateTime: String?,
        val lastModifiedDateTime: String?,
        val status: String?
    )

    data class AssignmentDetail(
        val id: String,
        val classId: String,
        val displayName: String,
        val dueDateTime: String?,
        val createdDateTime: String?,
        val lastModifiedDateTime: String?,
        val status: String?,
        val instructionsHtml: String?,
        val webUrl: String?,
        val resourceLabels: List<String>
    )

    fun listMyAssignments(): List<AssignmentSummary> {
        val first = "$baseUrl/education/me/assignments?\$orderby=lastModifiedDateTime%20desc&\$top=50"
        return paginate(first).mapNotNull(::parseSummary)
    }

    fun getAssignment(classId: String, assignmentId: String): AssignmentDetail {
        val url = "$baseUrl/education/classes/$classId/assignments/$assignmentId?\$expand=resources"
        return parseDetail(getJson(url))
    }

    private fun paginate(initialUrl: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        var next: String? = initialUrl
        var pageGuard = 0

        while (!next.isNullOrBlank() && pageGuard < 50) {
            val payload = getJson(next)
            val values = payload.optJSONArray("value") ?: JSONArray()
            for (i in 0 until values.length()) {
                values.optJSONObject(i)?.let(out::add)
            }
            next = payload.optString("@odata.nextLink").takeIf { it.isNotBlank() }
            pageGuard++
        }
        return out
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Accept", "application/json")
        }

        try {
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            if (status !in 200..299) {
                val graphMessage = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw TeamsGraphException(status, graphMessage?.takeIf { it.isNotBlank() } ?: "Microsoft Graph request failed")
            }
            return JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSummary(item: JSONObject): AssignmentSummary? {
        val id = item.optString("id")
        val classId = item.optString("classId")
        if (id.isBlank() || classId.isBlank()) return null
        return AssignmentSummary(
            id = id,
            classId = classId,
            displayName = item.optString("displayName").ifBlank { "Teams assignment" },
            dueDateTime = item.optString("dueDateTime").takeIf { it.isNotBlank() && it != "null" },
            createdDateTime = item.optString("createdDateTime").takeIf { it.isNotBlank() && it != "null" },
            lastModifiedDateTime = item.optString("lastModifiedDateTime").takeIf { it.isNotBlank() && it != "null" },
            status = item.optString("status").takeIf { it.isNotBlank() }
        )
    }

    private fun parseDetail(item: JSONObject): AssignmentDetail {
        val instructions = item.optJSONObject("instructions")?.optString("content")
            ?.takeIf { it.isNotBlank() }

        val resources = item.optJSONArray("resources") ?: JSONArray()
        val resourceLabels = buildList {
            for (i in 0 until resources.length()) {
                val wrapper = resources.optJSONObject(i) ?: continue
                val resource = wrapper.optJSONObject("resource") ?: wrapper
                val label = sequenceOf(
                    resource.optString("displayName"),
                    resource.optString("fileName"),
                    resource.optString("title"),
                    resource.optString("webUrl")
                ).firstOrNull { it.isNotBlank() }
                if (!label.isNullOrBlank()) add(label)
            }
        }.distinct()

        return AssignmentDetail(
            id = item.optString("id"),
            classId = item.optString("classId"),
            displayName = item.optString("displayName").ifBlank { "Teams assignment" },
            dueDateTime = item.optString("dueDateTime").takeIf { it.isNotBlank() && it != "null" },
            createdDateTime = item.optString("createdDateTime").takeIf { it.isNotBlank() && it != "null" },
            lastModifiedDateTime = item.optString("lastModifiedDateTime").takeIf { it.isNotBlank() && it != "null" },
            status = item.optString("status").takeIf { it.isNotBlank() },
            instructionsHtml = instructions,
            webUrl = item.optString("webUrl").takeIf { it.isNotBlank() && it != "null" },
            resourceLabels = resourceLabels
        )
    }
}

class TeamsGraphException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

/**
 * Access-token boundary for delegated Microsoft school-account auth.
 * The implementation should use MSAL and short-lived access tokens.
 */
interface TeamsAccessTokenProvider {
    fun getAccessToken(): String?
}
