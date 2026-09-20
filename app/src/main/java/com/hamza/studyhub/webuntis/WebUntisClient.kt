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
 * WebUntis client for the QR/shared-secret profile used on Hamza's phone.
 *
 * The QR login establishes a WebUntis session, then exchanges that session for the
 * current Bearer token used by WebUntis REST endpoints. Keeping both cookie + Bearer
 * makes this compatible with the mixed WebUntis API surface used in 2026.
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
        val schoolNameCookie: String?,
        val bearerToken: String,
        val profile: JSONObject
    ) {
        val cookieHeader: String
            get() = buildList {
                add("JSESSIONID=$jsessionId")
                schoolNameCookie?.takeIf { it.isNotBlank() }?.let { add("schoolname=$it") }
            }.joinToString("; ")
    }

    private data class Identity(val personId: Long, val personType: Int)

    @Volatile
    private var cachedSession: Session? = null

    fun fetchHomeworks(start: LocalDate, end: LocalDate): List<Homework> {
        val session = authenticatedSession()
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        val endpoint =
            "${serverBase()}WebUntis/api/homeworks/lessons?startDate=${start.format(formatter)}&endDate=${end.format(formatter)}"
        return parseHomeworkResponse(getJson(endpoint, session))
    }

    fun fetchOwnTimetable(start: LocalDate, end: LocalDate): List<TimetableEntry> {
        val session = authenticatedSession()
        val identity = extractIdentity(session.profile)
            ?: runCatching { extractIdentity(fetchUserData(session)) }.getOrNull()
            ?: throw IllegalStateException("WebUntis لم يرجع هوية الطالب")

        // This is the timetable endpoint used by current WebUntis builds.
        val startIso = start.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endIso = end.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val endpoint =
            "${serverBase()}WebUntis/api/rest/view/v1/timetable/entries" +
                "?resourceType=STUDENT&resources=${identity.personId}" +
                "&start=$startIso&end=$endIso&format=2&timetableType=MY_TIMETABLE&layout=START_TIME"

        val modern = runCatching {
            parseModernTimetable(getJson(endpoint, session))
        }

        if (modern.isSuccess && modern.getOrThrow().isNotEmpty()) {
            return modern.getOrThrow()
        }

        // Some schools still expose the classic timetable more reliably. Keep it as a
        // compatibility fallback, but with the correct params.options structure.
        val classic = runCatching {
            fetchClassicTimetable(session, identity, start, end)
        }

        return when {
            classic.isSuccess && classic.getOrThrow().isNotEmpty() -> classic.getOrThrow()
            modern.isSuccess -> modern.getOrThrow()
            classic.isSuccess -> classic.getOrThrow()
            else -> throw modern.exceptionOrNull()
                ?: classic.exceptionOrNull()
                ?: IllegalStateException("تعذر قراءة جدول WebUntis")
        }
    }

    private fun fetchModernTimetableNotUsed() = Unit

    private fun parseModernTimetable(root: JSONObject): List<TimetableEntry> {
        val days = root.optJSONArray("days")
            ?: root.optJSONObject("data")?.optJSONArray("days")
            ?: JSONArray()

        val out = mutableListOf<TimetableEntry>()
        val seen = mutableSetOf<String>()

        for (d in 0 until days.length()) {
            val day = days.optJSONObject(d) ?: continue
            val dateRaw = day.optString("date")
            val date = dateRaw.replace("-", "").toIntOrNull() ?: continue
            val entries = day.optJSONArray("gridEntries") ?: JSONArray()

            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val duration = entry.optJSONObject("duration") ?: JSONObject()
                val startTime = isoTimeToUntisInt(duration.optString("start"))
                val endTime = isoTimeToUntisInt(duration.optString("end"))

                val subject = namesForType(entry, "SUBJECT").firstOrNull()
                    ?: entry.optString("lessonInfo").takeIf { it.isNotBlank() }
                    ?: entry.optString("lessonText").takeIf { it.isNotBlank() }
                    ?: "Unterricht"
                val teachers = namesForType(entry, "TEACHER").joinToString(", ")
                val rooms = namesForType(entry, "ROOM").joinToString(", ")
                val ids = entry.optJSONArray("ids")
                val id = ids?.optLong(0, Long.MIN_VALUE) ?: Long.MIN_VALUE
                val substitution = entry.optString("substitutionText")
                    .ifBlank { entry.optString("lessonText") }
                val cancelled = entry.optString("status").equals("CANCELLED", true)

                val dedupe = "$date|$startTime|$endTime|$subject|$rooms|$teachers"
                if (!seen.add(dedupe)) continue

                out += TimetableEntry(
                    id = id,
                    date = date,
                    startTime = startTime,
                    endTime = endTime,
                    subject = subject,
                    teacher = teachers,
                    room = rooms,
                    cancelled = cancelled,
                    substitutionText = substitution
                )
            }
        }

        return out.sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startTime })
    }

    private fun namesForType(entry: JSONObject, wantedType: String): List<String> {
        val names = mutableListOf<String>()
        listOf("position1", "position2", "position3", "position4").forEach { key ->
            val values = entry.optJSONArray(key) ?: return@forEach
            for (i in 0 until values.length()) {
                val current = values.optJSONObject(i)?.optJSONObject("current") ?: continue
                if (!current.optString("type").equals(wantedType, true)) continue
                val name = current.optString("longName")
                    .ifBlank { current.optString("displayName") }
                    .ifBlank { current.optString("shortName") }
                    .trim()
                if (name.isNotBlank()) names += name
            }
        }
        return names.distinct()
    }

    private fun isoTimeToUntisInt(value: String): Int {
        if (value.isBlank()) return 0
        val clock = value.substringAfter('T', value).take(5)
        return clock.replace(":", "").toIntOrNull() ?: 0
    }

    private fun fetchClassicTimetable(
        session: Session,
        identity: Identity,
        start: LocalDate,
        end: LocalDate
    ): List<TimetableEntry> {
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        val endpoint =
            "${serverBase()}WebUntis/jsonrpc.do?school=${URLEncoder.encode(config.school, StandardCharsets.UTF_8.name())}"
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

        val body = JSONObject()
            .put("id", "HamzaStudyHubTimetable")
            .put("method", "getTimetable")
            .put("params", JSONObject().put("options", options))
            .put("jsonrpc", "2.0")

        val array = postJson(endpoint, body, session).optJSONArray("result") ?: JSONArray()
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
                cancelled = p.optString("code").equals("cancelled", true) ||
                    p.optBoolean("cancelled", false),
                substitutionText = p.optString("substText").ifBlank { p.optString("info") }
            )
        }.sortedWith(compareBy<TimetableEntry> { it.date }.thenBy { it.startTime })
    }

    private fun fetchUserData(session: Session): JSONObject {
        val endpoint = "${serverBase()}WebUntis/api/rest/view/v1/app/data"
        val root = getJson(endpoint, session)
        return root.optJSONObject("user")
            ?: root.optJSONObject("data")?.optJSONObject("user")
            ?: throw IllegalStateException("تعذر قراءة بيانات الطالب من WebUntis")
    }

    private fun extractIdentity(root: JSONObject): Identity? {
        val personId = root.optLong("personId", Long.MIN_VALUE)
        if (personId > 0 && personId != Long.MIN_VALUE) {
            return Identity(personId, root.optInt("personType", 5))
        }

        // Current app/data shape: user.person.id
        root.optJSONObject("person")?.let { person ->
            val id = person.optLong("id", Long.MIN_VALUE)
            if (id > 0 && id != Long.MIN_VALUE) {
                return Identity(id, root.optInt("personType", 5))
            }
        }

        listOf("user", "userData", "data", "student", "profile").forEach { key ->
            root.optJSONObject(key)?.let { child ->
                extractIdentity(child)?.let { return it }
            }
        }

        val keys = root.keys()
        while (keys.hasNext()) {
            val value = root.opt(keys.next())
            if (value is JSONObject) {
                extractIdentity(value)?.let { return it }
            }
        }
        return null
    }

    private fun firstElementName(array: JSONArray?): String {
        val item = array?.optJSONObject(0) ?: return ""
        return item.optString("longname")
            .ifBlank { item.optString("longName") }
            .ifBlank { item.optString("name") }
            .trim()
    }

    private fun authenticatedSession(): Session {
        cachedSession?.let { return it }
        return synchronized(this) {
            cachedSession ?: loginWithQrSecret().also { cachedSession = it }
        }
    }

    private fun loginWithQrSecret(): Session {
        val now = System.currentTimeMillis()
        val otp = Totp.generate(config.secret, now).toIntOrNull()
            ?: error("تعذر إنشاء رمز WebUntis المؤقت")
        val school = URLEncoder.encode(config.school, StandardCharsets.UTF_8.name())
        val endpoint =
            "${serverBase()}WebUntis/jsonrpc_intern.do?m=getUserData2017&school=$school&v=i2.2"
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
        connection.outputStream.use {
            it.write(requestBody.toString().toByteArray(StandardCharsets.UTF_8))
        }

        val response = JSONObject(readResponse(connection))
        response.optJSONObject("error")?.let {
            error(it.optString("message").ifBlank { "تعذر تسجيل الدخول إلى WebUntis" })
        }
        val result = response.optJSONObject("result") ?: JSONObject()
        val cookies = connection.headerFields
            .filterKeys { it?.equals("Set-Cookie", true) == true }
            .values
            .flatten()

        val jsession = extractCookie(cookies, "JSESSIONID")
            ?: error("WebUntis لم يرجع جلسة تسجيل دخول")
        val schoolCookie = extractCookie(cookies, "schoolname")
        connection.disconnect()

        val provisional = Session(
            jsessionId = jsession,
            schoolNameCookie = schoolCookie,
            bearerToken = "",
            profile = result
        )
        val token = fetchBearerToken(provisional)
        return provisional.copy(bearerToken = token)
    }

    private fun fetchBearerToken(session: Session): String {
        val endpoint = "${serverBase()}WebUntis/api/token/new"
        val connection = openConnection(endpoint).apply {
            requestMethod = "GET"
            setRequestProperty("Cookie", session.cookieHeader)
            setRequestProperty("Accept", "text/plain, application/json")
        }
        val token = readResponse(connection).trim().trim('"')
        connection.disconnect()
        if (token.isBlank() || token.startsWith("{")) {
            error("WebUntis لم يرجع رمز الوصول الحديث")
        }
        return token
    }

    private fun getJson(endpoint: String, session: Session): JSONObject {
        val connection = openConnection(endpoint).apply {
            requestMethod = "GET"
            setRequestProperty("Cookie", session.cookieHeader)
            if (session.bearerToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${session.bearerToken}")
            }
        }
        val text = readResponse(connection)
        connection.disconnect()
        return JSONObject(text)
    }

    private fun postJson(endpoint: String, body: JSONObject, session: Session): JSONObject {
        val connection = openConnection(endpoint).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Cookie", session.cookieHeader)
            if (session.bearerToken.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${session.bearerToken}")
            }
        }
        connection.outputStream.use {
            it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
        }
        val text = readResponse(connection)
        connection.disconnect()
        return JSONObject(text).also { root ->
            root.optJSONObject("error")?.let {
                error(it.optString("message").ifBlank { "WebUntis JSON-RPC error" })
            }
        }
    }

    private fun parseHomeworkResponse(root: JSONObject): List<Homework> {
        val data = root.optJSONObject("data") ?: root
        val homeworks = data.optJSONArray("homeworks") ?: JSONArray()
        val lessons = data.optJSONArray("lessons") ?: JSONArray()
        val records = data.optJSONArray("records") ?: JSONArray()
        val teachers = data.optJSONArray("teachers") ?: JSONArray()

        val lessonsById = (0 until lessons.length())
            .mapNotNull { lessons.optJSONObject(it) }
            .associateBy { it.optLong("id") }
        val teachersById = (0 until teachers.length())
            .mapNotNull { teachers.optJSONObject(it) }
            .associateBy { it.optLong("id") }

        val teacherByHomework = mutableMapOf<Long, Long>()
        for (i in 0 until records.length()) {
            records.optJSONObject(i)?.let {
                teacherByHomework[it.optLong("homeworkId")] = it.optLong("teacherId")
            }
        }

        return (0 until homeworks.length()).mapNotNull { i ->
            val hw = homeworks.optJSONObject(i) ?: return@mapNotNull null
            val id = hw.optLong("id", Long.MIN_VALUE)
            if (id == Long.MIN_VALUE) return@mapNotNull null

            val lessonId = hw.optLong("lessonId", Long.MIN_VALUE)
            val lesson = lessonsById[lessonId]
            val teacherId = hw.optLong(
                "teacherId",
                teacherByHomework[id] ?: Long.MIN_VALUE
            )
            val teacher = teachersById[teacherId]
            val subject = extractSubject(lesson)
                .ifBlank { hw.optString("subject") }
                .ifBlank { "Allgemein" }

            Homework(
                id = id,
                lessonId = lessonId,
                assignedDate = hw.optInt("date"),
                dueDate = hw.optInt("dueDate"),
                text = hw.optString("text").trim(),
                remark = hw.optString("remark").trim(),
                completed = hw.optBoolean("completed"),
                subject = subject,
                teacher = extractTeacher(teacher)
            )
        }
    }

    private fun extractSubject(o: JSONObject?): String {
        o ?: return ""
        val raw = o.opt("subject")
        if (raw is String && raw.isNotBlank()) return raw
        if (raw is JSONObject) {
            return raw.optString("longName").ifBlank { raw.optString("name") }
        }
        return o.optString("subjectName")
            .ifBlank { o.optString("subjectLongName") }
            .ifBlank { o.optString("lesson") }
    }

    private fun extractTeacher(o: JSONObject?): String {
        o ?: return ""
        return o.optString("longName")
            .ifBlank { o.optString("displayName") }
            .ifBlank { o.optString("name") }
    }

    private fun serverBase(): String =
        "https://${config.server.removePrefix("https://").removePrefix("http://").trim('/')}/"

    private fun openConnection(endpoint: String) =
        (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "HamzaStudyHub/0.5 Android")
            setRequestProperty("Accept", "application/json, text/plain, */*")
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
            error("WebUntis HTTP $code: ${body.take(250)}")
        }
        return body
    }

    private fun extractCookie(headers: List<String>, name: String): String? {
        val prefix = "$name="
        return headers.asSequence()
            .flatMap { it.split(';').asSequence() }
            .map { it.trim() }
            .firstOrNull { it.startsWith(prefix, true) }
            ?.substringAfter('=')
            ?.trim()
            ?.trim('"')
            ?.takeIf { it.isNotBlank() }
    }
}
