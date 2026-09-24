package io.github.hhwkart.nami.ui.compose.style

import org.junit.Assert.assertEquals
import org.junit.Test

class VisualStylePolicyTest {

    private val fullRenderer = LiquidGlassCapabilities(
        rendererAvailable = true,
        blurSupported = true,
        lensSupported = true,
        crossWindowBlurSupported = true,
    )

    @Test
    fun standardStyleAlwaysUsesMaterialTier() {
        assertEquals(
            LiquidGlassTier.MATERIAL,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.STANDARD,
                quality = LiquidGlassQuality.FULL,
                sdkInt = 35,
                isLowRamDevice = false,
                capabilities = fullRenderer,
            ),
        )
    }

    @Test
    fun reducedQualityUsesTintedFallback() {
        assertEquals(
            LiquidGlassTier.TINTED,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.REDUCED,
                sdkInt = 35,
                isLowRamDevice = false,
                capabilities = fullRenderer,
            ),
        )
    }

    @Test
    fun unavailableRendererStaysOnCompatibilityTint() {
        assertEquals(
            LiquidGlassTier.TINTED,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 35,
                isLowRamDevice = false,
                capabilities = LiquidGlassCapabilities.CompatibilityOnly,
            ),
        )
    }

    @Test
    fun api24To30NeverUseBackdropEvenWhenRendererClaimsSupport() {
        assertEquals(
            LiquidGlassTier.TINTED,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 24,
                isLowRamDevice = false,
                capabilities = fullRenderer,
            ),
        )
        assertEquals(
            LiquidGlassTier.BLUR,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 31,
                isLowRamDevice = false,
                capabilities = fullRenderer,
            ),
        )
    }

    @Test
    fun api31And32UseBlurButNeverLens() {
        assertEquals(
            LiquidGlassTier.BLUR,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 32,
                isLowRamDevice = false,
                capabilities = fullRenderer,
            ),
        )
    }

    @Test
    fun api33UsesLensOnlyWhenRendererReportsLensSupport() {
        assertEquals(
            LiquidGlassTier.BLUR_AND_LENS,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 33,
                isLowRamDevice = false,
                capabilities = fullRenderer,
            ),
        )
        assertEquals(
            LiquidGlassTier.BLUR,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 33,
                isLowRamDevice = false,
                capabilities = fullRenderer.copy(lensSupported = false),
            ),
        )
    }

    @Test
    fun dialogAndContentRolesDoNotBorrowRootBackdrop() {
        assertEquals(
            LiquidGlassTier.TINTED,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 35,
                isLowRamDevice = false,
                capabilities = fullRenderer.copy(crossWindowBlurSupported = false),
                surfaceRole = GlassSurfaceRole.DIALOG,
            ),
        )
        assertEquals(
            LiquidGlassTier.TINTED,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 35,
                isLowRamDevice = false,
                capabilities = fullRenderer,
                surfaceRole = GlassSurfaceRole.CONTENT,
            ),
        )
    }

    @Test
    fun lowRamAndMissingLensCapabilityNeverOverclaimRendererSupport() {
        val lowRam = LiquidGlassPolicy.resolve(
            interfaceStyle = InterfaceStyle.LIQUID_GLASS,
            quality = LiquidGlassQuality.FULL,
            sdkInt = 35,
            isLowRamDevice = true,
            capabilities = fullRenderer,
        )
        val noLensCapability = LiquidGlassPolicy.resolve(
            interfaceStyle = InterfaceStyle.LIQUID_GLASS,
            quality = LiquidGlassQuality.FULL,
            sdkInt = 35,
            isLowRamDevice = false,
            capabilities = fullRenderer.copy(lensSupported = false),
        )

        assertEquals(LiquidGlassTier.TINTED, lowRam)
        assertEquals(LiquidGlassTier.BLUR, noLensCapability)
    }
}
