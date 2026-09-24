package io.github.hhwkart.nami.ui.compose.theme

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.ui.compose.style.NamiBackdropCapabilities
import io.github.hhwkart.nami.ui.compose.style.LiquidGlassPolicy
import io.github.hhwkart.nami.ui.compose.style.LocalNamiVisualStyle

// Full Material 3 tokens generated from the green seed #006C4C
val LightGreenColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF006C4C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF89F8C6),
    onPrimaryContainer = Color(0xFF002114),
    inversePrimary = Color(0xFF6CDBAB),
    secondary = Color(0xFF4D6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9D9),
    onSecondaryContainer = Color(0xFF0A1F16),
    tertiary = Color(0xFF3D6373),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC1E8FB),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF5FBF4),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF5FBF4),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDBE5DD),
    onSurfaceVariant = Color(0xFF404943),
    surfaceDim = Color(0xFFD6DCD6),
    surfaceBright = Color(0xFFF5FBF4),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F5EE),
    surfaceContainer = Color(0xFFEAEFE9),
    surfaceContainerHigh = Color(0xFFE4EAE3),
    surfaceContainerHighest = Color(0xFFDEE4DE),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFBFC9C1),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFECF2EC),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

val DarkGreenColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF6CDBAB),
    onPrimary = Color(0xFF003825),
    primaryContainer = Color(0xFF005237),
    onPrimaryContainer = Color(0xFF89F8C6),
    inversePrimary = Color(0xFF006C4C),
    secondary = Color(0xFFB3CCBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF364B40),
    onSecondaryContainer = Color(0xFFCFE9D9),
    tertiary = Color(0xFFA5CCDF),
    onTertiary = Color(0xFF073543),
    tertiaryContainer = Color(0xFF244B5B),
    onTertiaryContainer = Color(0xFFC1E8FB),
    background = Color(0xFF0F1512),
    onBackground = Color(0xFFDEE4DE),
    surface = Color(0xFF0F1512),
    onSurface = Color(0xFFDEE4DE),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFBFC9C1),
    surfaceDim = Color(0xFF0F1512),
    surfaceBright = Color(0xFF353B37),
    surfaceContainerLowest = Color(0xFF0A0F0D),
    surfaceContainerLow = Color(0xFF171D19),
    surfaceContainer = Color(0xFF1B211E),
    surfaceContainerHigh = Color(0xFF252B28),
    surfaceContainerHighest = Color(0xFF303632),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDEE4DE),
    inverseOnSurface = Color(0xFF2C322E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

val AmoledDarkGreenColors: ColorScheme = DarkGreenColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0D0B),
    surfaceContainer = Color(0xFF121614),
    surfaceContainerHigh = Color(0xFF1B201D),
    surfaceContainerHighest = Color(0xFF252B28),
)

/**
 * Neutral Apple-like Liquid Glass palette. The green theme remains a separate
 * user choice; Liquid Glass uses a dark Base surface and progressively lighter
 * Elevated containers so depth comes from luminance, not a colored seed.
 */
val LiquidGlassDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF0A84FF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF243447),
    onPrimaryContainer = Color(0xFFD9ECFF),
    inversePrimary = Color(0xFF0066CC),
    secondary = Color(0xFF98989D),
    onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF303035),
    onSecondaryContainer = Color(0xFFE5E5EA),
    tertiary = Color(0xFF64D2FF),
    onTertiary = Color(0xFF001F2A),
    tertiaryContainer = Color(0xFF163A48),
    onTertiaryContainer = Color(0xFFB9EEFF),
    background = Color(0xFF070809),
    onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF070809),
    onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF3A3A40),
    onSurfaceVariant = Color(0xFFA7A7AE),
    surfaceDim = Color(0xFF050506),
    surfaceBright = Color(0xFF25262A),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0C0D0F),
    surfaceContainer = Color(0xFF121316),
    surfaceContainerHigh = Color(0xFF191A1E),
    surfaceContainerHighest = Color(0xFF222328),
    outline = Color(0xFF6E6E75),
    outlineVariant = Color(0xFF34353A),
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF1C1C1E),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF5A1B1B),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

val LiquidGlassAmoledColors: ColorScheme = LiquidGlassDarkColors.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF08090A),
    surfaceContainer = Color(0xFF101114),
    surfaceContainerHigh = Color(0xFF17181B),
    surfaceContainerHighest = Color(0xFF202126),
)

val LiquidGlassLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF001D36),
    inversePrimary = Color(0xFF9BC7FF),
    secondary = Color(0xFF636366),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE5E5EA),
    onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF0071A8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6EEFF),
    onTertiaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF636366),
    surfaceDim = Color(0xFFE5E5EA),
    surfaceBright = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF9F9FB),
    surfaceContainer = Color(0xFFF7F7FA),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFEFEFF4),
    outline = Color(0xFF8E8E93),
    outlineVariant = Color(0xFFD1D1D6),
    inverseSurface = Color(0xFF1C1C1E),
    inverseOnSurface = Color(0xFFF2F2F7),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

// MD3 15-role typography scale
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
    ),
)

// MD3 5-tier shapes scale (4, 8, 12, 16, 28 dp)
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NamiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val isTv = remember(context) {
        context.isTvDevice()
    }
    val rendererCapabilities = remember(context, view, view.isHardwareAccelerated) {
        NamiBackdropCapabilities.detect(context, view)
    }
    val visualStyle = remember(
        context,
        rendererCapabilities,
        DataStore.themeMode,
        DataStore.interfaceStyle,
        DataStore.liquidGlassQuality,
        DataStore.nightTheme,
        DataStore.amoledDark,
    ) {
        LiquidGlassPolicy.resolveFor(
            context = context,
            capabilities = rendererCapabilities,
        )
    }

    val dark = when (DataStore.nightTheme) {
        1 -> true
        2 -> false
        else -> isSystemInDarkTheme()
    }

    val amoled = dark && DataStore.amoledDark
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val themeMode = DataStore.themeMode

    val colors: ColorScheme = when {
        themeMode == Key.THEME_MODE_DYNAMIC && dynamicSupported -> {
            val base = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (amoled) {
                base.copy(
                    surface = Color.Black,
                    background = Color.Black,
                    surfaceDim = Color.Black,
                    surfaceContainerLowest = Color.Black,
                    surfaceContainerLow = Color(0xFF0A0A0A),
                    surfaceContainer = Color(0xFF121212),
                    surfaceContainerHigh = Color(0xFF1A1A1A),
                    surfaceContainerHighest = Color(0xFF222222),
                )
            } else {
                base
            }
        }
        themeMode == Key.THEME_MODE_CLASSIC -> {
            ClassicThemes.buildClassicColorScheme(
                themeId = DataStore.appTheme,
                isDark = dark,
                isAmoled = amoled,
            )
        }
        themeMode == Key.THEME_MODE_LIQUID_GLASS -> {
            if (dark) {
                if (amoled) LiquidGlassAmoledColors else LiquidGlassDarkColors
            } else {
                LiquidGlassLightColors
            }
        }
        else -> {
            // THEME_MODE_GREEN (Default green seed #006C4C)
            if (amoled) AmoledDarkGreenColors else if (dark) DarkGreenColors else LightGreenColors
        }
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivity()?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !dark
            insetsController.isAppearanceLightNavigationBars = !dark
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                window.isStatusBarContrastEnforced = false
                @Suppress("DEPRECATION")
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    CompositionLocalProvider(
        LocalIsTv provides isTv,
        LocalNamiVisualStyle provides visualStyle,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
