package io.github.hhwkart.nami.data.serialization

import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.domain.model.BackupFormatVersion

/** Keeps the legacy appTheme row active when importing a pre-themeMode backup. */
object LegacyBackupThemePolicy {
    fun shouldRestoreClassic(
        version: BackupFormatVersion,
        settingKeys: Set<String>,
    ): Boolean = version == BackupFormatVersion.LegacyV1 &&
        Key.APP_THEME in settingKeys &&
        Key.THEME_MODE !in settingKeys
}
