package com.hamza.studyhub.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssignmentReferenceParserTest {
    @Test fun parsesGermanPageAndExercise() {
        val ref = AssignmentReferenceParser.parse("Deutsch", "Arbeitsbuch S. 37 Nr. 3-5")
        assertNotNull(ref)
        assertEquals(37, ref!!.page)
        assertEquals(listOf("3-5"), ref.exercises)
        assertTrue(ref.confidence > 0.9)
    }

    @Test fun parsesArabicPageAndExercise() {
        val ref = AssignmentReferenceParser.parse("الواجب صفحة 12 تمرين 3")
        assertNotNull(ref)
        assertEquals(12, ref!!.page)
        assertEquals(listOf("3"), ref.exercises)
    }

    @Test fun resolvesMatchingSubjectBook() {
        val catalog = InMemoryBookCatalog(listOf(
            BookAsset("1", "Deutschprofis", "Deutsch", aliases = setOf("Arbeitsbuch")),
            BookAsset("2", "HSU 5", "HSU")
        ))
        val result = BookAssignmentResolver(catalog).resolve("Deutsch", "Hausaufgabe", "Arbeitsbuch S. 37 Nr. 3")
        assertNotNull(result)
        assertEquals(ResolutionStatus.RESOLVED, result!!.status)
        assertEquals("1", result.book?.id)
        assertEquals(37, result.reference.page)
    }

    @Test fun neverInventsBookWhenCatalogDoesNotMatch() {
        val catalog = InMemoryBookCatalog(listOf(BookAsset("1", "Mathe", "Mathematik")))
        val result = BookAssignmentResolver(catalog).resolve("Deutsch", "Hausaufgabe", "S. 37 Nr. 3")
        assertNotNull(result)
        assertTrue(result!!.book == null || result.status == ResolutionStatus.NEEDS_CONFIRMATION)
    }
}
