package com.hamza.studyhub.agents

import com.hamza.studyhub.homework.HomeworkInterpreter
import com.hamza.studyhub.homework.UpdateType
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

object AgentOrchestrator {
    private val agents: List<StudyAgent> = listOf(
        IntakeAgent,
        HomeworkUnderstandingAgent,
        BookReferenceAgent,
        TaskPlannerAgent,
        VerifierAgent,
        ProgressSeedAgent
    )

    fun enrich(raw: JSONObject): JSONObject {
        val context = AgentContext(raw)
        agents.forEach { agent ->
            runCatching { agent.process(context) }
                .onFailure { error -> context.addAgentError(agent.id, error.message ?: error.javaClass.simpleName) }
        }
        context.raw.put("agentPipelineVersion", "3.1")
        context.raw.put("agentProcessedAt", System.currentTimeMillis())
        return context.raw
    }
}

interface StudyAgent { val id: String; fun process(context: AgentContext) }

class AgentContext(val raw: JSONObject) {
    fun source(): String = raw.optString("source")
    fun title(): String = raw.optString("title")
    fun body(): String = raw.optString("bigText").ifBlank { raw.optString("text") }
    fun addAgentError(agent: String, message: String) {
        val errors = raw.optJSONArray("agentErrors") ?: JSONArray().also { raw.put("agentErrors", it) }
        errors.put(JSONObject().put("agent", agent).put("message", message.take(250)))
    }
}

private object IntakeAgent : StudyAgent {
    override val id = "school-intake"
    override fun process(context: AgentContext) {
        val raw = context.raw
        val source = context.source().lowercase()
        val importMethod = raw.optString("importMethod")
        val trust = when {
            importMethod.contains("WebUntis", true) -> "OFFICIAL"
            importMethod.contains("Microsoft Graph", true) -> "OFFICIAL"
            source == "untis" || source == "teams" -> "OFFICIAL_SIGNAL"
            source == "whatsapp" -> "COMMUNITY"
            raw.optBoolean("imported", false) -> "USER_IMPORTED"
            else -> "UNKNOWN"
        }
        raw.put("evidenceTrust", trust)
        raw.put("canonicalSource", when {
            importMethod.contains("WebUntis", true) -> "WEBUNTIS"
            importMethod.contains("Microsoft Graph", true) -> "TEAMS_GRAPH"
            source == "untis" -> "UNTIS_NOTIFICATION"
            source == "teams" -> "TEAMS_NOTIFICATION"
            source == "whatsapp" -> "WHATSAPP_PARENT"
            importMethod.contains("Screenshot", true) -> "SCREENSHOT"
            importMethod.contains("Shared", true) -> "SHARED_CONTENT"
            source == "ischool" -> "ISCHOOL"
            else -> "OTHER"
        })
        raw.put("contentFingerprint", fingerprint(listOf(context.source(), context.title(), context.body()).joinToString("|")))
        if (!raw.has("needsAttention")) raw.put("needsAttention", false)
        if (!raw.has("attentionReason")) raw.put("attentionReason", "")
    }
}

private object HomeworkUnderstandingAgent : StudyAgent {
    override val id = "homework-understanding"
    override fun process(context: AgentContext) {
        val interpretation = HomeworkInterpreter.interpret(context.title(), context.body())
        val raw = context.raw
        raw.put("updateType", interpretation.type.name)
        raw.put("updateLabel", interpretation.label)
        raw.put("needsFullInstructions", interpretation.needsFullText)
        raw.put("understandingConfidence", when {
            interpretation.type == UpdateType.UNKNOWN -> 0.35
            interpretation.needsFullText -> 0.55
            context.body().length >= 120 -> 0.92
            else -> 0.78
        })
        raw.put("understoodSteps", JSONArray().apply { interpretation.steps.forEach(::put) })
    }
}

private object TaskPlannerAgent : StudyAgent {
    override val id = "task-planner"
    override fun process(context: AgentContext) {
        val raw = context.raw
        if (raw.optString("updateType") != UpdateType.HOMEWORK.name) return
        val steps = raw.optJSONArray("understoodSteps") ?: JSONArray()
        val tasks = JSONArray()
        for (i in 0 until steps.length()) {
            val text = steps.optString(i).trim()
            if (text.isBlank()) continue
            tasks.put(JSONObject().put("id", "step-${i + 1}-${fingerprint(text).take(8)}")
                .put("title", text).put("done", false).put("order", i + 1))
        }
        if (tasks.length() == 0) {
            tasks.put(JSONObject().put("id", "step-1-read")
                .put("title", "راجع تفاصيل الواجب من المصدر الرسمي قبل البدء")
                .put("done", false).put("order", 1))
        }
        raw.put("taskChecklist", tasks)
        raw.put("progressPercent", 0)
    }
}

private object VerifierAgent : StudyAgent {
    override val id = "evidence-verifier"
    override fun process(context: AgentContext) {
        val raw = context.raw
        val type = raw.optString("updateType")
        val source = raw.optString("canonicalSource")
        val needsFull = raw.optBoolean("needsFullInstructions", false)
        val dueDate = raw.optInt("dueDate", 0)
        val trust = raw.optString("evidenceTrust")
        var needsAttention = raw.optBoolean("needsAttention", false)
        val reasons = linkedSetOf<String>()
        raw.optString("attentionReason").takeIf { it.isNotBlank() }?.let { reasons += it }
        if (type == UpdateType.HOMEWORK.name && needsFull) { needsAttention = true; reasons += "تفاصيل الواجب غير كاملة" }
        if (type == UpdateType.HOMEWORK.name && dueDate == 0) { needsAttention = true; reasons += "موعد التسليم غير مؤكد" }
        if (source == "WHATSAPP_PARENT" || trust == "COMMUNITY") { needsAttention = true; reasons += "معلومة غير رسمية وتحتاج تأكيد" }
        raw.put("needsAttention", needsAttention)
        raw.put("attentionReason", reasons.joinToString(" • "))
        raw.put("verified", trust == "OFFICIAL" && !needsFull)
        raw.put("verifierStatus", when {
            trust == "OFFICIAL" && !needsFull -> "VERIFIED"
            trust == "OFFICIAL_SIGNAL" && !needsFull -> "HIGH_CONFIDENCE"
            trust == "COMMUNITY" -> "NEEDS_CONFIRMATION"
            else -> "PARTIAL"
        })
    }
}

private object ProgressSeedAgent : StudyAgent {
    override val id = "progress-seed"
    override fun process(context: AgentContext) {
        val raw = context.raw
        if (raw.optString("updateType") != UpdateType.HOMEWORK.name) return
        val checklist = raw.optJSONArray("taskChecklist") ?: return
        val total = checklist.length()
        var done = 0
        for (i in 0 until total) if (checklist.optJSONObject(i)?.optBoolean("done", false) == true) done++
        val percent = if (total == 0) 0 else ((done.toDouble() / total) * 100).toInt()
        raw.put("progressPercent", percent)
        raw.put("progressStatus", when { percent >= 100 -> "DONE"; percent > 0 -> "IN_PROGRESS"; else -> "NOT_STARTED" })
    }
}

private fun fingerprint(value: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
