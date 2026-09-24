package io.github.hhwkart.nami.data.settings

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.DataStore
import androidx.datastore.core.MultiProcessDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesFileSerializer
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Process-local holder for the one shared configuration DataStore file.
 * MultiProcessDataStoreFactory must be used in both the main and :bg process.
 */
object MultiProcessConfigurationStore {
    private const val fileName = "configuration"

    @Volatile
    private var instance: DataStore<Preferences>? = null

    fun get(
        context: Context,
        migrations: List<DataMigration<Preferences>> = emptyList(),
    ): DataStore<Preferences> {
        instance?.let { return it }

        return synchronized(this) {
            instance ?: MultiProcessDataStoreFactory.create(
                serializer = PreferencesFileSerializer,
                migrations = migrations,
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
                produceFile = {
                    context.applicationContext.preferencesDataStoreFile(fileName)
                },
            ).also { instance = it }
        }
    }
}
