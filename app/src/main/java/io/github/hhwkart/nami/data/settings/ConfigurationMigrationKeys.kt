package io.github.hhwkart.nami.data.settings

import androidx.datastore.preferences.core.stringPreferencesKey

object ConfigurationMigrationKeys {
    const val migrationId = "room-configuration-to-preferences-v1"
    const val completeState = "complete"
    const val migrationStateName = "__nami_storage_migration_state"
    const val migrationIdName = "__nami_storage_migration_id"
    const val sourceDigestName = "__nami_storage_source_digest"

    val migrationState = stringPreferencesKey(migrationStateName)
    val migrationIdKey = stringPreferencesKey(migrationIdName)
    val sourceDigest = stringPreferencesKey(sourceDigestName)
}
