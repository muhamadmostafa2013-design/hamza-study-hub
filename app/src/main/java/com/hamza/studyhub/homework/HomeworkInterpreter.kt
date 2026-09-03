package com.hamza.studyhub.homework

data class HomeworkInterpretation(
    val type: UpdateType,
    val label: String,
    val emoji: String,
    val steps: List<String>,
    val needsFullText: Boolean
)

enum class UpdateType {
    HOMEWORK,
    DEADLINE_CHANGE,
    TIMETABLE_CHANGE,
    SCHOOL_MESSAGE,
    UNKNOWN
}

object HomeworkInterpreter {

    fun interpret(title: String, body: String): HomeworkInterpretation {
        val original = listOf(title, body).filter { it.isNotBlank() }.joinToString("\n")
        val text = original.lowercase()
        val type = classify(text)
        val steps = if (type == UpdateType.HOMEWORK) extractHomeworkSteps(original) else emptyList()

        return HomeworkInterpretation(
            type = type,
            label = when (type) {
                UpdateType.HOMEWORK -> "واجب"
                UpdateType.DEADLINE_CHANGE -> "تغيير موعد"
                UpdateType.TIMETABLE_CHANGE -> "تغيير جدول"
                UpdateType.SCHOOL_MESSAGE -> "رسالة مدرسة"
                UpdateType.UNKNOWN -> "تحديث"
            },
            emoji = when (type) {
                UpdateType.HOMEWORK -> "📝"
                UpdateType.DEADLINE_CHANGE -> "⏰"
                UpdateType.TIMETABLE_CHANGE -> "📅"
                UpdateType.SCHOOL_MESSAGE -> "📣"
                UpdateType.UNKNOWN -> "🔔"
            },
            steps = steps,
            needsFullText = type == UpdateType.HOMEWORK && !hasEnoughInstructionText(original)
        )
    }

    private fun classify(text: String): UpdateType {
        val deadlineWords = listOf(
            "due date", "deadline", "fällig", "abgabe", "abgabetermin", "verschoben",
            "geändert", "changed due", "new due", "neuer termin"
        )
        if (deadlineWords.any { text.contains(it) }) return UpdateType.DEADLINE_CHANGE

        val timetableWords = listOf(
            "stundenplan", "vertretung", "entfällt", "raumänderung", "room change",
            "timetable", "lesson cancelled", "cancelled lesson", "substitution"
        )
        if (timetableWords.any { text.contains(it) }) return UpdateType.TIMETABLE_CHANGE

        val homeworkWords = listOf(
            "hausaufgabe", "aufgabe", "assignment", "homework", "your task", "arbeitsauftrag",
            "bearbeiten", "worksheet", "arbeitsblatt", "reference materials"
        )
        if (homeworkWords.any { text.contains(it) }) return UpdateType.HOMEWORK

        val messageWords = listOf(
            "mitteilung", "nachricht", "information", "announcement", "reminder", "hinweis"
        )
        if (messageWords.any { text.contains(it) }) return UpdateType.SCHOOL_MESSAGE

        return UpdateType.UNKNOWN
    }

    private fun extractHomeworkSteps(text: String): List<String> {
        val compact = text.replace("\r", "\n")
        val segments = compact
            .split(Regex("(?<=[.!?])\\s+|\\n+|•|\\u2022"))
            .map { it.trim() }
            .filter { it.length >= 4 }

        val rules = listOf(
            Rule(listOf("read", "lesen", "lies", "durchlesen"), "اقرأ"),
            Rule(listOf("compare", "vergleichen", "vergleiche"), "قارن"),
            Rule(listOf("write", "schreiben", "schreibe", "notiere"), "اكتب"),
            Rule(listOf("complete", "bearbeiten", "bearbeite", "lösen", "löse"), "حل / أكمل"),
            Rule(listOf("watch", "video", "anschauen", "ansehen", "schaue"), "شاهد"),
            Rule(listOf("upload", "submit", "turn in", "abgeben", "einreichen"), "سلّم / ارفع"),
            Rule(listOf("bring", "mitbringen", "bringe"), "أحضر"),
            Rule(listOf("learn", "study", "lernen", "lerne"), "ذاكر"),
            Rule(listOf("draw", "zeichnen", "zeichne"), "ارسم")
        )

        val result = mutableListOf<String>()
        segments.forEach { segment ->
            val lower = segment.lowercase()
            val rule = rules.firstOrNull { r -> r.words.any { lower.contains(it) } }
            if (rule != null) {
                result += "${rule.arabicAction}: ${cleanInstruction(segment)}"
            }
        }

        return result.distinct().take(8).ifEmpty {
            listOf("اقرأ نص الواجب كاملًا؛ الإشعار وحده لا يحتوي تعليمات كافية لتحديد الخطوات بدقة.")
        }
    }

    private fun cleanInstruction(value: String): String {
        return value.replace(Regex("\\s+"), " ").trim().take(260)
    }

    private fun hasEnoughInstructionText(text: String): Boolean {
        if (text.length < 80) return false
        val verbs = listOf(
            "read", "lesen", "compare", "vergleichen", "write", "schreiben", "bearbeiten",
            "watch", "anschauen", "submit", "abgeben", "your task", "deine aufgabe"
        )
        return verbs.count { text.lowercase().contains(it) } >= 1
    }

    private data class Rule(val words: List<String>, val arabicAction: String)
}
