package io.github.hhwkart.nami.data.settings

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.hhwkart.nami.database.preference.KeyValuePair
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

class LegacyConfigurationMigrationTest {

    @Suppress("DEPRECATION")
    @Test
    fun decodesAllLegacyPreferenceTypesWithoutChangingExplicitZeroValues() {
        assertEquals(false, LegacyPreferenceCodec.decode(KeyValuePair("bool").put(false)).value.let {
            (it as LegacyPreferenceValue.BooleanValue).value
        })
        assertEquals(1.5f, (LegacyPreferenceCodec.decode(KeyValuePair("float").put(1.5f)).value as LegacyPreferenceValue.FloatValue).value)
        assertEquals(0, (LegacyPreferenceCodec.decode(KeyValuePair("int").put(0)).value as LegacyPreferenceValue.IntValue).value)
        assertEquals(0L, (LegacyPreferenceCodec.decode(KeyValuePair("long").put(0L)).value as LegacyPreferenceValue.LongValue).value)
        assertEquals("", (LegacyPreferenceCodec.decode(KeyValuePair("string").put("")).value as LegacyPreferenceValue.StringValue).value)
        assertEquals(
            setOf("one", "ёж"),
            (LegacyPreferenceCodec.decode(KeyValuePair("set").put(setOf("one", "ёж"))).value
                as LegacyPreferenceValue.StringSetValue).value,
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun migratesTypedValuesIdempotentlyAndWritesCompletionMarkers() = runBlocking {
        val rows = listOf(
            KeyValuePair("enabled").put(true),
            KeyValuePair("count").put(7),
            KeyValuePair("timeout").put(9L),
            KeyValuePair("name").put("Nami"),
            validUnicodeSet("labels", setOf("one", "ёж")),
        )
        val migration = LegacyConfigurationDataMigration(
            sourceAvailable = { true },
            readSource = { rows },
        )

        val migrated = migration.migrate(emptyPreferences())
        assertTrue(migration.shouldMigrate(emptyPreferences()))
        assertEquals(true, migrated[booleanPreferencesKey("enabled")])
        assertEquals(7, migrated[intPreferencesKey("count")])
        assertEquals(9L, migrated[longPreferencesKey("timeout")])
        assertEquals("Nami", migrated[stringPreferencesKey("name")])
        assertEquals(setOf("one", "ёж"), migrated[stringSetPreferencesKey("labels")])
        assertEquals(
            ConfigurationMigrationKeys.completeState,
            migrated[ConfigurationMigrationKeys.migrationState],
        )
        assertEquals(
            ConfigurationMigrationKeys.migrationId,
            migrated[ConfigurationMigrationKeys.migrationIdKey],
        )
        assertFalse(migrated[ConfigurationMigrationKeys.sourceDigest].isNullOrBlank())

        val rerun = migration.migrate(migrated)
        assertEquals(migrated, rerun)
        assertFalse(migration.shouldMigrate(migrated))
    }

    private fun validUnicodeSet(key: String, values: Set<String>): KeyValuePair {
        val row = KeyValuePair(key)
        val bytes = values.fold(ByteArray(0)) { result, value ->
            val encoded = value.toByteArray()
            result + ByteBuffer.allocate(4).putInt(encoded.size).array() + encoded
        }
        row.valueType = KeyValuePair.TYPE_STRING_SET
        row.value = bytes
        return row
    }
}
