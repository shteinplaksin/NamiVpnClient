package io.github.hhwkart.nami.ui.compose.importexport

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedImportReaderTest {
    @Test
    fun acceptsRawFileAtItsByteLimit() {
        assertEquals(
            "é",
            BoundedImportReader.readRaw(ByteArrayInputStream("é".toByteArray()), maxBytes = 2L),
        )
    }

    @Test
    fun rejectsRawFileAsSoonAsItExceedsItsByteLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.readRaw(ByteArrayInputStream(byteArrayOf(1, 2, 3)), maxBytes = 2L)
        }
    }

    @Test
    fun validatesClipboardTextByUtf8ByteSize() {
        assertEquals("é😀", BoundedImportReader.validateRawText("é😀", maxBytes = 6L))
        val fileError = assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.readRaw(ByteArrayInputStream("é😀".toByteArray()), maxBytes = 5L)
        }
        val clipboardError = assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.validateRawText("é😀", maxBytes = 5L)
        }
        assertEquals(fileError.message, clipboardError.message)
    }

    @Test
    fun validatesTheSharedProfileCountLimit() {
        BoundedImportReader.validateProfileCount(BoundedImportReader.MAX_PROFILE_COUNT)
        val error = assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.validateProfileCount(BoundedImportReader.MAX_PROFILE_COUNT + 1)
        }
        assertEquals(BoundedImportReader.PROFILE_COUNT_LIMIT_MESSAGE, error.message)
    }

    @Test
    fun countsExpandedZipBytesAcrossEntries() {
        val archive = zip("first.txt" to "abc", "second.txt" to "def")

        assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.readZipEntries(
                ByteArrayInputStream(archive),
                maxDecompressedBytes = 5L,
            )
        }
    }

    @Test
    fun readsZipEntryNamesAndUtf8Content() {
        val archive = zip("profiles.txt" to "ss://example\n日本語")

        assertEquals(
            listOf(ImportTextEntry("profiles.txt", "ss://example\n日本語")),
            BoundedImportReader.readZipEntries(ByteArrayInputStream(archive)),
        )
    }

    @Test
    fun enforcesZipEntryCountBeforeParsingEntries() {
        val archive = zip("first.txt" to "a", "second.txt" to "b")

        assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.readZipEntries(
                ByteArrayInputStream(archive),
                maxEntries = 1,
            )
        }
    }

    @Test
    fun enforcesCompressedArchiveSize() {
        val archive = zip("first.txt" to "a")

        assertThrows(IllegalArgumentException::class.java) {
            BoundedImportReader.readZipEntries(
                ByteArrayInputStream(archive),
                maxArchiveBytes = 1L,
            )
        }
    }

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}
