package io.github.hhwkart.nami.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.hhwkart.nami.database.preference.KeyValuePair
import java.security.MessageDigest

/**
 * One-time conversion from the legacy Room preference table to Preferences
 * DataStore. The source provider is injected so migration tests do not need to
 * construct the process-global PublicDatabase singleton.
 */
class LegacyConfigurationDataMigration(
    private val sourceAvailable: suspend () -> Boolean,
    private val readSource: suspend () -> List<KeyValuePair>,
) : DataMigration<Preferences> {

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        return currentData[ConfigurationMigrationKeys.migrationState] !=
            ConfigurationMigrationKeys.completeState && sourceAvailable()
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val decoded = readSource()
            .map(LegacyPreferenceCodec::decode)
            .sortedBy { it.key }

        val migrated = currentData.toMutablePreferences()
        decoded.forEach { entry ->
            when (val value = entry.value) {
                is LegacyPreferenceValue.BooleanValue ->
                    migrated[androidx.datastore.preferences.core.booleanPreferencesKey(entry.key)] =
                        value.value

                is LegacyPreferenceValue.FloatValue ->
                    migrated[floatPreferencesKey(entry.key)] = value.value

                is LegacyPreferenceValue.IntValue ->
                    migrated[intPreferencesKey(entry.key)] = value.value

                is LegacyPreferenceValue.LongValue ->
                    migrated[longPreferencesKey(entry.key)] = value.value

                is LegacyPreferenceValue.StringValue ->
                    migrated[stringPreferencesKey(entry.key)] = value.value

                is LegacyPreferenceValue.StringSetValue ->
                    migrated[stringSetPreferencesKey(entry.key)] = value.value
            }
        }

        migrated[ConfigurationMigrationKeys.migrationState] =
            ConfigurationMigrationKeys.completeState
        migrated[ConfigurationMigrationKeys.migrationIdKey] =
            ConfigurationMigrationKeys.migrationId
        migrated[ConfigurationMigrationKeys.sourceDigest] = digest(decoded)
        return migrated.toPreferences()
    }

    /** Deliberately keeps configuration.db intact during the fallback release. */
    override suspend fun cleanUp() = Unit

    private fun digest(entries: List<DecodedLegacyPreference>): String {
        val canonical = buildString {
            entries.forEach { entry ->
                append(entry.key)
                append('|')
                append(entry.value)
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }
}
