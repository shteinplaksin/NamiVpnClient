package io.github.hhwkart.nami.data.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID

private const val JOURNAL_VERSION = 1

/**
 * Logical pre-image captured before a restore mutates durable state.
 *
 * The entries are the existing opaque, URL-safe base64 row payloads. Keeping
 * them opaque means the journal can provide best-effort rollback for legacy
 * profile/group/rule/settings rows without copying an open SQLite .db file
 * (and therefore without making an unsafe WAL/SHM snapshot). It is not a
 * distributed transaction across Room databases. A Room/DataStore adapter
 * owns the actual mutation and calls [markCommitted] only after every selected
 * category has been verified.
 */
data class LogicalRestorePreImage(
    val profiles: List<String>? = null,
    val groups: List<String>? = null,
    val rules: List<String>? = null,
    val settings: List<String>? = null,
) {
    init {
        require(profiles != null || groups != null || rules != null || settings != null) {
            "A restore journal must contain at least one pre-image section"
        }
    }
}

interface RestoreJournalStorage {
    /** Implementations must replace the journal atomically at this boundary. */
    suspend fun read(): ByteArray?
    suspend fun write(bytes: ByteArray)
    suspend fun delete()
}

@Serializable
private enum class JournalState {
    PREPARED,
    COMMITTED,
}

@Serializable
private data class JournalDocument(
    val journalVersion: Int = JOURNAL_VERSION,
    val operationId: String,
    val createdAtEpochMillis: Long,
    val state: JournalState,
    val profiles: List<String>? = null,
    val groups: List<String>? = null,
    val rules: List<String>? = null,
    val settings: List<String>? = null,
)

data class RestoreJournalEntry(
    val operationId: String,
    val createdAtEpochMillis: Long,
    val state: RestoreJournalEntryState,
    val preImage: LogicalRestorePreImage,
)

enum class RestoreJournalEntryState {
    Prepared,
    Committed,
}

class RestoreJournalFormatException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/**
 * Small transactional journal state machine. It deliberately does not know
 * about Room, SQLite files, Android Context, or the service implementation.
 */
class LogicalRestoreJournal(
    private val storage: RestoreJournalStorage,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val operationIds: () -> String = { UUID.randomUUID().toString() },
    private val json: Json = Json {
        encodeDefaults = true
        prettyPrint = false
        ignoreUnknownKeys = false
    },
) {

    suspend fun prepare(preImage: LogicalRestorePreImage): RestoreJournalEntry {
        val existing = readEntry()
        if (existing != null) {
            if (existing.state == RestoreJournalEntryState.Prepared) {
                if (existing.preImage == preImage) return existing
                throw IllegalStateException(
                    "A different restore operation is already prepared: ${existing.operationId}",
                )
            }
            // A committed marker is safe to discard. The adapter has already
            // verified the new state; clearing it makes the journal reusable.
            storage.delete()
        }

        val document = JournalDocument(
            operationId = operationIds().also { require(it.isNotBlank()) },
            createdAtEpochMillis = nowEpochMillis(),
            state = JournalState.PREPARED,
            profiles = preImage.profiles,
            groups = preImage.groups,
            rules = preImage.rules,
            settings = preImage.settings,
        )
        write(document)
        return document.toEntry()
    }

    suspend fun pending(): RestoreJournalEntry? = readEntry()
        ?.takeIf { it.state == RestoreJournalEntryState.Prepared }

    suspend fun markCommitted(operationId: String): RestoreJournalEntry {
        val current = readEntry()
            ?: throw IllegalStateException("No restore journal exists")
        check(current.operationId == operationId) {
            "Restore journal operation mismatch: expected ${current.operationId}, got $operationId"
        }
        check(current.state == RestoreJournalEntryState.Prepared) {
            "Restore journal is already committed: $operationId"
        }

        val committed = JournalDocument(
            journalVersion = JOURNAL_VERSION,
            operationId = current.operationId,
            createdAtEpochMillis = current.createdAtEpochMillis,
            state = JournalState.COMMITTED,
            profiles = current.preImage.profiles,
            groups = current.preImage.groups,
            rules = current.preImage.rules,
            settings = current.preImage.settings,
        )
        write(committed)
        return committed.toEntry()
    }

    suspend fun clearCommitted(operationId: String) {
        val current = readEntry()
            ?: throw IllegalStateException("No restore journal exists")
        check(current.operationId == operationId) {
            "Restore journal operation mismatch: expected ${current.operationId}, got $operationId"
        }
        check(current.state == RestoreJournalEntryState.Committed) {
            "Cannot clear an uncommitted restore journal: $operationId"
        }
        storage.delete()
    }

    suspend fun clearPreparedAfterRollback(operationId: String) {
        val current = readEntry()
            ?: throw IllegalStateException("No restore journal exists")
        check(current.operationId == operationId) {
            "Restore journal operation mismatch: expected ${current.operationId}, got $operationId"
        }
        check(current.state == RestoreJournalEntryState.Prepared) {
            "Cannot clear a non-prepared restore journal: $operationId"
        }
        storage.delete()
    }

    private suspend fun readEntry(): RestoreJournalEntry? {
        val bytes = storage.read() ?: return null
        val document = try {
            json.decodeFromString<JournalDocument>(bytes.toString(Charsets.UTF_8))
        } catch (error: SerializationException) {
            throw RestoreJournalFormatException("Invalid restore journal", error)
        }
        if (document.journalVersion != JOURNAL_VERSION) {
            throw RestoreJournalFormatException(
                "Unsupported restore journal version: ${document.journalVersion}",
            )
        }
        return document.toEntry()
    }

    private suspend fun write(document: JournalDocument) {
        storage.write(
            json.encodeToString(JournalDocument.serializer(), document)
                .toByteArray(Charsets.UTF_8),
        )
    }

    private fun JournalDocument.toEntry() = RestoreJournalEntry(
        operationId = operationId,
        createdAtEpochMillis = createdAtEpochMillis,
        state = when (state) {
            JournalState.PREPARED -> RestoreJournalEntryState.Prepared
            JournalState.COMMITTED -> RestoreJournalEntryState.Committed
        },
        preImage = LogicalRestorePreImage(
            profiles = profiles,
            groups = groups,
            rules = rules,
            settings = settings,
        ),
    )

}
