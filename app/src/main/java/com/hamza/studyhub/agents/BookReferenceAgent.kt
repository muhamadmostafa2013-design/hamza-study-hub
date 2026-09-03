package com.hamza.studyhub.agents

import com.hamza.studyhub.books.AssignmentReferenceParser
import org.json.JSONArray

/**
 * Detects page/exercise references in school homework without guessing a book.
 * Book identity is resolved later against the family's private Book Library.
 */
object BookReferenceAgent : StudyAgent {
    override val id: String = "book-reference"

    override fun process(context: AgentContext) {
        val reference = AssignmentReferenceParser.parse(context.title(), context.body())
        val raw = context.raw

        if (reference == null) {
            raw.put("bookReferenceDetected", false)
            return
        }

        raw.put("bookReferenceDetected", true)
        raw.put("bookPage", reference.page ?: 0)
        raw.put("bookReferenceRaw", reference.rawReference)
        raw.put("bookReferenceConfidence", reference.confidence)

        val exercises = JSONArray()
        reference.exercises.forEach { exercises.put(it) }
        raw.put("bookExercises", exercises)

        if (reference.page != null && !raw.has("learningCaptureSuggested")) {
            raw.put("learningCaptureSuggested", true)
            raw.put(
                "learningCapturePrompt",
                "بعد ما حمزة يخلص، صوّر الصفحة المحلولة علشان نتابع طريقة الحل والأخطاء المتكررة"
            )
        }
    }
}
