package com.hamza.studyhub.webuntis

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Minimal WebUntis homework client.
 *
 * It uses the QR-generated user + shared secret that WebUntis itself provides to
 * mobile clients. The app generates the 30-second OTP locally, opens a short-lived
 * WebUntis session, reads homework, then discards the session.
 *
 * No password or QR secret is sent to Hamza Study Hub's GitHub repo or any third
 * party server; authentication goes directly from the phone to the configured
 * WebUntis school server.
 */
class WebUntisClient(private val config: WebUntisConfig) {

    data class Homework(
        val id: Long,
        val lessonId: Long,
        val assignedDate: Int,
        val dueDate: Int,
        val text: String,
        val remark: String,
        val completed: Boolean,
        val subject: String,
        val teacher: String
    )

    private data class Session(
        val jsessionId: String,
        val schoolNameCookie: String
    ) {
        val cookieHeader: String
            get() = "JSESSIONID=$jsessionId; schoolname=$schoolNameCookie"
    }

    fun fetchHomeworks(start: LocalDate, end: LocalDate): List<Homework> {
        val session = loginWithQrSecret()
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        val endpoint = buildString {
            append(serverBase())
            append("WebUntis/api/homeworks/lessons")
            append("?startDate=${start.format(formatter)}")
            append("&endDate=${end.format(formatter)}")
        }

        val root = getJson(endpoint, session)
        return parseHomeworkResponse(root)
    }

    private fun loginWithQrSecret(): Session {
        val now = System.currentTimeMillis()
        val otp = Totp.generate(config.secret, now).toIntOrNull()
            ?: throw IllegalStateException("تعذر إنشاء رمز WebUntis المؤقت")

        val school = URLEncoder.encode(config.school, StandardCharsets.UTF_8.name())
        val endpoint = "${serverBase()}WebUntis/jsonrpc_intern.do?school=$school"

        val auth = JSONObject()
            .put("user", config.user)
            .put("otp", otp)
            .put("clientTime", now)

        val requestBody = JSONObject()
            .put("id", "HamzaStudyHub")
            .put("method", "getUserData2017")
            .put("params", JSONArray().put(JSONObject().put("auth", auth)))
            .put("jsonrpc", "2.0")

        val connection = openConnection(endpoint).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        connection.outputStream.use { output ->
            output.write(requestBody.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val responseText = readResponse(connection)
        val response = JSONObject(responseText)
        response.optJSONObject("error")?.let { error ->
            throw IllegalStateException(
                error.optString("message").ifBlank { "تعذر تسجيل الدخول إلى WebUntis" }
            )
        }

        val cookieValues = connection.headerFields
            .filterKeys { it?.equals("Set-Cookie", ignoreCase = true) == true }
            .values
            .flatten()

        val jsession = extractCookie(cookieValues, "JSESSIONID")
            ?: throw IllegalStateException("WebUntis لم يرجع جلسة تسجيل دخول")
        val schoolCookie = extractCookie(cookieValues, "schoolname")
            ?: throw IllegalStateException("WebUntis لم يرجع schoolname للجلسة")

        connection.disconnect()
        return Session(jsession, schoolCookie)
    }

    private fun getJson(endpoint: String, session: Session): JSONObject {
        val connection = openConnection(endpoint).apply {
            requestMethod = "GET"
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Cookie", session.cookieHeader)
        }

        val text = readResponse(connection)
        connection.disconnect()
        val root = JSONObject(text)

        root.optJSONObject("error")?.let { error ->
            throw IllegalStateException(
                error.optString("message").ifBlank { "WebUntis رجع خطأ أثناء قراءة الواجبات" }
            )
        }

        return root
    }

    private fun parseHomeworkResponse(root: JSONObject): List<Homework> {
        val data = root.optJSONObject("data") ?: root
        val homeworks = data.optJSONArray("homeworks") ?: JSONArray()
        val lessons = data.optJSONArray("lessons") ?: JSONArray()
        val records = data.optJSONArray("records") ?: JSONArray()
        val teachers = data.optJSONArray("teachers") ?: JSONArray()

        val lessonsById = mutableMapOf<Long, JSONObject>()
        for (i in 0 until lessons.length()) {
            val lesson = lessons.optJSONObject(i) ?: continue
            lessonsById[lesson.optLong("id", Long.MIN_VALUE)] = lesson
        }

        val teachersById = mutableMapOf<Long, JSONObject>()
        for (i in 0 until teachers.length()) {
            val teacher = teachers.optJSONObject(i) ?: continue
            teachersById[teacher.optLong("id", Long.MIN_VALUE)] = teacher
        }

        val teacherByHomeworkId = mutableMapOf<Long, Long>()
        for (i in 0 until records.length()) {
            val record = records.optJSONObject(i) ?: continue
            val homeworkId = record.optLong("homeworkId", Long.MIN_VALUE)
            val teacherId = record.optLong("teacherId", Long.MIN_VALUE)
            if (homeworkId != Long.MIN_VALUE && teacherId != Long.MIN_VALUE) {
                teacherByHomeworkId[homeworkId] = teacherId
            }
        }

        val result = mutableListOf<Homework>()
        for (i in 0 until homeworks.length()) {
            val hw = homeworks.optJSONObject(i) ?: continue
            val id = hw.optLong("id", Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) continue

            val lessonId = hw.optLong("lessonId", Long.MIN_VALUE)
            val lesson = lessonsById[lessonId]
            val teacher = teacherByHomeworkId[id]?.let { teachersById[it] }

            result += Homework(
                id = id,
                lessonId = lessonId,
                assignedDate = hw.optInt("date", 0),
                dueDate = hw.optInt("dueDate", 0),
                text = hw.optString("text").trim(),
                remark = hw.optString("remark").trim(),
                completed = hw.optBoolean("completed", false),
                subject = extractSubject(lesson),
                teacher = extractTeacher(teacher)
            )
        }

        return result
    }

    private fun extractSubject(lesson: JSONObject?): String {
        lesson ?: return ""
        val raw = lesson.opt("subject")
        if (raw is String && raw.isNotBlank()) return raw
        if (raw is JSONObject) {
            raw.optString("longName").takeIf { it.isNotBlank() }?.let { return it }
            raw.optString("name").takeIf { it.isNotBlank() }?.let { return it }
        }
        listOf("subjectName", "subjectLongName").forEach { key ->
            lesson.optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun extractTeacher(teacher: JSONObject?): String {
        teacher ?: return ""
        listOf("longName", "displayName", "name").forEach { key ->
            teacher.optString(key).takeIf { it.isNotBlank() }?.let { return it }
        }
        return ""
    }

    private fun serverBase(): String {
        val clean = config.server
            .removePrefix("https://")
            .removePrefix("http://")
            .trim('/')
        return "https://$clean/"
    }

    private fun openConnection(endpoint: String): HttpURLConnection {
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36 HamzaStudyHub/0.2"
            )
            setRequestProperty("Accept", "application/json, text/plain, */*")
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = if (stream == null) {
            ""
        } else {
            BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { it.readText() }
        }

        if (code !in 200..299) {
            throw IllegalStateException("WebUntis HTTP $code: ${body.take(250)}")
        }
        return body
    }

    private fun extractCookie(headers: List<String>, name: String): String? {
        val prefix = "$name="
        return headers.asSequence()
            .flatMap { header -> header.split(';').asSequence() }
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix, ignoreCase = true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
    }
}
