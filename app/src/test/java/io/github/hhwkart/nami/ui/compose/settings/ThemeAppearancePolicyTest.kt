package io.github.hhwkart.nami.ui.compose.settings

import io.github.hhwkart.nami.Key
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeAppearancePolicyTest {

    @Test
    fun appearanceDraftKeepsAmoledIndependentFromMode() {
        val classic = ThemeAppearancePolicy.resolve(Key.THEME_MODE_CLASSIC, amoledDark = true)
        val green = ThemeAppearancePolicy.resolve(Key.THEME_MODE_GREEN, amoledDark = false)

        assertTrue(classic.amoledDark)
        assertFalse(classic.dynamicColors)
        assertFalse(green.amoledDark)
        assertFalse(green.dynamicColors)
    }

    @Test
    fun dynamicModeProducesOneConsistentSelection() {
        val selection = ThemeAppearancePolicy.resolve(Key.THEME_MODE_DYNAMIC, amoledDark = true)

        assertTrue(selection.dynamicColors)
        assertTrue(selection.amoledDark)
    }
}
