package io.github.hhwkart.nami.data.settings

import android.content.Context
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.database.preference.PublicDatabase

object ConfigurationMigrationFactory {

    fun fromLegacyRoom(context: Context): LegacyConfigurationDataMigration {
        val appContext = context.applicationContext
        val legacyDatabase = appContext.getDatabasePath(Key.DB_PUBLIC)

        return LegacyConfigurationDataMigration(
            sourceAvailable = { legacyDatabase.exists() },
            readSource = { PublicDatabase.kvPairDao.all() },
        )
    }
}
