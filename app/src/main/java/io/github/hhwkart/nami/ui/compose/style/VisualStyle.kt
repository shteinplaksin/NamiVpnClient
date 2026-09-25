package io.github.hhwkart.nami.ui.compose.style

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.database.DataStore

enum class InterfaceStyle(val persistedValue: Int) {
    STANDARD(0),
    // Persisted value 1 was already used by the original Liquid Glass toggle;
    // keep it as Regular Glass so existing user preferences migrate safely.
    LIQUID_GLASS(1),
    CLEAR_GLASS(2),
    ;

    companion object {
        fun fromPersisted(value: Int): InterfaceStyle = entries.firstOrNull {
            it.persistedValue == value
        } ?: STANDARD
    }
}

enum class LiquidGlassQuality(val persistedValue: Int) {
    AUTO(0),
    FULL(1),
    REDUCED(2),
    ;

    companion object {
        fun fromPersisted(value: Int): LiquidGlassQuality = entries.firstOrNull {
            it.persistedValue == value
        } ?: AUTO
    }
}

enum class LiquidGlassTier {
    MATERIAL,
    TINTED,
    BLUR,
    BLUR_AND_LENS,
}

enum class GlassSurfaceRole {
    NAVIGATION,
    SELECTED_INDICATOR,
    TOP_BAR,
    OVERLAY,
    CONTROL,
    DIALOG,
    CARD,
    CONTENT,
}

/** Runtime facts reported by the renderer adapter, not inferred from API level. */
@Immutable
data class LiquidGlassCapabilities(
    val rendererAvailable: Boolean,
    val blurSupported: Boolean,
    val lensSupported: Boolean,
    val crossWindowBlurSupported: Boolean,
) {
    val canUseBlur: Boolean
        get() = rendererAvailable && blurSupported

    val canUseLens: Boolean
        get() = canUseBlur && lensSupported

    companion object {
        /** Safe default until the production backdrop adapter has been installed. */
        val CompatibilityOnly = LiquidGlassCapabilities(
            rendererAvailable = false,
            blurSupported = false,
            lensSupported = false,
            crossWindowBlurSupported = false,
        )
    }
}

@Immutable
data class NamiVisualStyle(
    val interfaceStyle: InterfaceStyle,
    val requestedQuality: LiquidGlassQuality,
    val effectiveTier: LiquidGlassTier,
    val reduceMotion: Boolean,
) {
    val liquidEnabled: Boolean
        get() = interfaceStyle != InterfaceStyle.STANDARD &&
            effectiveTier != LiquidGlassTier.MATERIAL

    val isClearGlass: Boolean
        get() = interfaceStyle == InterfaceStyle.CLEAR_GLASS

    companion object {
        val Standard = NamiVisualStyle(
            interfaceStyle = InterfaceStyle.STANDARD,
            requestedQuality = LiquidGlassQuality.AUTO,
            effectiveTier = LiquidGlassTier.MATERIAL,
            reduceMotion = false,
        )
    }
}

val LocalNamiVisualStyle = staticCompositionLocalOf { NamiVisualStyle.Standard }

object LiquidGlassPolicy {

    fun resolve(
        interfaceStyle: InterfaceStyle,
        quality: LiquidGlassQuality,
        sdkInt: Int,
        isLowRamDevice: Boolean,
        capabilities: LiquidGlassCapabilities,
        surfaceRole: GlassSurfaceRole = GlassSurfaceRole.NAVIGATION,
    ): LiquidGlassTier {
        if (interfaceStyle == InterfaceStyle.STANDARD) return LiquidGlassTier.MATERIAL

        // Reduced motion is handled by the animation layer. It must not be
        // confused with transparency or renderer availability.
        if (quality == LiquidGlassQuality.REDUCED || isLowRamDevice) {
            return LiquidGlassTier.TINTED
        }

        // Content and ordinary cards are intentionally outside the expensive
        // backdrop scope. Dialogs also require an explicit cross-window path.
        if (surfaceRole == GlassSurfaceRole.CARD ||
            surfaceRole == GlassSurfaceRole.CONTENT ||
            (surfaceRole == GlassSurfaceRole.DIALOG &&
                !capabilities.crossWindowBlurSupported)
        ) {
            return LiquidGlassTier.TINTED
        }

        if (!capabilities.rendererAvailable || sdkInt < Build.VERSION_CODES.S) {
            return LiquidGlassTier.TINTED
        }

        return when {
            sdkInt >= Build.VERSION_CODES.TIRAMISU && capabilities.canUseLens ->
                LiquidGlassTier.BLUR_AND_LENS

            capabilities.canUseBlur -> LiquidGlassTier.BLUR
            else -> LiquidGlassTier.TINTED
        }
    }

    fun resolveFor(
        context: Context,
        capabilities: LiquidGlassCapabilities = LiquidGlassCapabilities.CompatibilityOnly,
        surfaceRole: GlassSurfaceRole = GlassSurfaceRole.NAVIGATION,
    ): NamiVisualStyle {
        // Theme mode is the user-facing switch for the visual language. Keep
        // the old interface-style value as a migration/backup field, but do
        // not let it silently force Liquid Glass back on after the user has
        // selected a regular theme.
        val interfaceStyle = if (DataStore.themeMode == Key.THEME_MODE_LIQUID_GLASS) {
            InterfaceStyle.CLEAR_GLASS
        } else {
            InterfaceStyle.STANDARD
        }
        val quality = LiquidGlassQuality.AUTO
        val isLowRamDevice = context.getSystemService(ActivityManager::class.java)
            ?.isLowRamDevice == true
        val animationsEnabled = areSystemAnimationsEnabled(context)
        val tier = resolve(
            interfaceStyle = interfaceStyle,
            quality = quality,
            sdkInt = Build.VERSION.SDK_INT,
            isLowRamDevice = isLowRamDevice,
            capabilities = capabilities,
            surfaceRole = surfaceRole,
        )
        return NamiVisualStyle(
            interfaceStyle = interfaceStyle,
            requestedQuality = quality,
            effectiveTier = tier,
            reduceMotion = !animationsEnabled,
        )
    }

    private fun areSystemAnimationsEnabled(context: Context): Boolean {
        val resolver = context.contentResolver
        val animatorScale = runCatching {
            Settings.Global.getFloat(
                resolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        val transitionScale = runCatching {
            Settings.Global.getFloat(
                resolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
        return animatorScale > 0f && transitionScale > 0f
    }
}
