package io.github.hhwkart.nami.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageBootstrapTest {

    @Test
    fun awaitsDataStoreWithoutUsingLegacyFallback() = runTest {
        val cause = IllegalStateException("legacy should not be consulted")
        val bootstrap = StorageBootstrap(
            store = InMemoryDataStore(emptyPreferences()),
            legacyFallback = object : LegacySettingsFallback {
                override suspend fun isAvailable(): Boolean = error(cause.message!!)
            },
        )

        assertSame(StorageBootstrapState.Ready, bootstrap.awaitReady())
        assertSame(StorageBootstrapState.Ready, bootstrap.awaitReady())
    }

    @Test
    fun keepsLegacyFallbackWhenDataStoreCannotBeRead() = runTest {
        val failure = IllegalStateException("corrupt preferences")
        val bootstrap = StorageBootstrap(
            store = InMemoryDataStore(failure = failure),
            legacyFallback = object : LegacySettingsFallback {
                override suspend fun isAvailable(): Boolean = true
            },
        )

        val state = bootstrap.awaitReady()
        assertTrue(state is StorageBootstrapState.LegacyFallback)
        assertSame(state, bootstrap.awaitReady())
    }

    @Test
    fun reportsFailureWhenNeitherStoreNorLegacySourceIsAvailable() = runTest {
        val failure = IllegalStateException("corrupt preferences")
        val bootstrap = StorageBootstrap(
            store = InMemoryDataStore(failure = failure),
            legacyFallback = object : LegacySettingsFallback {
                override suspend fun isAvailable(): Boolean = false
            },
        )

        val state = bootstrap.awaitReady()
        assertTrue(state is StorageBootstrapState.Failed)
    }

    private class InMemoryDataStore(
        initial: Preferences = emptyPreferences(),
        failure: Throwable? = null,
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)

        override val data: Flow<Preferences> = failure?.let { error ->
            flow { throw error }
        } ?: state

        override suspend fun updateData(
            transform: suspend (Preferences) -> Preferences,
        ): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }
}
