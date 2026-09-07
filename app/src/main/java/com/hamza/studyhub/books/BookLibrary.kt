package com.hamza.studyhub.books

import java.security.MessageDigest

/**
 * Metadata for one private study book supplied by Hamza's family.
 *
 * The app keeps a durable URI grant to the source file. It does not redistribute
 * the book. Page content can be read on demand only to resolve a school assignment
 * and analyze Hamza's own learning evidence.
 */
data class BookAsset(
    val id: String,
    val title: String,
    val subject: String,
    val edition: String? = null,
    val localUri: String? = null,
    val mimeType: String? = null,
    val pageCount: Int? = null,
    val aliases: Set<String> = emptySet(),
    val importedAt: Long = System.currentTimeMillis()
)

data class AssignmentBookReference(
    val page: Int?,
    val exercises: List<String>,
    val rawReference: String,
    val confidence: Double
)

data class ResolvedBookAssignment(
    val book: BookAsset?,
    val reference: AssignmentBookReference,
    val status: ResolutionStatus,
    val confidence: Double,
    val reason: String
)

enum class ResolutionStatus {
    RESOLVED,
    NEEDS_CONFIRMATION,
    PAGE_ONLY,
    NO_MATCH
}

interface BookCatalog {
    fun allBooks(): List<BookAsset>
}

class InMemoryBookCatalog(
    private val books: List<BookAsset>
) : BookCatalog {
    override fun allBooks(): List<BookAsset> = books
}

object AssignmentReferenceParser {
    private val pagePatterns = listOf(
        Regex("(?i)(?:\\bS(?:eite)?\\.?|\\bPage|\\bP\\.?|صفحة)\\s*[:#-]?\\s*(\\d{1,4})"),
        Regex("(?i)\\bpp?\\.?\\s*(\\d{1,4})")
    )

    private val exercisePattern = Regex(
        "(?i)(?:Aufg(?:abe)?\\.?|Nr\\.?|No\\.?|Exercise|Ex\\.?|تمرين|سؤال)\\s*[:#-]?\\s*([0-9]+[a-z]?(?:\\s*[-–]\\s*[0-9]+[a-z]?)?)"
    )

    fun parse(vararg textParts: String): AssignmentBookReference? {
        val raw = textParts
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()

        if (raw.isBlank()) return null

        val page = pagePatterns.firstNotNullOfOrNull { pattern ->
            pattern.find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }

        val exercises = exercisePattern.findAll(raw)
            .mapNotNull { it.groupValues.getOrNull(1)?.replace(" ", "") }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

        if (page == null && exercises.isEmpty()) return null

        val confidence = when {
            page != null && exercises.isNotEmpty() -> 0.96
            page != null -> 0.82
            else -> 0.68
        }

        return AssignmentBookReference(
            page = page,
            exercises = exercises,
            rawReference = raw,
            confidence = confidence
        )
    }
}

class BookAssignmentResolver(
    private val catalog: BookCatalog
) {
    fun resolve(
        subjectHint: String,
        title: String,
        body: String
    ): ResolvedBookAssignment? {
        val reference = AssignmentReferenceParser.parse(title, body) ?: return null
        val normalizedSubject = normalize(subjectHint)

        val candidates = catalog.allBooks().map { book ->
            val subjectScore = similarity(normalizedSubject, normalize(book.subject))
            val text = normalize("$title $body")
            val aliasScore = (setOf(book.title) + book.aliases)
                .maxOfOrNull { alias -> if (normalize(alias).isNotBlank() && text.contains(normalize(alias))) 1.0 else 0.0 }
                ?: 0.0
            book to maxOf(subjectScore, aliasScore)
        }.sortedByDescending { it.second }

        val best = candidates.firstOrNull()
        if (best == null || best.second < 0.45) {
            return ResolvedBookAssignment(
                book = null,
                reference = reference,
                status = if (reference.page != null) ResolutionStatus.PAGE_ONLY else ResolutionStatus.NO_MATCH,
                confidence = reference.confidence * 0.6,
                reason = "تم اكتشاف رقم الصفحة/التمرين لكن الكتاب غير محدد بعد"
            )
        }

        val runnerUp = candidates.getOrNull(1)?.second ?: 0.0
        val ambiguous = best.second - runnerUp < 0.15 && runnerUp >= 0.45
        val finalConfidence = (reference.confidence * 0.65) + (best.second * 0.35)

        return ResolvedBookAssignment(
            book = best.first,
            reference = reference,
            status = if (ambiguous) ResolutionStatus.NEEDS_CONFIRMATION else ResolutionStatus.RESOLVED,
            confidence = finalConfidence.coerceIn(0.0, 1.0),
            reason = if (ambiguous) {
                "أكثر من كتاب محتمل للمادة؛ يحتاج تأكيد مرة واحدة"
            } else {
                "تم الربط اعتمادًا على المادة ومرجع الصفحة"
            }
        )
    }

    private fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun similarity(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (a.contains(b) || b.contains(a)) return 0.85
        val aTokens = a.split(" ").filter { it.isNotBlank() }.toSet()
        val bTokens = b.split(" ").filter { it.isNotBlank() }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0
        return aTokens.intersect(bTokens).size.toDouble() / aTokens.union(bTokens).size
    }
}

fun stableBookId(title: String, subject: String, edition: String? = null): String {
    val source = listOf(title, subject, edition.orEmpty()).joinToString("|").lowercase()
    val bytes = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
    return bytes.take(8).joinToString("") { "%02x".format(it) }
}
