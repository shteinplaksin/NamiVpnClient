package io.github.hhwkart.nami.ui.compose.style

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NamiBackdropCapabilitiesTest {

    @Test
    fun standardAndCompatibilityModesNeverCreateLayerBackdrop() {
        assertFalse(
            NamiBackdropCapabilities.shouldCreateBackdropLayer(
                enabled = false,
                rendererTier = LiquidGlassTier.MATERIAL,
                sdkInt = 35,
                hardwareAccelerated = true,
            ),
        )
        assertFalse(
            NamiBackdropCapabilities.shouldCreateBackdropLayer(
                enabled = true,
                rendererTier = LiquidGlassTier.TINTED,
                sdkInt = 35,
                hardwareAccelerated = true,
            ),
        )
    }

    @Test
    fun api24To32AndLensTierNeverCreateLayerBackdrop() {
        assertFalse(
            NamiBackdropCapabilities.shouldCreateBackdropLayer(
                enabled = true,
                rendererTier = LiquidGlassTier.BLUR,
                sdkInt = 24,
                hardwareAccelerated = true,
            ),
        )
        assertFalse(
            NamiBackdropCapabilities.shouldCreateBackdropLayer(
                enabled = true,
                rendererTier = LiquidGlassTier.BLUR_AND_LENS,
                sdkInt = 32,
                hardwareAccelerated = true,
            ),
        )
    }

    @Test
    fun supportedHardwareApi31PathCanCreateLayerBackdrop() {
        assertTrue(
            NamiBackdropCapabilities.shouldCreateBackdropLayer(
                enabled = true,
                rendererTier = LiquidGlassTier.BLUR,
                sdkInt = 31,
                hardwareAccelerated = true,
            ),
        )
    }

    @Test
    fun api24To30NeverReportProductionBackdropRenderer() {
        for (sdkInt in 24..30) {
            val capabilities = NamiBackdropCapabilities.detect(
                sdkInt = sdkInt,
                hardwareAccelerated = true,
                crossWindowBlurEnabled = true,
            )

            assertFalse("API $sdkInt must use compatibility rendering", capabilities.rendererAvailable)
            assertFalse("API $sdkInt must not expose blur", capabilities.blurSupported)
            assertFalse("API $sdkInt must not expose lens", capabilities.lensSupported)
        }
    }

    @Test
    fun api31And32ReportBlurButNeverLens() {
        for (sdkInt in 31..32) {
            val capabilities = NamiBackdropCapabilities.detect(
                sdkInt = sdkInt,
                hardwareAccelerated = true,
                crossWindowBlurEnabled = false,
            )

            assertTrue("API $sdkInt should expose the same-window blur renderer", capabilities.rendererAvailable)
            assertTrue("API $sdkInt should expose blur", capabilities.blurSupported)
            assertFalse("API $sdkInt must not expose lens", capabilities.lensSupported)
            assertFalse("Cross-window blur is not assumed", capabilities.crossWindowBlurSupported)
        }
    }

    @Test
    fun unsupportedRendererApiUsesCompatibilityTint() {
        val capabilities = NamiBackdropCapabilities.detect(
            sdkInt = 31,
            hardwareAccelerated = true,
            crossWindowBlurEnabled = true,
            rendererApiSupported = false,
        )

        assertFalse(capabilities.rendererAvailable)
        assertFalse(capabilities.blurSupported)
        assertFalse(
            NamiBackdropCapabilities.shouldCreateBackdropLayer(
                enabled = true,
                rendererTier = LiquidGlassTier.BLUR,
                sdkInt = 31,
                hardwareAccelerated = true,
                rendererApiSupported = false,
            ),
        )
        assertEquals(
            LiquidGlassTier.TINTED,
            LiquidGlassPolicy.resolve(
                interfaceStyle = InterfaceStyle.LIQUID_GLASS,
                quality = LiquidGlassQuality.AUTO,
                sdkInt = 31,
                isLowRamDevice = false,
                capabilities = capabilities,
            ),
        )
    }

    @Test
    fun onlyNavigationAndSelectedIndicatorCanUseUpstreamBackdrop() {
        assertTrue(
            shouldUseUpstreamBackdrop(
                effectiveTier = LiquidGlassTier.BLUR,
                surfaceRole = GlassSurfaceRole.NAVIGATION,
                hasUpstreamLayer = true,
            ),
        )
        assertTrue(
            shouldUseUpstreamBackdrop(
                effectiveTier = LiquidGlassTier.BLUR,
                surfaceRole = GlassSurfaceRole.SELECTED_INDICATOR,
                hasUpstreamLayer = true,
            ),
        )
        assertFalse(
            shouldUseUpstreamBackdrop(
                effectiveTier = LiquidGlassTier.BLUR,
                surfaceRole = GlassSurfaceRole.CONTENT,
                hasUpstreamLayer = true,
            ),
        )
        assertFalse(
            shouldUseUpstreamBackdrop(
                effectiveTier = LiquidGlassTier.BLUR,
                surfaceRole = GlassSurfaceRole.CARD,
                hasUpstreamLayer = true,
            ),
        )
        assertFalse(
            shouldUseUpstreamBackdrop(
                effectiveTier = LiquidGlassTier.BLUR,
                surfaceRole = GlassSurfaceRole.SELECTED_INDICATOR,
                hasUpstreamLayer = false,
            ),
        )
        assertFalse(
            shouldUseUpstreamBackdrop(
                effectiveTier = LiquidGlassTier.TINTED,
                surfaceRole = GlassSurfaceRole.SELECTED_INDICATOR,
                hasUpstreamLayer = true,
            ),
        )
    }

    @Test
    fun api33ReportsLensWhenRuntimeShaderIsAvailable() {
        val capabilities = NamiBackdropCapabilities.detect(
            sdkInt = 33,
            hardwareAccelerated = true,
            crossWindowBlurEnabled = true,
        )

        assertTrue(capabilities.rendererAvailable)
        assertTrue(capabilities.blurSupported)
        assertTrue(capabilities.lensSupported)
        assertTrue(capabilities.crossWindowBlurSupported)
    }

    @Test
    fun api33WithoutRuntimeShaderKeepsBlurFallback() {
        val capabilities = NamiBackdropCapabilities.detect(
            sdkInt = 33,
            hardwareAccelerated = true,
            crossWindowBlurEnabled = true,
            runtimeShaderSupported = false,
        )

        assertTrue(capabilities.rendererAvailable)
        assertTrue(capabilities.blurSupported)
        assertFalse(capabilities.lensSupported)
    }

    @Test
    fun nonAcceleratedViewUsesCompatibilityFallbackOnSupportedApi() {
        val capabilities = NamiBackdropCapabilities.detect(
            sdkInt = 35,
            hardwareAccelerated = false,
            crossWindowBlurEnabled = true,
        )

        assertFalse(capabilities.rendererAvailable)
        assertFalse(capabilities.blurSupported)
        assertFalse(capabilities.lensSupported)
    }
}
