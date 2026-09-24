package io.github.hhwkart.nami.ui.compose.importexport

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

internal data class ImportTextEntry(
    val name: String,
    val text: String,
)

internal object BoundedImportReader {
    const val MAX_RAW_FILE_BYTES = 8L * 1024 * 1024
    const val MAX_ZIP_ARCHIVE_BYTES = 16L * 1024 * 1024
    const val MAX_ZIP_DECOMPRESSED_BYTES = 32L * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 256
    const val MAX_PROFILE_COUNT = 5_000
    const val PROFILE_COUNT_LIMIT_MESSAGE = "Import contains too many profiles (limit: 5000)"

    private const val RAW_SIZE_LIMIT_MESSAGE = "Import exceeds the 8 MiB size limit"

    fun readRaw(input: InputStream, maxBytes: Long = MAX_RAW_FILE_BYTES): String {
        require(maxBytes >= 0L)
        return readText(
            input = input,
            maxBytes = maxBytes,
            limitMessage = RAW_SIZE_LIMIT_MESSAGE,
        ).text
    }

    fun validateRawText(text: String, maxBytes: Long = MAX_RAW_FILE_BYTES): String {
        require(maxBytes >= 0L)
        var byteCount = 0L
        var index = 0
        while (index < text.length) {
            val character = text[index]
            byteCount += when {
                character.code <= 0x7f -> 1
                character.code <= 0x7ff -> 2
                character.isHighSurrogate() && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                    index++
                    4
                }
                else -> 3
            }
            if (byteCount > maxBytes) throw IllegalArgumentException(RAW_SIZE_LIMIT_MESSAGE)
            index++
        }
        return text
    }

    fun validateProfileCount(profileCount: Int) {
        require(profileCount <= MAX_PROFILE_COUNT) { PROFILE_COUNT_LIMIT_MESSAGE }
    }

    fun readZipEntries(
        input: InputStream,
        maxArchiveBytes: Long = MAX_ZIP_ARCHIVE_BYTES,
        maxDecompressedBytes: Long = MAX_ZIP_DECOMPRESSED_BYTES,
        maxEntries: Int = MAX_ZIP_ENTRIES,
    ): List<ImportTextEntry> {
        require(maxArchiveBytes >= 0L)
        require(maxDecompressedBytes >= 0L)
        require(maxEntries >= 0)

        val entries = mutableListOf<ImportTextEntry>()
        val boundedArchive = BoundedInputStream(
            input = input,
            maxBytes = maxArchiveBytes,
            limitMessage = "ZIP archive exceeds the ${maxArchiveBytes / BYTES_PER_MIB} MiB size limit",
        )
        ZipInputStream(boundedArchive).use { zip ->
            var remainingDecompressedBytes = maxDecompressedBytes
            var entryCount = 0
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > maxEntries) {
                    throw IllegalArgumentException("ZIP archive has too many entries (limit: $maxEntries)")
                }

                val entryLimitMessage =
                    "ZIP contents exceed the ${maxDecompressedBytes / BYTES_PER_MIB} MiB expanded size limit"
                if (entry.isDirectory) {
                    remainingDecompressedBytes -= drain(
                        input = zip,
                        maxBytes = remainingDecompressedBytes,
                        limitMessage = entryLimitMessage,
                    )
                } else {
                    val content = readText(
                        input = zip,
                        maxBytes = remainingDecompressedBytes,
                        limitMessage = entryLimitMessage,
                    )
                    remainingDecompressedBytes -= content.bytesRead
                    entries += ImportTextEntry(entry.name, content.text)
                }
                zip.closeEntry()
            }
        }
        return entries
    }

    private fun readText(input: InputStream, maxBytes: Long, limitMessage: String): BoundedText {
        val bounded = BoundedInputStream(input, maxBytes, limitMessage)
        val reader = InputStreamReader(bounded, StandardCharsets.UTF_8)
        val text = StringBuilder()
        val buffer = CharArray(8 * 1024)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            text.append(buffer, 0, count)
        }
        return BoundedText(text.toString(), bounded.bytesRead)
    }

    private fun drain(input: InputStream, maxBytes: Long, limitMessage: String): Long {
        val bounded = BoundedInputStream(input, maxBytes, limitMessage)
        val buffer = ByteArray(8 * 1024)
        var count: Int
        do {
            count = bounded.read(buffer)
        } while (count >= 0)
        return bounded.bytesRead
    }

    private data class BoundedText(
        val text: String,
        val bytesRead: Long,
    )

    private class BoundedInputStream(
        private val input: InputStream,
        private val maxBytes: Long,
        private val limitMessage: String,
    ) : InputStream() {
        var bytesRead: Long = 0L
            private set

        override fun read(): Int {
            if (bytesRead == maxBytes) {
                if (input.read() < 0) return -1
                throw IllegalArgumentException(limitMessage)
            }
            val value = input.read()
            if (value >= 0) bytesRead++
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (offset < 0 || length < 0 || length > buffer.size - offset) {
                throw IndexOutOfBoundsException()
            }
            if (length == 0) return 0
            if (bytesRead == maxBytes) return read()

            val remaining = maxBytes - bytesRead
            val count = input.read(buffer, offset, minOf(length.toLong(), remaining).toInt())
            if (count > 0) bytesRead += count
            return count
        }

        override fun close() {
            input.close()
        }
    }

    private const val BYTES_PER_MIB = 1024L * 1024L
}
