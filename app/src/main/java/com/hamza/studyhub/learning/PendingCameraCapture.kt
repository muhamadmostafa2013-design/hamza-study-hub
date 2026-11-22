package com.hamza.studyhub.learning

import java.io.File
import java.util.UUID

/** Persists only an opaque basename, never an arbitrary path or a granted URI. */
internal object PendingCameraCapture {
    private val allowedName = Regex("capture-[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.jpg")

    fun newName(): String = "capture-${UUID.randomUUID()}.jpg"

    fun resolve(filesDir: File, name: String?): File? {
        if (name == null || !allowedName.matches(name)) return null
        return runCatching {
            val root = filesDir.canonicalFile
            val directory = File(root, "student_work")
            val file = File(directory, name)
            file.takeIf { directory.canonicalFile == directory && file.canonicalFile == file }
        }.getOrNull()
    }
}
