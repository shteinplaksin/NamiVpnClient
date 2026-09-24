package io.github.hhwkart.nami.ui.compose.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicThemesTest {

    @Test
    fun classicThemes_count_matches21LegacyThemes() {
        assertEquals(21, ClassicThemes.ALL.size)
        val ids = ClassicThemes.ALL.map { it.id }
        assertEquals((1..21).toList(), ids)
    }

    @Test
    fun classicThemes_fallback_returnsPinkSsr() {
        // Fallback for 0, negative, or out-of-bounds IDs
        assertEquals(2, ClassicThemes.getThemeInfo(0).id)
        assertEquals("Pink SSR", ClassicThemes.getThemeName(0))
        assertEquals(2, ClassicThemes.getThemeInfo(-1).id)
        assertEquals(2, ClassicThemes.getThemeInfo(999).id)
    }

    @Test
    fun classicThemes_lightScheme_all21ThemesGenerateValidContrast() {
        for (theme in ClassicThemes.ALL) {
            val scheme = ClassicThemes.buildClassicColorScheme(theme.id, isDark = false, isAmoled = false)
            assertNotNull("Light scheme for ${theme.name} must not be null", scheme)
            val primContrast = ClassicThemes.calculateContrast(scheme.onPrimary, scheme.primary)
            assertTrue(
                "Theme ${theme.name} light onPrimary contrast $primContrast must be >= 3.0",
                primContrast >= 3.0,
            )
            val primContainerContrast = ClassicThemes.calculateContrast(scheme.onPrimaryContainer, scheme.primaryContainer)
            assertTrue(
                "Theme ${theme.name} light onPrimaryContainer contrast $primContainerContrast must be >= 4.0",
                primContainerContrast >= 4.0,
            )
            val secContainerContrast = ClassicThemes.calculateContrast(scheme.onSecondaryContainer, scheme.secondaryContainer)
            assertTrue(
                "Theme ${theme.name} light onSecondaryContainer contrast $secContainerContrast must be >= 4.0",
                secContainerContrast >= 4.0,
            )
        }
    }

    @Test
    fun classicThemes_darkScheme_all21ThemesGenerateValidContrast() {
        for (theme in ClassicThemes.ALL) {
            val scheme = ClassicThemes.buildClassicColorScheme(theme.id, isDark = true, isAmoled = false)
            assertNotNull("Dark scheme for ${theme.name} must not be null", scheme)
            val primContrast = ClassicThemes.calculateContrast(scheme.onPrimary, scheme.primary)
            assertTrue(
                "Theme ${theme.name} dark onPrimary contrast $primContrast must be >= 3.0",
                primContrast >= 3.0,
            )
            val primContainerContrast = ClassicThemes.calculateContrast(scheme.onPrimaryContainer, scheme.primaryContainer)
            assertTrue(
                "Theme ${theme.name} dark onPrimaryContainer contrast $primContainerContrast must be >= 4.0",
                primContainerContrast >= 4.0,
            )
        }
    }

    @Test
    fun classicThemes_amoledDark_pureBlackSurfaces() {
        for (theme in ClassicThemes.ALL) {
            val scheme = ClassicThemes.buildClassicColorScheme(theme.id, isDark = true, isAmoled = true)
            assertEquals("AMOLED surface for ${theme.name} must be black", Color.Black, scheme.surface)
            assertEquals("AMOLED background for ${theme.name} must be black", Color.Black, scheme.background)
            assertEquals("AMOLED surfaceDim for ${theme.name} must be black", Color.Black, scheme.surfaceDim)
            assertEquals("AMOLED surfaceContainerLowest for ${theme.name} must be black", Color.Black, scheme.surfaceContainerLowest)
        }
    }

    @Test
    fun classicThemes_yellowAndLime_lightModeHaveDarkText() {
        // Yellow (id=14) and Lime (id=13) are high-luminance primaries where white text is unreadable
        val yellowScheme = ClassicThemes.buildClassicColorScheme(14, isDark = false, isAmoled = false)
        assertNotEquals("Yellow light mode onPrimary must not be white", Color.White, yellowScheme.onPrimary)
        val yellowContrast = ClassicThemes.calculateContrast(yellowScheme.onPrimary, yellowScheme.primary)
        assertTrue("Yellow onPrimary contrast must be >= 4.5, got $yellowContrast", yellowContrast >= 4.5)

        val limeScheme = ClassicThemes.buildClassicColorScheme(13, isDark = false, isAmoled = false)
        assertNotEquals("Lime light mode onPrimary must not be white", Color.White, limeScheme.onPrimary)
        val limeContrast = ClassicThemes.calculateContrast(limeScheme.onPrimary, limeScheme.primary)
        assertTrue("Lime onPrimary contrast must be >= 4.5, got $limeContrast", limeContrast >= 4.5)
    }
}
