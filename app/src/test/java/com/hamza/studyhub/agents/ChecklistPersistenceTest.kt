package com.hamza.studyhub.agents

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistPersistenceTest {
    @Test fun reprocessingTheSameHomeworkKeepsCheckedSteps() {
        val raw = JSONObject()
            .put("source", "Untis")
            .put("title", "Hausaufgabe • Deutsch")
            .put("bigText", "Arbeitsbuch Seite 37 Aufgabe 3")
            .put("dueDate", 20261115)
        AgentOrchestrator.enrich(raw)
        val first = raw.getJSONArray("taskChecklist").getJSONObject(0)
        first.put("done", true)
        AgentOrchestrator.enrich(raw)
        val again = raw.getJSONArray("taskChecklist").getJSONObject(0)
        assertTrue(again.getBoolean("done"))
        assertEquals(100, raw.getInt("progressPercent"))
    }
}
