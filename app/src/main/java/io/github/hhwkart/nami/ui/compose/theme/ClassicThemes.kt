package io.github.hhwkart.nami.ui.compose.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.pow

data class ClassicThemeInfo(
    val id: Int,
    val name: String,
    val primary: Color,
    val accent: Color,
)

object ClassicThemes {

    val ALL: List<ClassicThemeInfo> = listOf(
        ClassicThemeInfo(1, "Red", Color(0xFFF44336), Color(0xFFFF5252)),
        ClassicThemeInfo(2, "Pink SSR", Color(0xFFFF80AB), Color(0xFFFF4081)),
        ClassicThemeInfo(3, "Pink", Color(0xFFE91E63), Color(0xFFFF4081)),
        ClassicThemeInfo(4, "Purple", Color(0xFF9C27B0), Color(0xFFE040FB)),
        ClassicThemeInfo(5, "Deep Purple", Color(0xFF673AB7), Color(0xFF7C4DFF)),
        ClassicThemeInfo(6, "Indigo", Color(0xFF3F51B5), Color(0xFF536DFE)),
        ClassicThemeInfo(7, "Blue", Color(0xFF2196F3), Color(0xFF448AFF)),
        ClassicThemeInfo(8, "Light Blue", Color(0xFF03A9F4), Color(0xFF40C4FF)),
        ClassicThemeInfo(9, "Cyan", Color(0xFF00BCD4), Color(0xFF18FFFF)),
        ClassicThemeInfo(10, "Teal", Color(0xFF009688), Color(0xFF64FFDA)),
        ClassicThemeInfo(11, "Green", Color(0xFF4CAF50), Color(0xFF69F0AE)),
        ClassicThemeInfo(12, "Light Green", Color(0xFF8BC34A), Color(0xFFB2FF59)),
        ClassicThemeInfo(13, "Lime", Color(0xFFCDDC39), Color(0xFFEEFF41)),
        ClassicThemeInfo(14, "Yellow", Color(0xFFFFEB3B), Color(0xFFFFFF00)),
        ClassicThemeInfo(15, "Amber", Color(0xFFFFC107), Color(0xFFFFD740)),
        ClassicThemeInfo(16, "Orange", Color(0xFFFF9800), Color(0xFFFFAB40)),
        ClassicThemeInfo(17, "Deep Orange", Color(0xFFFF5722), Color(0xFFFF6E40)),
        ClassicThemeInfo(18, "Brown", Color(0xFF795548), Color(0xFFBCAAA4)),
        ClassicThemeInfo(19, "Grey", Color(0xFF9E9E9E), Color(0xFFE0E0E0)),
        ClassicThemeInfo(20, "Blue Grey", Color(0xFF607D8B), Color(0xFFB0BEC5)),
        ClassicThemeInfo(21, "Black", Color(0xFF212121), Color(0xFF757575)),
    )

    fun getThemeInfo(id: Int): ClassicThemeInfo {
        return ALL.find { it.id == id } ?: ALL[1] // Pink SSR (legacy default)
    }

    fun getThemeName(id: Int): String = getThemeInfo(id).name

    fun colorToHsl(color: Color): FloatArray {
        val r = color.red
        val g = color.green
        val b = color.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2f
        if (max == min) {
            return floatArrayOf(0f, 0f, l)
        }
        val d = max - min
        val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        val h = when (max) {
            r -> (g - b) / d + (if (g < b) 6f else 0f)
            g -> (b - r) / d + 2f
            else -> (r - g) / d + 4f
        } * 60f
        return floatArrayOf(h, s, l)
    }

    fun hslToColor(h: Float, s: Float, l: Float): Color {
        val clampedH = (h % 360f + 360f) % 360f
        val clampedS = s.coerceIn(0f, 1f)
        val clampedL = l.coerceIn(0f, 1f)
        if (clampedS == 0f) {
            return Color(clampedL, clampedL, clampedL, 1f)
        }
        val q = if (clampedL < 0.5f) clampedL * (1f + clampedS) else clampedL + clampedS - clampedL * clampedS
        val p = 2f * clampedL - q
        fun hueToRgb(p: Float, q: Float, t: Float): Float {
            var tc = t
            if (tc < 0f) tc += 1f
            if (tc > 1f) tc -= 1f
            if (tc < 1f / 6f) return p + (q - p) * 6f * tc
            if (tc < 1f / 2f) return q
            if (tc < 2f / 3f) return p + (q - p) * (2f / 3f - tc) * 6f
            return p
        }
        val r = hueToRgb(p, q, clampedH / 360f + 1f / 3f)
        val g = hueToRgb(p, q, clampedH / 360f)
        val b = hueToRgb(p, q, clampedH / 360f - 1f / 3f)
        return Color(r, g, b, 1f)
    }

    fun calculateLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            val d = c.toDouble()
            return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }

    fun calculateContrast(foreground: Color, background: Color): Double {
        val l1 = calculateLuminance(foreground)
        val l2 = calculateLuminance(background)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun contrastOn(background: Color): Color {
        val whiteContrast = calculateContrast(Color.White, background)
        val darkText = Color(0xFF1A1C1A)
        val darkContrast = calculateContrast(darkText, background)
        return if (whiteContrast >= darkContrast) Color.White else darkText
    }

    private val BlackLightColorScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF212121),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFE0E0E0),
        onPrimaryContainer = Color(0xFF121212),
        inversePrimary = Color(0xFFE0E0E0),
        secondary = Color(0xFF616161),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFEEEEEE),
        onSecondaryContainer = Color(0xFF1E1E1E),
        tertiary = Color(0xFF424242),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0E0E0),
        onTertiaryContainer = Color(0xFF121212),
        background = Color(0xFFFAFAFA),
        onBackground = Color(0xFF121212),
        surface = Color(0xFFFAFAFA),
        onSurface = Color(0xFF121212),
        surfaceVariant = Color(0xFFEEEEEE),
        onSurfaceVariant = Color(0xFF616161),
        surfaceDim = Color(0xFFE0E0E0),
        surfaceBright = Color(0xFFFAFAFA),
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = Color(0xFFF5F5F5),
        surfaceContainer = Color(0xFFEEEEEE),
        surfaceContainerHigh = Color(0xFFE0E0E0),
        surfaceContainerHighest = Color(0xFFD6D6D6),
        outline = Color(0xFF757575),
        outlineVariant = Color(0xFFBDBDBD),
        inverseSurface = Color(0xFF1E1E1E),
        inverseOnSurface = Color(0xFFF5F5F5),
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        scrim = Color.Black,
    )

    private fun buildBlackDarkColorScheme(isAmoled: Boolean): ColorScheme {
        val surfaceColor = if (isAmoled) Color.Black else Color(0xFF121212)
        val surfaceLowest = if (isAmoled) Color.Black else Color(0xFF0A0A0A)
        val surfaceLow = if (isAmoled) Color(0xFF0C0C0C) else Color(0xFF181818)
        val surfaceNorm = if (isAmoled) Color(0xFF141414) else Color(0xFF1E1E1E)
        val surfaceHigh = if (isAmoled) Color(0xFF1E1E1E) else Color(0xFF282828)
        val surfaceHighest = if (isAmoled) Color(0xFF282828) else Color(0xFF333333)

        return darkColorScheme(
            primary = Color(0xFFE0E0E0),
            onPrimary = Color(0xFF1E1E1E),
            primaryContainer = Color(0xFF424242),
            onPrimaryContainer = Color(0xFFF5F5F5),
            inversePrimary = Color(0xFF212121),
            secondary = Color(0xFFBDBDBD),
            onSecondary = Color(0xFF212121),
            secondaryContainer = Color(0xFF333333),
            onSecondaryContainer = Color(0xFFE0E0E0),
            tertiary = Color(0xFF9E9E9E),
            onTertiary = Color(0xFF121212),
            tertiaryContainer = Color(0xFF424242),
            onTertiaryContainer = Color(0xFFE0E0E0),
            background = surfaceColor,
            onBackground = Color(0xFFE0E0E0),
            surface = surfaceColor,
            onSurface = Color(0xFFE0E0E0),
            surfaceVariant = Color(0xFF2E2E2E),
            onSurfaceVariant = Color(0xFFBDBDBD),
            surfaceDim = surfaceColor,
            surfaceBright = if (isAmoled) Color(0xFF383838) else Color(0xFF2A2A2A),
            surfaceContainerLowest = surfaceLowest,
            surfaceContainerLow = surfaceLow,
            surfaceContainer = surfaceNorm,
            surfaceContainerHigh = surfaceHigh,
            surfaceContainerHighest = surfaceHighest,
            outline = Color(0xFF8E8E8E),
            outlineVariant = Color(0xFF444444),
            inverseSurface = Color(0xFFE0E0E0),
            inverseOnSurface = Color(0xFF1E1E1E),
            error = Color(0xFFFFB4AB),
            onError = Color(0xFF690005),
            errorContainer = Color(0xFF93000A),
            onErrorContainer = Color(0xFFFFDAD6),
            scrim = Color.Black,
        )
    }

    fun buildClassicColorScheme(themeId: Int, isDark: Boolean, isAmoled: Boolean): ColorScheme {
        val info = getThemeInfo(themeId)
        if (info.id == 21) {
            return if (isDark) buildBlackDarkColorScheme(isAmoled) else BlackLightColorScheme
        }
        val hsl = colorToHsl(info.primary)
        val h = hsl[0]
        val s = hsl[1]

        val accentHsl = colorToHsl(info.accent)
        val accentH = accentHsl[0]
        val accentS = accentHsl[1]

        return if (isDark) {
            val neutralS = (s * 0.10f).coerceAtMost(0.06f)
            val surfaceColor = if (isAmoled) Color.Black else hslToColor(h, neutralS, 0.08f)
            val surfaceLowest = if (isAmoled) Color.Black else hslToColor(h, neutralS, 0.05f)
            val surfaceLow = if (isAmoled) Color(0xFF0C0C0C) else hslToColor(h, neutralS, 0.10f)
            val surfaceNorm = if (isAmoled) Color(0xFF141414) else hslToColor(h, neutralS, 0.12f)
            val surfaceHigh = if (isAmoled) Color(0xFF1E1E1E) else hslToColor(h, neutralS, 0.17f)
            val surfaceHighest = if (isAmoled) Color(0xFF282828) else hslToColor(h, neutralS, 0.22f)

            val prim = hslToColor(h, (s * 0.85f).coerceAtMost(0.85f), 0.75f)
            val primContainer = hslToColor(h, s * 0.8f, 0.25f)
            val sec = hslToColor(accentH, (accentS * 0.55f).coerceAtMost(0.6f), 0.75f)
            val secContainer = hslToColor(accentH, accentS * 0.5f, 0.25f)
            val ter = hslToColor(accentH, (accentS * 0.80f).coerceAtMost(0.85f), 0.78f)
            val terContainer = hslToColor(accentH, accentS * 0.7f, 0.26f)

            darkColorScheme(
                primary = prim,
                onPrimary = contrastOn(prim),
                primaryContainer = primContainer,
                onPrimaryContainer = contrastOn(primContainer),
                inversePrimary = info.primary,
                secondary = sec,
                onSecondary = contrastOn(sec),
                secondaryContainer = secContainer,
                onSecondaryContainer = contrastOn(secContainer),
                tertiary = ter,
                onTertiary = contrastOn(ter),
                tertiaryContainer = terContainer,
                onTertiaryContainer = contrastOn(terContainer),
                background = surfaceColor,
                onBackground = hslToColor(h, neutralS, 0.90f),
                surface = surfaceColor,
                onSurface = hslToColor(h, neutralS, 0.90f),
                surfaceVariant = hslToColor(h, (s * 0.12f).coerceAtMost(0.08f), 0.26f),
                onSurfaceVariant = hslToColor(h, (s * 0.12f).coerceAtMost(0.08f), 0.78f),
                surfaceDim = surfaceColor,
                surfaceBright = if (isAmoled) Color(0xFF383838) else hslToColor(h, neutralS, 0.24f),
                surfaceContainerLowest = surfaceLowest,
                surfaceContainerLow = surfaceLow,
                surfaceContainer = surfaceNorm,
                surfaceContainerHigh = surfaceHigh,
                surfaceContainerHighest = surfaceHighest,
                outline = hslToColor(h, neutralS, 0.55f),
                outlineVariant = hslToColor(h, neutralS, 0.27f),
                inverseSurface = hslToColor(h, neutralS, 0.90f),
                inverseOnSurface = hslToColor(h, neutralS, 0.12f),
                error = Color(0xFFFFB4AB),
                onError = Color(0xFF690005),
                errorContainer = Color(0xFF93000A),
                onErrorContainer = Color(0xFFFFDAD6),
                scrim = Color.Black,
            )
        } else {
            val neutralS = (s * 0.08f).coerceAtMost(0.06f)
            val prim = info.primary
            val primContainer = hslToColor(h, (s * 0.55f).coerceAtMost(0.6f), 0.90f)
            val sec = hslToColor(accentH, (accentS * 0.45f).coerceAtMost(0.5f), 0.38f)
            val secContainer = hslToColor(accentH, (accentS * 0.35f).coerceAtMost(0.4f), 0.90f)
            val ter = hslToColor(accentH, accentS, 0.40f)
            val terContainer = hslToColor(accentH, (accentS * 0.55f).coerceAtMost(0.6f), 0.88f)

            lightColorScheme(
                primary = prim,
                onPrimary = contrastOn(prim),
                primaryContainer = primContainer,
                onPrimaryContainer = contrastOn(primContainer),
                inversePrimary = hslToColor(h, (s * 0.85f).coerceAtMost(0.85f), 0.75f),
                secondary = sec,
                onSecondary = contrastOn(sec),
                secondaryContainer = secContainer,
                onSecondaryContainer = contrastOn(secContainer),
                tertiary = ter,
                onTertiary = contrastOn(ter),
                tertiaryContainer = terContainer,
                onTertiaryContainer = contrastOn(terContainer),
                background = hslToColor(h, neutralS, 0.98f),
                onBackground = hslToColor(h, neutralS, 0.10f),
                surface = hslToColor(h, neutralS, 0.98f),
                onSurface = hslToColor(h, neutralS, 0.10f),
                surfaceVariant = hslToColor(h, (s * 0.12f).coerceAtMost(0.10f), 0.90f),
                onSurfaceVariant = hslToColor(h, (s * 0.12f).coerceAtMost(0.10f), 0.30f),
                surfaceDim = hslToColor(h, neutralS, 0.87f),
                surfaceBright = hslToColor(h, neutralS, 0.98f),
                surfaceContainerLowest = Color.White,
                surfaceContainerLow = hslToColor(h, neutralS, 0.96f),
                surfaceContainer = hslToColor(h, neutralS, 0.94f),
                surfaceContainerHigh = hslToColor(h, neutralS, 0.92f),
                surfaceContainerHighest = hslToColor(h, neutralS, 0.90f),
                outline = hslToColor(h, neutralS, 0.50f),
                outlineVariant = hslToColor(h, neutralS, 0.80f),
                inverseSurface = hslToColor(h, neutralS, 0.20f),
                inverseOnSurface = hslToColor(h, neutralS, 0.95f),
                error = Color(0xFFBA1A1A),
                onError = Color.White,
                errorContainer = Color(0xFFFFDAD6),
                onErrorContainer = Color(0xFF410002),
                scrim = Color.Black,
            )
        }
    }
}
