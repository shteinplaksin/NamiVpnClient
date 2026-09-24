package io.github.hhwkart.nami.domain.repository

import io.github.hhwkart.nami.domain.model.SettingKey
import io.github.hhwkart.nami.domain.model.SettingValue
import io.github.hhwkart.nami.domain.model.SettingsSnapshot
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    fun observeSettings(): Flow<SettingsSnapshot>

    suspend fun readSettings(): SettingsSnapshot

    suspend fun updateSettings(transform: (SettingsSnapshot) -> SettingsSnapshot): SettingsSnapshot

    suspend fun set(key: SettingKey, value: SettingValue): SettingsSnapshot =
        updateSettings { it.withValue(key, value) }

    suspend fun remove(key: SettingKey): SettingsSnapshot =
        updateSettings { it.withoutValue(key) }
}
