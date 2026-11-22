package com.hamza.studyhub.learning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PendingCameraCaptureTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun generatedNameResolvesAfterStateRestoration() {
        val root = temporary.newFolder()
        val savedName = PendingCameraCapture.newName()
        val pending = PendingCameraCapture.resolve(root, savedName)!!
        assertTrue(pending.parentFile!!.mkdirs())
        pending.writeBytes(byteArrayOf(1, 2, 3))
        val restored = PendingCameraCapture.resolve(root, savedName)
        assertEquals(pending, restored)
        assertEquals(3L, restored?.length())
    }

    @Test fun rejectsArbitraryPathsAndUnrelatedExistingFilenames() {
        val root = temporary.newFolder()
        for (candidate in listOf(null, "", "../example.jpg", "/tmp/example.jpg", "attempt-123.jpg")) {
            assertNull(PendingCameraCapture.resolve(root, candidate))
        }
    }
}
