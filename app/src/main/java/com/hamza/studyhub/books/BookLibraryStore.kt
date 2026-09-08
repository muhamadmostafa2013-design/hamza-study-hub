package com.hamza.studyhub.books

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BookLibraryStore(private val context: Context) : BookCatalog {
    private val file: File get() = File(context.filesDir, "book_library.json")

    override fun allBooks(): List<BookAsset> {
        if (!file.exists()) return emptyList()
        val root = runCatching { JSONObject(file.readText()) }.getOrElse { return emptyList() }
        val items = root.optJSONArray("books") ?: JSONArray()
        return buildList {
            for (i in 0 until items.length()) items.optJSONObject(i)?.toBookAsset()?.let(::add)
        }.sortedBy { it.subject.lowercase() }
    }

    fun upsert(book: BookAsset) {
        val books = allBooks().toMutableList()
        val index = books.indexOfFirst { it.id == book.id }
        if (index >= 0) books[index] = book else books.add(book)
        write(books)
    }

    fun delete(bookId: String) = write(allBooks().filterNot { it.id == bookId })
    fun get(bookId: String): BookAsset? = allBooks().firstOrNull { it.id == bookId }

    private fun write(books: List<BookAsset>) {
        val root = JSONObject().apply {
            put("version", 1)
            put("updatedAt", System.currentTimeMillis())
            put("books", JSONArray().apply { books.forEach { put(it.toJson()) } })
        }
        file.writeText(root.toString())
    }

    private fun BookAsset.toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("title", title); put("subject", subject)
        put("edition", edition.orEmpty()); put("localUri", localUri.orEmpty())
        put("mimeType", mimeType.orEmpty()); put("pageCount", pageCount ?: 0)
        put("importedAt", importedAt)
        put("aliases", JSONArray().apply { aliases.forEach(::put) })
    }

    private fun JSONObject.toBookAsset(): BookAsset? {
        val id = optString("id"); val title = optString("title"); val subject = optString("subject")
        if (id.isBlank() || title.isBlank() || subject.isBlank()) return null
        val aliasesJson = optJSONArray("aliases") ?: JSONArray()
        val aliases = buildSet {
            for (i in 0 until aliasesJson.length()) aliasesJson.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        }
        return BookAsset(
            id = id, title = title, subject = subject,
            edition = optString("edition").takeIf { it.isNotBlank() },
            localUri = optString("localUri").takeIf { it.isNotBlank() },
            mimeType = optString("mimeType").takeIf { it.isNotBlank() },
            pageCount = optInt("pageCount", 0).takeIf { it > 0 },
            aliases = aliases,
            importedAt = optLong("importedAt", System.currentTimeMillis())
        )
    }
}
