package com.hamza.studyhub.agents

import android.content.Context
import com.hamza.studyhub.learning.LearningEvidenceStore
import com.hamza.studyhub.learning.StudentAttempt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Parent-facing supervisor for Hamza's study workflow.
 *
 * The agent is deliberately bounded: it may rank, summarize and request confirmation,
 * but it never edits school facts, marks homework completed, invents deadlines, or
 * promotes community/user-imported evidence above official school sources.
 */
class StudySupervisorAgent(private val context: Context) {
    private val store = StudySupervisorRunStore(context)
    private val engine = StudySupervisorEngine()

    fun runIfNeeded(feed: List<JSONObject>, force: Boolean = false): StudySupervisorReport {
        val attempts = LearningEvidenceStore(context).attempts()
        val inputFingerprint = StudySupervisorEngine.inputFingerprint(feed, attempts)
        val latest = store.latest()
        if (!force && latest?.inputFingerprint == inputFingerprint) return latest

        val report = engine.build(feed, attempts)
        store.append(report)
        return report
    }

    fun latest(): StudySupervisorReport? = store.latest()
}

class StudySupervisorEngine(
    private val todayProvider: () -> LocalDate = { LocalDate.now() }
) {
    fun build(feed: List<JSONObject>, attempts: List<StudentAttempt>): StudySupervisorReport {
        val today = todayProvider()
        val fingerprint = inputFingerprint(feed, attempts)
        val evidenceKeys = feed.mapNotNull(::evidenceKey).toSet()

        val ranked = feed.mapNotNull { item -> candidate(item, attempts, today) }
            .sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.dueDate ?: LocalDate.MAX })
            .take(MAX_ACTIONS)

        val verifiedActions = ranked.mapIndexedNotNull { index, candidate ->
            val key = candidate.evidenceKey
            if (key !in evidenceKeys || candidate.completed) return@mapIndexedNotNull null
            StudySupervisorAction(
                priority = index + 1,
                evidenceKey = key,
                title = candidate.title,
                subject = candidate.subject,
                source = candidate.source,
                dueDate = candidate.dueDate?.toString(),
                instruction = candidate.instruction,
                reason = candidate.reason,
                requiresParentConfirmation = candidate.requiresConfirmation,
                confidence = candidate.confidence
            )
        }

        val confirmations = verifiedActions
            .filter { it.requiresParentConfirmation }
            .map { "${it.title}: راجع المصدر الرسمي قبل الاعتماد على التفاصيل أو الموعد." }
            .distinct()

        val status = when {
            verifiedActions.isEmpty() -> SupervisorStatus.CLEAR
            confirmations.isNotEmpty() -> SupervisorStatus.NEEDS_PARENT_CONFIRMATION
            else -> SupervisorStatus.READY
        }

        val headline = when (status) {
            SupervisorStatus.CLEAR -> "لا توجد واجبات مفتوحة تحتاج ترتيبًا الآن."
            SupervisorStatus.NEEDS_PARENT_CONFIRMATION -> "جهزت الخطة، وفيها ${confirmations.size} نقطة تحتاج تأكيد منك."
            SupervisorStatus.READY -> "جهزت أولويات المذاكرة من الأدلة الموجودة في التطبيق."
        }

        val confidence = when {
            verifiedActions.isEmpty() -> 1.0
            else -> verifiedActions.map { it.confidence }.average().coerceIn(0.0, 1.0)
        }

        return StudySupervisorReport(
            runId = UUID.randomUUID().toString(),
            generatedAt = System.currentTimeMillis(),
            inputFingerprint = fingerprint,
            status = status,
            headline = headline,
            actions = verifiedActions,
            confirmations = confirmations,
            evidenceCount = feed.size,
            attemptCount = attempts.size,
            confidence = confidence
        )
    }

    private fun candidate(item: JSONObject, attempts: List<StudentAttempt>, today: LocalDate): Candidate? {
        if (item.optBoolean("completed", false)) return null

        val title = item.optString("title").ifBlank { "تحديث مدرسي" }
        val subject = item.optString("subject").ifBlank { title.substringAfter("•", "").trim() }
        val source = item.optString("source").ifBlank { "School" }
        val trust = item.optString("evidenceTrust")
        val body = item.optString("bigText").ifBlank { item.optString("text") }
        val dueDate = parseDueDate(item.optInt("dueDate", 0))
        val days = dueDate?.let { ChronoUnit.DAYS.between(today, it) }
        val unresolvedAttention = item.optBoolean("needsAttention", false) && !item.optBoolean("attentionResolved", false)
        val missingInstructions = item.optBoolean("needsFullInstructions", false) || body.isBlank()
        val community = trust.equals("COMMUNITY", true) || source.equals("WhatsApp", true)
        val weakEvidence = trust.equals("UNKNOWN", true) || trust.equals("USER_IMPORTED", true) || community
        val requiresConfirmation = weakEvidence || missingInstructions || dueDate == null
        val key = evidenceKey(item) ?: return null
        val assignmentAttempts = attempts.count { it.assignmentFingerprint == item.optString("contentFingerprint") || it.assignmentFingerprint == item.optString("externalId") }

        var score = 0
        if (unresolvedAttention) score += 45
        score += when {
            days == null -> 15
            days < 0 -> 80
            days == 0L -> 70
            days == 1L -> 60
            days <= 3L -> 45
            days <= 7L -> 25
            else -> 5
        }
        if (item.optBoolean("isNew", true)) score += 12
        if (trust.equals("OFFICIAL", true)) score += 8
        if (assignmentAttempts >= 2) score += 6

        val reason = when {
            days != null && days < 0 -> "موعد التسليم فات؛ راجعه أولًا وحدد هل ما زال مطلوبًا."
            days == 0L -> "موعد التسليم اليوم."
            days == 1L -> "موعد التسليم غدًا."
            missingInstructions -> "تفاصيل المطلوب غير كاملة في البيانات الحالية."
            unresolvedAttention -> item.optString("attentionReason").ifBlank { "النظام علّمه كعنصر يحتاج مراجعة." }
            assignmentAttempts >= 2 -> "فيه أكثر من محاولة محفوظة لهذا الواجب؛ يستحق مراجعة مركزة."
            else -> "واجب مفتوح تم ترتيبه حسب الموعد وحداثة التحديث."
        }

        val instruction = when {
            missingInstructions || weakEvidence -> "افتح المصدر الرسمي وثبّت المطلوب، ثم ابدأ الحل."
            days != null && days < 0 -> "تحقق من حالة الواجب في المصدر الرسمي ثم أكمله إذا كان ما زال مطلوبًا."
            days == 0L -> "ابدأ بهذا الواجب الآن، وبعد الانتهاء صوّر محاولة حمزة لحفظ دليل التعلم."
            days == 1L -> "ضع هذا الواجب ضمن أول جلسة مذاكرة اليوم."
            else -> "راجع المطلوب وحدد وقتًا للحل قبل الموعد."
        }

        val confidence = when {
            trust.equals("OFFICIAL", true) && !missingInstructions && dueDate != null -> 0.96
            trust.equals("OFFICIAL_SIGNAL", true) && !missingInstructions -> 0.84
            weakEvidence -> 0.55
            else -> 0.72
        }

        return Candidate(
            evidenceKey = key,
            title = title,
            subject = subject,
            source = source,
            dueDate = dueDate,
            instruction = instruction,
            reason = reason,
            requiresConfirmation = requiresConfirmation,
            confidence = confidence,
            score = score,
            completed = false
        )
    }

    private fun parseDueDate(value: Int): LocalDate? {
        if (value <= 0) return null
        return runCatching { LocalDate.parse(value.toString(), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }

    private data class Candidate(
        val evidenceKey: String,
        val title: String,
        val subject: String,
        val source: String,
        val dueDate: LocalDate?,
        val instruction: String,
        val reason: String,
        val requiresConfirmation: Boolean,
        val confidence: Double,
        val score: Int,
        val completed: Boolean
    )

    companion object {
        private const val MAX_ACTIONS = 5

        fun inputFingerprint(feed: List<JSONObject>, attempts: List<StudentAttempt>): String {
            val feedPart = feed.sortedBy { evidenceKey(it).orEmpty() }.joinToString("\n") { item ->
                listOf(
                    evidenceKey(item).orEmpty(),
                    item.optString("title"),
                    item.optString("bigText").ifBlank { item.optString("text") },
                    item.optInt("dueDate", 0).toString(),
                    item.optBoolean("completed", false).toString(),
                    item.optBoolean("needsAttention", false).toString(),
                    item.optBoolean("attentionResolved", false).toString(),
                    item.optString("evidenceTrust")
                ).joinToString("|")
            }
            val attemptsPart = attempts.sortedBy { it.id }.joinToString("\n") { "${it.id}|${it.assignmentFingerprint}|${it.attemptNumber}" }
            return sha256("$feedPart\n--attempts--\n$attemptsPart")
        }

        private fun evidenceKey(item: JSONObject): String? = sequenceOf(
            item.optString("externalId"),
            item.optString("contentFingerprint"),
            item.optString("notificationKey")
        ).firstOrNull { it.isNotBlank() } ?: item.optLong("timestamp", 0L).takeIf { it > 0 }?.let { "timestamp:$it" }

        private fun sha256(value: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

data class StudySupervisorAction(
    val priority: Int,
    val evidenceKey: String,
    val title: String,
    val subject: String,
    val source: String,
    val dueDate: String?,
    val instruction: String,
    val reason: String,
    val requiresParentConfirmation: Boolean,
    val confidence: Double
)

data class StudySupervisorReport(
    val runId: String,
    val generatedAt: Long,
    val inputFingerprint: String,
    val status: SupervisorStatus,
    val headline: String,
    val actions: List<StudySupervisorAction>,
    val confirmations: List<String>,
    val evidenceCount: Int,
    val attemptCount: Int,
    val confidence: Double
) {
    fun compactText(): String = buildString {
        append(headline)
        actions.firstOrNull()?.let { first ->
            append("\n\nأول أولوية: ").append(first.title)
            first.dueDate?.let { append(" • ").append(it) }
        }
        if (confirmations.isNotEmpty()) append("\nيحتاج تأكيد: ").append(confirmations.size)
    }

    fun parentBrief(): String = buildString {
        append(headline).append("\n\n")
        if (actions.isEmpty()) {
            append("لا توجد خطوات مقترحة الآن.")
        } else {
            actions.forEach { action ->
                append(action.priority).append(") ").append(action.title)
                if (action.subject.isNotBlank()) append(" • ").append(action.subject)
                action.dueDate?.let { append(" • ").append(it) }
                append("\n").append(action.instruction)
                append("\nالسبب: ").append(action.reason)
                if (action.requiresParentConfirmation) append("\n⚠ يحتاج تأكيد من المصدر الرسمي")
                append("\n\n")
            }
        }
        append("الثقة: ").append((confidence * 100).toInt()).append("%")
        append(" • الأدلة: ").append(evidenceCount)
        append(" • المحاولات: ").append(attemptCount)
    }
}

enum class SupervisorStatus { CLEAR, READY, NEEDS_PARENT_CONFIRMATION }

private class StudySupervisorRunStore(context: Context) {
    private val file = File(context.filesDir, "study_supervisor_runs.jsonl")

    fun append(report: StudySupervisorReport) {
        file.appendText(report.toJson().toString() + "\n")
    }

    fun latest(): StudySupervisorReport? {
        if (!file.exists()) return null
        return file.readLines().asReversed().firstNotNullOfOrNull { line ->
            runCatching { JSONObject(line).toReport() }.getOrNull()
        }
    }

    private fun StudySupervisorReport.toJson(): JSONObject = JSONObject().apply {
        put("runId", runId)
        put("generatedAt", generatedAt)
        put("inputFingerprint", inputFingerprint)
        put("status", status.name)
        put("headline", headline)
        put("evidenceCount", evidenceCount)
        put("attemptCount", attemptCount)
        put("confidence", confidence)
        put("confirmations", JSONArray().apply { confirmations.forEach(::put) })
        put("actions", JSONArray().apply {
            actions.forEach { action ->
                put(JSONObject().apply {
                    put("priority", action.priority)
                    put("evidenceKey", action.evidenceKey)
                    put("title", action.title)
                    put("subject", action.subject)
                    put("source", action.source)
                    put("dueDate", action.dueDate.orEmpty())
                    put("instruction", action.instruction)
                    put("reason", action.reason)
                    put("requiresParentConfirmation", action.requiresParentConfirmation)
                    put("confidence", action.confidence)
                })
            }
        })
    }

    private fun JSONObject.toReport(): StudySupervisorReport {
        val actionArray = optJSONArray("actions") ?: JSONArray()
        val actions = buildList {
            for (i in 0 until actionArray.length()) {
                val item = actionArray.optJSONObject(i) ?: continue
                add(StudySupervisorAction(
                    priority = item.optInt("priority"),
                    evidenceKey = item.optString("evidenceKey"),
                    title = item.optString("title"),
                    subject = item.optString("subject"),
                    source = item.optString("source"),
                    dueDate = item.optString("dueDate").takeIf { it.isNotBlank() },
                    instruction = item.optString("instruction"),
                    reason = item.optString("reason"),
                    requiresParentConfirmation = item.optBoolean("requiresParentConfirmation"),
                    confidence = item.optDouble("confidence", 0.0)
                ))
            }
        }
        val confirmationsArray = optJSONArray("confirmations") ?: JSONArray()
        val confirmations = buildList {
            for (i in 0 until confirmationsArray.length()) confirmationsArray.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
        return StudySupervisorReport(
            runId = optString("runId"),
            generatedAt = optLong("generatedAt"),
            inputFingerprint = optString("inputFingerprint"),
            status = runCatching { SupervisorStatus.valueOf(optString("status")) }.getOrDefault(SupervisorStatus.CLEAR),
            headline = optString("headline"),
            actions = actions,
            confirmations = confirmations,
            evidenceCount = optInt("evidenceCount"),
            attemptCount = optInt("attemptCount"),
            confidence = optDouble("confidence", 0.0)
        )
    }
}
