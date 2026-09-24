package io.github.hhwkart.nami.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.hhwkart.nami.domain.model.SettingKey
import io.github.hhwkart.nami.domain.model.SettingValue
import io.github.hhwkart.nami.domain.model.SettingsSnapshot
import io.github.hhwkart.nami.domain.model.StorageMigrationState
import io.github.hhwkart.nami.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class DataStoreSettingsRepository(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    override fun observeSettings(): Flow<SettingsSnapshot> = store.data.map(::toSnapshot)

    override suspend fun readSettings(): SettingsSnapshot = observeSettings().first()

    override suspend fun updateSettings(
        transform: (SettingsSnapshot) -> SettingsSnapshot,
    ): SettingsSnapshot {
        var updated = SettingsSnapshot()
        store.updateData { preferences ->
            val current = toSnapshot(preferences)
            updated = transform(current)
            val mutable = preferences.toMutablePreferences()
            val currentKeys = current.values.keys
            val updatedKeys = updated.values.keys

            currentKeys.subtract(updatedKeys).forEach { key ->
                removeKey(mutable, key.value, current.values.getValue(key))
            }
            updated.values.forEach { (key, value) ->
                putValue(mutable, key.value, value)
            }
            mutable.toPreferences()
        }
        return updated
    }

    private fun toSnapshot(preferences: Preferences): SettingsSnapshot {
        val values = preferences.asMap()
            .filterKeys { it.name !in reservedNames }
            .mapNotNull { (key, raw) ->
                settingValue(key.name, raw)?.let { SettingKey(key.name) to it }
            }
            .toMap()

        val migrationState = when (preferences[ConfigurationMigrationKeys.migrationState]) {
            ConfigurationMigrationKeys.completeState -> StorageMigrationState.Ready
            null -> StorageMigrationState.NotStarted
            else -> StorageMigrationState.InProgress
        }
        return SettingsSnapshot(values = values, migrationState = migrationState)
    }

    private fun settingValue(key: String, raw: Any): SettingValue? = when (raw) {
        is Boolean -> SettingValue.BooleanValue(raw)
        is Float -> SettingValue.FloatValue(raw)
        is Int -> SettingValue.IntValue(raw)
        is Long -> SettingValue.LongValue(raw)
        is String -> SettingValue.StringValue(raw)
        is Set<*> -> SettingValue.StringSetValue(raw.filterIsInstance<String>().toSet())
        else -> null
    }

    private fun putValue(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: String,
        value: SettingValue,
    ) {
        when (value) {
            is SettingValue.BooleanValue -> preferences[booleanPreferencesKey(key)] = value.value
            is SettingValue.FloatValue -> preferences[floatPreferencesKey(key)] = value.value
            is SettingValue.IntValue -> preferences[intPreferencesKey(key)] = value.value
            is SettingValue.LongValue -> preferences[longPreferencesKey(key)] = value.value
            is SettingValue.StringValue -> preferences[stringPreferencesKey(key)] = value.value
            is SettingValue.StringSetValue ->
                preferences[stringSetPreferencesKey(key)] = value.value
        }
    }

    private fun removeKey(
        preferences: androidx.datastore.preferences.core.MutablePreferences,
        key: String,
        value: SettingValue,
    ) {
        when (value) {
            is SettingValue.BooleanValue -> preferences.remove(booleanPreferencesKey(key))
            is SettingValue.FloatValue -> preferences.remove(floatPreferencesKey(key))
            is SettingValue.IntValue -> preferences.remove(intPreferencesKey(key))
            is SettingValue.LongValue -> preferences.remove(longPreferencesKey(key))
            is SettingValue.StringValue -> preferences.remove(stringPreferencesKey(key))
            is SettingValue.StringSetValue -> preferences.remove(stringSetPreferencesKey(key))
        }
    }

    private companion object {
        val reservedNames = setOf(
            ConfigurationMigrationKeys.migrationStateName,
            ConfigurationMigrationKeys.migrationIdName,
            ConfigurationMigrationKeys.sourceDigestName,
        )
    }
}
