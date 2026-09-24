package io.github.hhwkart.nami.data.backup

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogicalRestoreJournalTest {

    @Test
    fun prepareIsIdempotentForTheSamePreImage() = runTest {
        val storage = InMemoryStorage()
        val journal = journal(storage)
        val preImage = LogicalRestorePreImage(
            profiles = listOf("profile-before"),
            settings = listOf("settings-before"),
        )

        val first = journal.prepare(preImage)
        val second = journal.prepare(preImage)

        assertEquals(first, second)
        assertEquals(first, journal.pending())
    }

    @Test
    fun committedJournalIsRecoverableUntilExplicitlyCleared() = runTest {
        val storage = InMemoryStorage()
        val journal = journal(storage)
        val prepared = journal.prepare(LogicalRestorePreImage(rules = listOf("rules-before")))

        val committed = journal.markCommitted(prepared.operationId)

        assertEquals(RestoreJournalEntryState.Committed, committed.state)
        assertNull(journal.pending())

        journal.clearCommitted(prepared.operationId)
        assertNull(journal.pending())
    }

    @Test
    fun aDifferentPreparedOperationCannotOverwriteThePreImage() = runTest {
        val journal = journal(InMemoryStorage())
        journal.prepare(LogicalRestorePreImage(groups = listOf("group-before")))

        val error = try {
            journal.prepare(LogicalRestorePreImage(groups = listOf("other-group")))
            null
        } catch (throwable: Throwable) {
            throwable
        }
        assertTrue(error is IllegalStateException)
    }

    @Test
    fun preparedJournalCanBeClearedOnlyAfterRollback() = runTest {
        val storage = InMemoryStorage()
        val journal = journal(storage)
        val prepared = journal.prepare(LogicalRestorePreImage(settings = emptyList()))

        journal.clearPreparedAfterRollback(prepared.operationId)

        assertNull(journal.pending())
    }

    @Test
    fun journalUsesLogicalRowsAndNotAnOpenDatabaseFile() = runTest {
        val storage = InMemoryStorage()
        val journal = journal(storage)
        val entry = journal.prepare(
            LogicalRestorePreImage(
                profiles = listOf("opaque-profile-blob"),
                groups = emptyList(),
                rules = listOf("opaque-rule-blob"),
            ),
        )

        assertTrue(entry.preImage.profiles!!.single() == "opaque-profile-blob")
        assertTrue(storage.bytes!!.toString(Charsets.UTF_8).contains("opaque-profile-blob"))
        assertTrue(!storage.bytes!!.toString(Charsets.UTF_8).contains(".db"))
    }

    private fun journal(storage: InMemoryStorage) = LogicalRestoreJournal(
        storage = storage,
        nowEpochMillis = { 1234L },
        operationIds = { "operation-1" },
    )

    private class InMemoryStorage : RestoreJournalStorage {
        var bytes: ByteArray? = null

        override suspend fun read(): ByteArray? = bytes?.copyOf()

        override suspend fun write(bytes: ByteArray) {
            this.bytes = bytes.copyOf()
        }

        override suspend fun delete() {
            bytes = null
        }
    }

}
