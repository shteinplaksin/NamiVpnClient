package io.github.hhwkart.nami.utils

import io.github.hhwkart.nami.Key
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferencePolicyTest {
    @Test
    fun formerGreenDefaultMigratesToMaterialYouWhenSupported() {
        assertEquals(
            Key.THEME_MODE_DYNAMIC,
            ThemePreferencePolicy.migrateThemeMode(Key.THEME_MODE_GREEN, sdkInt = 31),
        )
    }

    @Test
    fun formerGreenDefaultMigratesToClassicWhenMaterialYouIsUnavailable() {
        assertEquals(
            Key.THEME_MODE_CLASSIC,
            ThemePreferencePolicy.migrateThemeMode(Key.THEME_MODE_GREEN, sdkInt = 30),
        )
    }

    @Test
    fun selectedThemeModeIsPreserved() {
        assertEquals(
            Key.THEME_MODE_LIQUID_GLASS,
            ThemePreferencePolicy.migrateThemeMode(Key.THEME_MODE_LIQUID_GLASS, sdkInt = 35),
        )
    }

    @Test
    fun legacyAutoNightModeMigratesToFollowSystem() {
        assertEquals(0, ThemePreferencePolicy.migrateNightTheme(3))
        assertEquals(0, ThemePreferencePolicy.migrateNightTheme(-1))
        assertEquals(2, ThemePreferencePolicy.migrateNightTheme(2))
    }
}
