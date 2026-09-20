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

/** WebUntis client used directly from Hamza's phone. */
class WebUntisClient(private val config: WebUntisConfig) {

    data class Homework(
        val id: Long, val lessonId: Long, val assignedDate: Int, val dueDate: Int,
        val text: String, val remark: String, val completed: Boolean,
        val subject: String, val teacher: String
    )

    data class TimetableEntry(
        val id: Long,
        val date: Int,
        val startTime: Int,
        val endTime: Int,
        val subject: String,
        val teacher: String,
        val room: String,
        val cancelled: Boolean,
        val substitutionText: String
    )

    private data class Session(
        val jsessionId: String,
        val schoolNameCookie: String,
        val profile: JSONObject
    ) {
        val cookieHeader: String get() = "JSESSIONID=$jsessionId; schoolname=$schoolNameCookie"
    }

    private data class Identity(val personId: Long, val personType: Int)

    fun fetchHomeworks(start: LocalDate, end: LocalDate): List<Homework> {
        val session = loginWithQrSecret()
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        val endpoint = "${serverBase()}WebUntis/api/homeworks/lessons?startDate=${start.format(formatter)}&endDate=${end.format(formatter)}"
        return parseHomeworkResponse(getJson(endpoint, session))
    }

    /** Reads the logged-in student's timetable through the authenticated WebUntis session. */
    fun fetchOwnTimetable(start: LocalDate, end: LocalDate): List<TimetableEntry> {
        val session = loginWithQrSecret()
        val formatter = DateTimeFormatter.BASIC_ISO_DATE

        // getUserData2017 already returns the logged-in person's identity. Using it directly
        // avoids an extra app/data call whose JSON shape differs between WebUntis servers.
        val identity = extractIdentity(session.profile)
            ?: runCatching { extractIdentity(fetchUserData(session)) }.getOrNull()
            ?: throw IllegalStateException("WebUntis لم يرجع هوية الطالب")

        val endpoint = "${serverBase()}WebUntis/jsonrpc.do?school=${URLEncoder.encode(config.school, StandardCharsets.UTF_8.name())}"
        val options = JSONObject()
            .put("element", JSONObject().put("id", identity.personId).put("type", identity.personType))
            .put("startDate", start.format(formatter).toInt())
            .put("endDate", end.format(formatter).toInt())
            .put("onlyBaseTimetable", false)
            .put("showBooking", true)
            .put("showInfo", true)
            .put("showSubstText", true)
            .put("showLsText", true)
            .put("showLsNumber", true)
            .put("showStudentgroup", true)
            .put("klasseFields", JSONArray().put("id").put("name").put("longname"))
            .put("roomFields", JSONArray().put("id").put("name").put("longname"))
            .put("subjectFields", JSONArray().put("id").put("name").put("longname"))
            .put("teacherFields", JSONArray().put("id").put("name").put("longname"))

        // Classic WebUntis expects the timetable options inside params.options.
        val body = JSONObject().put("id", "HamzaStudyHubTimetable")
            .put("method", "getTimetable")
            .put("params", JSONObject().put("options", options))
            .put("jsonrpc", "2.0")

        val root = postJson(endpoint, body, session)
        val array = root.optJSONArray("result") ?: JSONArray()
        return (0 until array.length()).mapNotNull { i ->
            val p = array.optJSONObject(i) ?: return@mapNotNull null
            TimetableEntry(
                id = p.optLong("id", Long.MIN_VALUE),
                date = p.optInt("date", 0),
                startTime = p.optInt("startTime", 0),
                endTime = p.optInt("endTime", 0),
                subject = firstElementName(p.optJSONArray("su")),
                teacher = firstElementName(p.optJSONArray("te")),
                room = firstElementName(p.optJSONArray("ro")),
                cancelled = p.optString("code").equals("cancelled", true) || p.optBoolean("cancelled", false),
                substitutionText = p.optString("substText").ifBlank { p.optString("info") }
            )
        }.sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startTime })
    }

    private fun fetchUserData(session: Session): JSONObject {
        val endpoint = "${serverBase()}WebUntis/api/rest/view/v1/app/data"
        val root = runCatching { getJson(endpoint, session) }.getOrNull()
        val user = root?.optJSONObject("user") ?: root?.optJSONObject("data")?.optJSONObject("user")
        if (user != null) return user
        throw IllegalStateException("تعذر قراءة بيانات الطالب من WebUntis")
    }

    private fun extractIdentity(root: JSONObject): Identity? {
        val personId = root.optLong("personId", Long.MIN_VALUE)
        if (personId != Long.MIN_VALUE && personId > 0) {
            return Identity(personId, root.optInt("personType", 5))
        }

        listOf("user", "userData", "data", "person", "student", "profile").forEach { key ->
            val child = root.optJSONObject(key)
            if (child != null) extractIdentity(child)?.let { return it }
        }

        val keys = root.keys()
        while (keys.hasNext()) {
            val value = root.opt(keys.next())
            if (value is JSONObject) extractIdentity(value)?.let { return it }
        }
        return null
    }

    private fun firstElementName(array: JSONArray?): String {
        val item = array?.optJSONObject(0) ?: return ""
        return item.optString("longname").ifBlank { item.optString("name") }.trim()
    }

    private fun loginWithQrSecret(): Session {
        val now = System.currentTimeMillis()
        val otp = Totp.generate(config.secret, now).toIntOrNull() ?: error("تعذر إنشاء رمز WebUntis المؤقت")
        val school = URLEncoder.encode(config.school, StandardCharsets.UTF_8.name())
        val endpoint = "${serverBase()}WebUntis/jsonrpc_intern.do?school=$school"
        val auth = JSONObject().put("user", config.user).put("otp", otp).put("clientTime", now)
        val requestBody = JSONObject().put("id", "HamzaStudyHub").put("method", "getUserData2017")
            .put("params", JSONArray().put(JSONObject().put("auth", auth))).put("jsonrpc", "2.0")
        val connection = openConnection(endpoint).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json") }
        connection.outputStream.use { it.write(requestBody.toString().toByteArray(StandardCharsets.UTF_8)) }
        val response = JSONObject(readResponse(connection))
        response.optJSONObject("error")?.let { error(it.optString("message").ifBlank { "تعذر تسجيل الدخول إلى WebUntis" }) }
        val result = response.optJSONObject("result") ?: JSONObject()
        val cookies = connection.headerFields.filterKeys { it?.equals("Set-Cookie", true) == true }.values.flatten()
        val session = Session(
            extractCookie(cookies, "JSESSIONID") ?: error("WebUntis لم يرجع جلسة تسجيل دخول"),
            extractCookie(cookies, "schoolname") ?: error("WebUntis لم يرجع schoolname للجلسة"),
            result
        )
        connection.disconnect()
        return session
    }

    private fun getJson(endpoint: String, session: Session): JSONObject {
        val c = openConnection(endpoint).apply { requestMethod = "GET"; setRequestProperty("Cookie", session.cookieHeader) }
        val text = readResponse(c); c.disconnect(); return JSONObject(text)
    }

    private fun postJson(endpoint: String, body: JSONObject, session: Session): JSONObject {
        val c = openConnection(endpoint).apply { requestMethod = "POST"; doOutput = true; setRequestProperty("Content-Type", "application/json"); setRequestProperty("Cookie", session.cookieHeader) }
        c.outputStream.use { it.write(body.toString().toByteArray(StandardCharsets.UTF_8)) }
        val text = readResponse(c); c.disconnect(); return JSONObject(text).also { root -> root.optJSONObject("error")?.let { error(it.optString("message")) } }
    }

    private fun parseHomeworkResponse(root: JSONObject): List<Homework> {
        val data = root.optJSONObject("data") ?: root
        val homeworks = data.optJSONArray("homeworks") ?: JSONArray(); val lessons = data.optJSONArray("lessons") ?: JSONArray(); val records = data.optJSONArray("records") ?: JSONArray(); val teachers = data.optJSONArray("teachers") ?: JSONArray()
        val lessonsById = (0 until lessons.length()).mapNotNull { lessons.optJSONObject(it) }.associateBy { it.optLong("id") }
        val teachersById = (0 until teachers.length()).mapNotNull { teachers.optJSONObject(it) }.associateBy { it.optLong("id") }
        val teacherByHomework = mutableMapOf<Long, Long>(); for (i in 0 until records.length()) records.optJSONObject(i)?.let { teacherByHomework[it.optLong("homeworkId")] = it.optLong("teacherId") }
        return (0 until homeworks.length()).mapNotNull { i ->
            val hw = homeworks.optJSONObject(i) ?: return@mapNotNull null; val id = hw.optLong("id", Long.MIN_VALUE); if (id == Long.MIN_VALUE) return@mapNotNull null
            val lessonId = hw.optLong("lessonId", Long.MIN_VALUE); val lesson = lessonsById[lessonId]; val teacher = teacherByHomework[id]?.let { teachersById[it] }
            Homework(id, lessonId, hw.optInt("date"), hw.optInt("dueDate"), hw.optString("text").trim(), hw.optString("remark").trim(), hw.optBoolean("completed"), extractSubject(lesson), extractTeacher(teacher))
        }
    }

    private fun extractSubject(o: JSONObject?): String { o ?: return ""; val raw=o.opt("subject"); if(raw is String && raw.isNotBlank()) return raw; if(raw is JSONObject) return raw.optString("longName").ifBlank{raw.optString("name")}; return o.optString("subjectName").ifBlank{o.optString("subjectLongName")} }
    private fun extractTeacher(o: JSONObject?): String { o ?: return ""; return o.optString("longName").ifBlank{o.optString("displayName").ifBlank{o.optString("name")}} }
    private fun serverBase(): String = "https://${config.server.removePrefix("https://").removePrefix("http://").trim('/')}/"
    private fun openConnection(endpoint: String) = (URL(endpoint).openConnection() as HttpURLConnection).apply { connectTimeout=15_000; readTimeout=20_000; instanceFollowRedirects=true; setRequestProperty("User-Agent","HamzaStudyHub/0.5 Android"); setRequestProperty("Accept","application/json, text/plain, */*") }
    private fun readResponse(c: HttpURLConnection): String { val code=c.responseCode; val stream=if(code in 200..299)c.inputStream else c.errorStream; val body=if(stream==null)"" else BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use{it.readText()}; if(code !in 200..299) error("WebUntis HTTP $code: ${body.take(250)}"); return body }
    private fun extractCookie(headers: List<String>, name: String): String? { val prefix="$name="; return headers.asSequence().flatMap{it.split(';').asSequence()}.map{it.trim()}.firstOrNull{it.startsWith(prefix,true)}?.substringAfter('=')?.trim()?.trim('"')?.takeIf{it.isNotBlank()} }
}
