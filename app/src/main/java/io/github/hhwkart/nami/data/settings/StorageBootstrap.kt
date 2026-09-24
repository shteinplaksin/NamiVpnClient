package io.github.hhwkart.nami.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hhwkart.nami.domain.model.StorageMigrationState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface StorageBootstrapState {
    data object NotStarted : StorageBootstrapState
    data object Starting : StorageBootstrapState
    data object Ready : StorageBootstrapState

    data class LegacyFallback(val cause: Throwable) : StorageBootstrapState

    data class Failed(val cause: Throwable) : StorageBootstrapState
}

interface LegacySettingsFallback {
    suspend fun isAvailable(): Boolean
}

@Singleton
class RoomLegacySettingsFallback @Inject constructor(
    @ApplicationContext
    private val context: android.content.Context,
) : LegacySettingsFallback {
    private val databaseFile = context.applicationContext
        .getDatabasePath(io.github.hhwkart.nami.Key.DB_PUBLIC)

    override suspend fun isAvailable(): Boolean = databaseFile.exists()
}

/**
 * Async readiness gate for settings-dependent startup. It never blocks the
 * main thread and never deletes or rewrites the legacy Room source.
 */
@Singleton
class StorageBootstrap @Inject constructor(
    private val store: DataStore<Preferences>,
    private val legacyFallback: LegacySettingsFallback,
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<StorageBootstrapState>(StorageBootstrapState.NotStarted)

    val state: StateFlow<StorageBootstrapState> = mutableState.asStateFlow()

    suspend fun awaitReady(): StorageBootstrapState = mutex.withLock {
        when (val current = mutableState.value) {
            StorageBootstrapState.Ready,
            is StorageBootstrapState.LegacyFallback,
            is StorageBootstrapState.Failed,
            -> return@withLock current

            StorageBootstrapState.NotStarted,
            StorageBootstrapState.Starting,
            -> Unit
        }

        mutableState.value = StorageBootstrapState.Starting
        try {
            store.data.first()
            StorageBootstrapState.Ready.also { mutableState.value = it }
        } catch (cancelled: CancellationException) {
            // Cancellation is lifecycle control, not a failed migration. Do not
            // poison the singleton state when the caller's scope is cancelled.
            throw cancelled
        } catch (error: Throwable) {
            if (legacyFallback.isAvailable()) {
                StorageBootstrapState.LegacyFallback(error).also { mutableState.value = it }
            } else {
                StorageBootstrapState.Failed(error).also { mutableState.value = it }
            }
        }
    }

    fun migrationState(state: StorageBootstrapState): StorageMigrationState = when (state) {
        StorageBootstrapState.NotStarted -> StorageMigrationState.NotStarted
        StorageBootstrapState.Starting -> StorageMigrationState.InProgress
        StorageBootstrapState.Ready -> StorageMigrationState.Ready
        is StorageBootstrapState.LegacyFallback -> StorageMigrationState.LegacyFallback
        is StorageBootstrapState.Failed -> StorageMigrationState.Failed(
            state.cause.message ?: "Configuration storage failed",
        )
    }
}
