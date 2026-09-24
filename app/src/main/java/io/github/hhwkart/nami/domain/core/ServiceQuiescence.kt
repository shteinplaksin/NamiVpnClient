package io.github.hhwkart.nami.domain.core

import io.github.hhwkart.nami.domain.model.ConnectionState
import kotlinx.coroutines.flow.Flow

enum class QuiescenceReason {
    BackupRestore,
    DataMigration,
    DatabaseMaintenance,
}

sealed interface QuiescenceResult {
    data class Acquired(val lease: QuiescenceLease) : QuiescenceResult

    data class TimedOut(val timeoutMillis: Long) : QuiescenceResult

    data class Failed(val message: String) : QuiescenceResult {
        init {
            require(message.isNotBlank()) { "Quiescence failure message cannot be blank" }
        }
    }
}

interface QuiescenceLease {
    val reason: QuiescenceReason

    suspend fun release(): Result<Unit>
}

/**
 * Coordinates a confirmed stopped service and holds that state while a critical operation runs.
 * Implementations must not report success before the background service is actually quiescent.
 */
interface ServiceQuiescence {
    fun observeState(): Flow<ConnectionState>

    suspend fun acquire(
        reason: QuiescenceReason,
        timeoutMillis: Long,
    ): QuiescenceResult
}
