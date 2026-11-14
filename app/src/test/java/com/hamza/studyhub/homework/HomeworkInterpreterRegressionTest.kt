package com.hamza.studyhub.homework

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeworkInterpreterRegressionTest {
    @Test fun ordinaryAssignmentWithDueDateRemainsHomework() {
        val interpreted = HomeworkInterpreter.interpret(
            "Homework: Deutsch", "Read the worksheet; due date is tomorrow."
        )
        assertEquals(UpdateType.HOMEWORK, interpreted.type)
    }

    @Test fun explicitlyMovedDeadlineIsClassifiedSeparately() {
        val interpreted = HomeworkInterpreter.interpret(
            "Homework update", "The due date changed to Friday."
        )
        assertEquals(UpdateType.DEADLINE_CHANGE, interpreted.type)
    }

    @Test fun germanHomeworkInstructionsRemainRecognized() {
        val interpreted = HomeworkInterpreter.interpret(
            "Hausaufgabe", "Bitte Arbeitsblatt lesen und Aufgabe 3 bearbeiten."
        )
        assertEquals(UpdateType.HOMEWORK, interpreted.type)
    }
}
