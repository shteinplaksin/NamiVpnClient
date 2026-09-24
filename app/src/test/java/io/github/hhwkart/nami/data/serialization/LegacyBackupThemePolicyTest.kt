package io.github.hhwkart.nami.data.serialization

import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.domain.model.BackupFormatVersion
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyBackupThemePolicyTest {

    @Test
    fun v1PaletteWithoutThemeModeRestoresClassicMode() {
        assertTrue(
            LegacyBackupThemePolicy.shouldRestoreClassic(
                BackupFormatVersion.LegacyV1,
                setOf(Key.APP_THEME),
            ),
        )
    }

    @Test
    fun currentOrExplicitThemeModeIsNotRewritten() {
        assertFalse(
            LegacyBackupThemePolicy.shouldRestoreClassic(
                BackupFormatVersion.CurrentV2,
                setOf(Key.APP_THEME),
            ),
        )
        assertFalse(
            LegacyBackupThemePolicy.shouldRestoreClassic(
                BackupFormatVersion.LegacyV1,
                setOf(Key.APP_THEME, Key.THEME_MODE),
            ),
        )
    }
}
