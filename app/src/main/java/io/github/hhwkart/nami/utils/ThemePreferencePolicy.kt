package io.github.hhwkart.nami.utils

import io.github.hhwkart.nami.Key

object ThemePreferencePolicy {
    fun migrateThemeMode(mode: Int, sdkInt: Int): Int = when (mode) {
        Key.THEME_MODE_GREEN -> if (sdkInt >= 31) {
            Key.THEME_MODE_DYNAMIC
        } else {
            Key.THEME_MODE_CLASSIC
        }

        else -> mode
    }

    fun migrateNightTheme(mode: Int): Int = mode.takeIf { it in 0..2 } ?: 0
}
