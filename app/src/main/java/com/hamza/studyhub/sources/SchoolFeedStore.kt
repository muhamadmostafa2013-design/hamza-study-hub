package com.hamza.studyhub.sources

import android.content.Context
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/**
 * Single-process, local-first feed store.
 *
 * Multiple sync workers, shared-content intake, and the notification listener
 * must never overwrite each other's changes. Every read-modify-write happens
 * under one process-wide lock and is committed through Android AtomicFile.
 */
object SchoolFeedStore {
    private const val FILENAME = "school_notifications.jsonl"
    private val lock = Any()

    fun read(context: Context): List<JSONObject> = synchronized(lock) {
        readLocked(atomicFile(context))
    }

    fun append(context: Context, item: JSONObject) {
        edit(context) { it.add(item) }
    }

    /**
     * Perform a mutation against the latest file snapshot. The callback must
     * not retain references to the mutable feed or modify it after returning.
     */
    fun <T> edit(context: Context, block: (MutableList<JSONObject>) -> T): T =
        synchronized(lock) {
            val file = atomicFile(context)
            val entries = readLocked(file)
            val before = serialize(entries)
            val result = block(entries)
            val after = serialize(entries)
            if (before != after) {
                val output = file.startWrite()
                try {
                    output.write(after.toByteArray(Charsets.UTF_8))
                    file.finishWrite(output)
                } catch (error: Exception) {
                    file.failWrite(output)
                    throw error
                }
            }
            result
        }

    private fun atomicFile(context: Context): AtomicFile =
        AtomicFile(File(context.filesDir, FILENAME))

    private fun readLocked(file: AtomicFile): MutableList<JSONObject> {
        val text = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            ""
        }
        return text.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { line -> runCatching { JSONObject(line) }.getOrNull() }
            .toMutableList()
    }

    private fun serialize(items: List<JSONObject>): String =
        if (items.isEmpty()) "" else items.joinToString("\n") { it.toString() } + "\n"
}
