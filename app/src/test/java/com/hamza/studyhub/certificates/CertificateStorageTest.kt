package com.hamza.studyhub.certificates

import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CertificateStorageTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun storesOnlyGeneratedSafeBasenames() {
        val root = folder.newFolder().canonicalFile
        val id = CertificateStorage.newId()
        assertEquals(root, CertificateStorage.fileIn(root, id)?.parentFile)
        assertNull(CertificateStorage.fileIn(root, "../$id"))
        assertNull(CertificateStorage.fileIn(root, "import-staging.part"))
    }

    @Test fun copiesTheExactBytesWithinTheLimit() {
        val source = "%PDF-1.7\nsynthetic".toByteArray()
        val output = ByteArrayOutputStream()
        CertificateStorage.copyPdf(source.inputStream(), output, source.size.toLong())
        assertArrayEquals(source, output.toByteArray())
    }

    @Test(expected = CertificateStorage.InvalidPdf::class)
    fun rejectsWrongHeader() {
        CertificateStorage.copyPdf("not a PDF".byteInputStream(), ByteArrayOutputStream())
    }

    @Test(expected = CertificateStorage.PdfTooLarge::class)
    fun rejectsOverLimitStream() {
        CertificateStorage.copyPdf("%PDF-1.7".byteInputStream(), ByteArrayOutputStream(), 6)
    }
}
