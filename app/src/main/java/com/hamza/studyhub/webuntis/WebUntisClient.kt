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
 * QR/secret authentication is session-cookie based. Some newer WebUntis REST
 * endpoints also accept a Bearer token, so we use it when the server exposes one,
 * but never make it mandatory for homework or the classic timetable API.
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
        val loginProfile: JSONObject
    ) {
        val cookieHeader: String
            get() = buildList {
                add("JSESSIONID=$jsessionId")
                schoolNameCookie?.takeIf { it.isNotBlank() }?.let { add("schoolname=$it") }
            }.joinToString("; ")
    }

    private data class Identity(
        val personId: Long,
        val personType: Int,
        val klasseId: Long? = null
    )

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
        val identity = resolveIdentity(session)
            ?: throw IllegalStateException("WebUntis لم يرجع هوية الطالب")

        // 1) Current REST timetable. Some servers expose this for personal student views.
        val modernStudent = runCatching {
            fetchModernTimetable(
                session = session,
                resourceType = "STUDENT",
                resourceId = identity.personId,
                start = start,
                end = end,
                timetableType = "MY_TIMETABLE"
            )
        }
        if (modernStudent.isSuccess && modernStudent.getOrThrow().isNotEmpty()) {
            return modernStudent.getOrThrow()
        }

        // 2) Classic JSON-RPC timetable is the proven fallback for secret/QR sessions.
        val classicStudent = runCatching {
            fetchClassicTimetable(
                session = session,
                elementId = identity.personId,
                elementType = identity.personType,
                start = start,
                end = end
            )
        }
        if (classicStudent.isSuccess && classicStudent.getOrThrow().isNotEmpty()) {
            return classicStudent.getOrThrow()
        }

        // 3) A few schools expose only the class timetable for student accounts.
        // Secret login can usually provide klasseId via daytimetable/config.
        identity.klasseId?.takeIf { it > 0 }?.let { klasseId ->
            val modernClass = runCatching {
                fetchModernTimetable(
                    session = session,
                    resourceType = "CLASS",
                    resourceId = klasseId,
                    start = start,
                    end = end,
                    timetableType = "STANDARD"
                )
            }
            if (modernClass.isSuccess && modernClass.getOrThrow().isNotEmpty()) {
                return modernClass.getOrThrow()
            }

            val classicClass = runCatching {
                fetchClassicTimetable(
                    session = session,
                    elementId = klasseId,
                    elementType = 1,
                    start = start,
                    end = end
                )
            }
            if (classicClass.isSuccess && classicClass.getOrThrow().isNotEmpty()) {
                return classicClass.getOrThrow()
            }
        }

        // An empty timetable can be legitimate (holiday/weekend), so only throw when
        // both student paths actually failed. Otherwise return the verified empty result.
        if (modernStudent.isFailure && classicStudent.isFailure) {
            throw IllegalStateException(
                "تعذر قراءة جدول WebUntis: " +
                    listOfNotNull(
                        modernStudent.exceptionOrNull()?.message,
                        classicStudent.exceptionOrNull()?.message
                    ).joinToString(" • ")
            )
        }

        return classicStudent.getOrNull()
            ?: modernStudent.getOrNull()
            ?: emptyList()
    }

    private fun resolveIdentity(session: Session): Identity? {
        extractIdentity(session.loginProfile)?.let { loginIdentity ->
            val klasse = runCatching { fetchKlasseId(session) }.getOrNull()
            return loginIdentity.copy(klasseId = klasse)
        }

        runCatching { fetchAppConfig(session) }.getOrNull()?.let { appConfig ->
            extractIdentityFromAppConfig(appConfig)?.let { appIdentity ->
                val klasse = appIdentity.klasseId ?: runCatching { fetchKlasseId(session) }.getOrNull()
                return appIdentity.copy(klasseId = klasse)
            }
        }

        runCatching { fetchUserData(session) }.getOrNull()?.let { userData ->
            extractIdentity(userData)?.let { appIdentity ->
                val klasse = runCatching { fetchKlasseId(session) }.getOrNull()
                return appIdentity.copy(klasseId = klasse)
            }
        }

        return null
    }

    private fun fetchAppConfig(session: Session): JSONObject {
        return getJson("${serverBase()}WebUntis/api/app/config", session)
    }

    private fun fetchKlasseId(session: Session): Long? {
        val root = getJson("${serverBase()}WebUntis/api/daytimetable/config", session)
        val data = root.optJSONObject("data") ?: root
        return data.optLong("klasseId", Long.MIN_VALUE)
            .takeIf { it != Long.MIN_VALUE && it > 0 }
    }

    private fun extractIdentityFromAppConfig(root: JSONObject): Identity? {
        val data = root.optJSONObject("data") ?: root
        val user = data.optJSONObject("loginServiceConfig")
            ?.optJSONObject("user")
            ?: return null

        val personId = user.optLong("personId", Long.MIN_VALUE)
        if (personId <= 0 || personId == Long.MIN_VALUE) return null

        val persons = user.optJSONArray("persons") ?: JSONArray()
        var personType = 5
        for (i in 0 until persons.length()) {
            val person = persons.optJSONObject(i) ?: continue
            if (person.optLong("id", Long.MIN_VALUE) == personId) {
                personType = person.optInt("type", 5)
                break
            }
        }

        return Identity(personId = personId, personType = personType)
    }

    private fun fetchUserData(session: Session): JSONObject {
        val root = getJson("${serverBase()}WebUntis/api/rest/view/v1/app/data", session)
        return root.optJSONObject("user")
            ?: root.optJSONObject("data")?.optJSONObject("user")
            ?: throw IllegalStateException("تعذر قراءة بيانات الطالب من WebUntis")
    }

    private fun extractIdentity(root: JSONObject): Identity? {
        val personId = root.optLong("personId", Long.MIN_VALUE)
        if (personId > 0 && personId != Long.MIN_VALUE) {
            return Identity(
                personId = personId,
                personType = root.optInt("personType", 5),
                klasseId = root.optLong("klasseId", Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE && it > 0 }
            )
        }

        root.optJSONObject("person")?.let { person ->
            val id = person.optLong("id", Long.MIN_VALUE)
            if (id > 0 && id != Long.MIN_VALUE) {
                return Identity(
                    personId = id,
                    personType = person.optInt("type", root.optInt("personType", 5))
                )
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

    private fun fetchModernTimetable(
        session: Session,
        resourceType: String,
        resourceId: Long,
        start: LocalDate,
        end: LocalDate,
        timetableType: String
    ): List<TimetableEntry> {
        val endpoint =
            "${serverBase()}WebUntis/api/rest/view/v1/timetable/entries" +
                "?resourceType=$resourceType&resources=$resourceId" +
                "&start=${start.format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
                "&end=${end.format(DateTimeFormatter.ISO_LOCAL_DATE)}" +
                "&format=2&timetableType=$timetableType&layout=START_TIME"
        return parseModernTimetable(getJson(endpoint, session))
    }

    private fun parseModernTimetable(root: JSONObject): List<TimetableEntry> {
        val days = root.optJSONArray("days")
            ?: root.optJSONObject("data")?.optJSONArray("days")
            ?: JSONArray()

        val out = mutableListOf<TimetableEntry>()
        val seen = mutableSetOf<String>()

        for (d in 0 until days.length()) {
            val day = days.optJSONObject(d) ?: continue
            val date = day.optString("date").replace("-", "").toIntOrNull() ?: continue
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
                val id = if (ids != null && ids.length() > 0) {
                    ids.optLong(0)
                } else {
                    Long.MIN_VALUE
                }
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
        elementId: Long,
        elementType: Int,
        start: LocalDate,
        end: LocalDate
    ): List<TimetableEntry> {
        val formatter = DateTimeFormatter.BASIC_ISO_DATE
        val endpoint =
            "${serverBase()}WebUntis/jsonrpc.do?school=${URLEncoder.encode(config.school, StandardCharsets.UTF_8.name())}"

        val options = JSONObject()
            .put("id", System.currentTimeMillis())
            .put("element", JSONObject().put("id", elementId).put("type", elementType))
            .put("startDate", start.format(formatter).toInt())
            .put("endDate", end.format(formatter).toInt())
            .put("onlyBaseTimetable", false)
            .put("showBooking", true)
            .put("showInfo", true)
            .put("showSubstText", true)
            .put("showLsText", true)
            .put("showLsNumber", true)
            .put("showStudentgroup", true)
            .put("klasseFields", JSONArray().put("id").put("name").put("longname").put("externalkey"))
            .put("roomFields", JSONArray().put("id").put("name").put("longname").put("externalkey"))
            .put("subjectFields", JSONArray().put("id").put("name").put("longname").put("externalkey"))
            .put("teacherFields", JSONArray().put("id").put("name").put("longname").put("externalkey"))

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

        val requestBody = JSONObject()
            .put("id", "HamzaStudyHub")
            .put("method", "getUserData2017")
            .put(
                "params",
                JSONArray().put(
                    JSONObject().put(
                        "auth",
                        JSONObject()
                            .put("clientTime", now)
                            .put("user", config.user)
                            .put("otp", otp)
                    )
                )
            )
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

        val cookies = connection.headerFields
            .filterKeys { it?.equals("Set-Cookie", true) == true }
            .values
            .flatten()

        val jsession = extractCookie(cookies, "JSESSIONID")
            ?: error("WebUntis لم يرجع جلسة تسجيل دخول")
        val schoolCookie = extractCookie(cookies, "schoolname")
        val profile = response.optJSONObject("result") ?: JSONObject()
        connection.disconnect()

        val cookieSession = Session(
            jsessionId = jsession,
            schoolNameCookie = schoolCookie,
            bearerToken = "",
            loginProfile = profile
        )

        // Bearer token is an optional optimization/compatibility path. Secret sessions
        // remain fully usable with JSESSIONID even when token/new is unavailable.
        val bearer = runCatching { fetchBearerToken(cookieSession) }.getOrDefault("")
        return cookieSession.copy(bearerToken = bearer)
    }

    private fun fetchBearerToken(session: Session): String {
        val connection = openConnection("${serverBase()}WebUntis/api/token/new").apply {
            requestMethod = "GET"
            setRequestProperty("Cookie", session.cookieHeader)
            setRequestProperty("Accept", "text/plain, application/json")
        }
        val token = readResponse(connection).trim().trim('"')
        connection.disconnect()
        return token.takeIf {
            it.isNotBlank() && !it.startsWith("{") && !it.startsWith("<")
        } ?: error("WebUntis لم يرجع Bearer token")
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
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Cache-Control", "no-cache")
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
