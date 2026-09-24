package io.github.hhwkart.nami.data.settings

import io.github.hhwkart.nami.database.preference.OnPreferenceDataStoreChangeListener
import io.github.hhwkart.nami.database.preference.PublicDatabase
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.domain.model.SettingKey
import io.github.hhwkart.nami.domain.model.SettingValue
import io.github.hhwkart.nami.domain.model.SettingsSnapshot
import io.github.hhwkart.nami.domain.model.StorageMigrationState
import io.github.hhwkart.nami.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Compatibility adapter for the current release boundary. Until every
 * caller and the backup/reset pipeline move to MultiProcess DataStore, the
 * Room-backed facade remains the single authoritative settings store.
 *
 * Keeping the domain port on this adapter prevents a split-brain state where
 * UI/legacy code writes configuration.db while domain use cases read a second
 * Preferences file.
 */
class RoomSettingsRepository : SettingsRepository {
    private val updateMutex = Mutex()
    private val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val preferenceListener = object : OnPreferenceDataStoreChangeListener {
        override fun onPreferenceDataStoreChanged(
            store: androidx.preference.PreferenceDataStore,
            key: String,
        ) {
            changes.tryEmit(Unit)
        }
    }

    init {
        DataStore.configurationStore.registerChangeListener(preferenceListener)
    }

    override fun observeSettings(): Flow<SettingsSnapshot> = flow {
        emit(readSnapshot())
        changes.collect { emit(readSnapshot()) }
    }.flowOn(Dispatchers.IO)

    override suspend fun readSettings(): SettingsSnapshot = withContext(Dispatchers.IO) {
        readSnapshot()
    }

    override suspend fun updateSettings(
        transform: (SettingsSnapshot) -> SettingsSnapshot,
    ): SettingsSnapshot = updateMutex.withLock {
        withContext(Dispatchers.IO) {
            val current = readSnapshot()
            val updated = transform(current)
            PublicDatabase.instance.runInTransaction {
                current.values.keys
                    .subtract(updated.values.keys)
                    .forEach { DataStore.configurationStore.remove(it.value) }
                updated.values.forEach { (key, value) ->
                    putValue(key.value, value)
                }
            }
            changes.tryEmit(Unit)
            updated
        }
    }

    private fun readSnapshot(): SettingsSnapshot {
        val values = PublicDatabase.kvPairDao.all()
            .map { LegacyPreferenceCodec.decode(it) }
            .associate { decoded ->
                SettingKey(decoded.key) to decoded.value.toSettingValue()
            }
        return SettingsSnapshot(
            values = values,
            migrationState = StorageMigrationState.LegacyFallback,
        )
    }

    private fun putValue(key: String, value: SettingValue) {
        when (value) {
            is SettingValue.BooleanValue -> DataStore.configurationStore.putBoolean(key, value.value)
            is SettingValue.FloatValue -> DataStore.configurationStore.putFloat(key, value.value)
            is SettingValue.IntValue -> DataStore.configurationStore.putInt(key, value.value)
            is SettingValue.LongValue -> DataStore.configurationStore.putLong(key, value.value)
            is SettingValue.StringValue -> DataStore.configurationStore.putString(key, value.value)
            is SettingValue.StringSetValue ->
                DataStore.configurationStore.putStringSet(key, value.value.toMutableSet())
        }
    }

    private fun LegacyPreferenceValue.toSettingValue(): SettingValue = when (this) {
        is LegacyPreferenceValue.BooleanValue -> SettingValue.BooleanValue(value)
        is LegacyPreferenceValue.FloatValue -> SettingValue.FloatValue(value)
        is LegacyPreferenceValue.IntValue -> SettingValue.IntValue(value)
        is LegacyPreferenceValue.LongValue -> SettingValue.LongValue(value)
        is LegacyPreferenceValue.StringValue -> SettingValue.StringValue(value)
        is LegacyPreferenceValue.StringSetValue -> SettingValue.StringSetValue(value)
    }
}
