package com.hamza.studyhub.certificates

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

internal object CertificateStorage {
    const val MAX_BYTES = 20L * 1024 * 1024
    const val MAX_PAGES = 500
    private val namePattern = Regex("[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}\\.pdf")
    private val signature = "%PDF-".toByteArray(Charsets.US_ASCII)

    fun newId() = "${UUID.randomUUID()}.pdf"

    fun fileIn(root: File, id: String): File? {
        if (!namePattern.matches(id)) return null
        return runCatching {
            val folder = root.canonicalFile
            val file = File(folder, id)
            file.takeIf { it.canonicalFile == it }
        }.getOrNull()
    }

    fun copyPdf(input: InputStream, output: OutputStream, maxBytes: Long = MAX_BYTES) {
        require(maxBytes >= signature.size)
        val header = ByteArray(signature.size)
        for (i in header.indices) {
            val value = input.read()
            if (value < 0) throw InvalidPdf()
            header[i] = value.toByte()
        }
        if (!header.contentEquals(signature)) throw InvalidPdf()
        output.write(header)
        var total = header.size.toLong()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) throw InvalidPdf()
            total += count
            if (total > maxBytes) throw PdfTooLarge()
            output.write(buffer, 0, count)
        }
    }

    class InvalidPdf : Exception()
    class PdfTooLarge : Exception()
}
