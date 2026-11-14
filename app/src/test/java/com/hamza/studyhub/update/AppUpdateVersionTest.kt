package com.hamza.studyhub.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateVersionTest {
    @Test fun publishedReleaseIsRecognizedAsNewer() {
        assertTrue(AppUpdateManager.isNewer("0.5.3", "0.5.2"))
        assertTrue(AppUpdateManager.isNewer("v0.6.0", "0.5.99"))
        assertTrue(AppUpdateManager.isNewer("1.0.0", "0.9.99"))
    }

    @Test fun olderOrSameReleaseCannotTriggerUpdate() {
        assertFalse(AppUpdateManager.isNewer("0.5.2", "0.5.3"))
        assertFalse(AppUpdateManager.isNewer("0.5.3", "0.5.3"))
        assertFalse(AppUpdateManager.isNewer("0.5.0", "1.0.0"))
    }
}
