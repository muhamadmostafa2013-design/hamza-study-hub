package com.hamza.studyhub.agents

import com.hamza.studyhub.books.AssignmentReferenceParser
import org.json.JSONArray

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
        raw.put("bookExercises", JSONArray().apply { reference.exercises.forEach(::put) })
        if (reference.page != null) {
            raw.put("learningCaptureSuggested", true)
            raw.put("learningCapturePrompt", "بعد ما حمزة يخلص، صوّر الصفحة المحلولة لمتابعة طريقة الحل والأخطاء المتكررة")
        }
    }
}
