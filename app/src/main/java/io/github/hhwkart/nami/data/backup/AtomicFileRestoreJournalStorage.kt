package io.github.hhwkart.nami.data.backup

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android persistence for [LogicalRestoreJournal]. AtomicFile protects the
 * small logical pre-image from a process death while it is being replaced;
 * this is intentionally not a copy of either Room database file.
 */
class AtomicFileRestoreJournalStorage(
    private val file: File,
) : RestoreJournalStorage {

    override suspend fun read(): ByteArray? = withContext(Dispatchers.IO) {
        if (!file.exists() && !File("${file.path}.bak").exists()) return@withContext null
        AtomicFile(file).readFully()
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        file.parentFile?.mkdirs()
        val atomicFile = AtomicFile(file)
        val stream = atomicFile.startWrite()
        try {
            stream.write(bytes)
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Throwable) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        AtomicFile(file).delete()
    }
}
