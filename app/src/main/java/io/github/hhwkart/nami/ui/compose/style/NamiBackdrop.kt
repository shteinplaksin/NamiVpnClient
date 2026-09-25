package io.github.hhwkart.nami.ui.compose.style

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow

/**
 * Stable Nami-facing handle for the optional upstream backdrop layer.
 *
 * The public shape of this abstraction is intentionally unchanged. The
 * upstream LayerBackdrop is internal so navigation code does not depend on
 * the third-party API and compatibility mode can keep returning a tint-only
 * handle with no renderer object.
 */
@Stable
class NamiBackdrop(
    val enabled: Boolean,
) {
    internal var upstreamLayer: LayerBackdrop? = null
        private set
    internal var renderBackdrop: Backdrop? = null
        private set

    internal constructor(
        enabled: Boolean,
        upstreamLayer: LayerBackdrop?,
        renderBackdrop: Backdrop? = upstreamLayer,
    ) : this(enabled = enabled) {
        this.upstreamLayer = upstreamLayer
        this.renderBackdrop = renderBackdrop
    }
}

/**
 * Runtime capability adapter for the production renderer.
 *
 * API level is only one input. The renderer also requires a hardware-
 * accelerated Compose view. Runtime-shader support is detected independently
 * so API 33+ can use the upstream refraction shader when it is available.
 */
object NamiBackdropCapabilities {

    internal fun shouldCreateBackdropLayer(
        enabled: Boolean,
        rendererTier: LiquidGlassTier,
        sdkInt: Int,
        hardwareAccelerated: Boolean,
        rendererApiSupported: Boolean = sdkInt >= Build.VERSION_CODES.S,
    ): Boolean = enabled &&
        (rendererTier == LiquidGlassTier.BLUR ||
            (rendererTier == LiquidGlassTier.BLUR_AND_LENS &&
                sdkInt >= Build.VERSION_CODES.TIRAMISU)) &&
        sdkInt >= Build.VERSION_CODES.S &&
        rendererApiSupported &&
        hardwareAccelerated

    fun detect(context: Context, view: View): LiquidGlassCapabilities = detect(
        sdkInt = Build.VERSION.SDK_INT,
        hardwareAccelerated = view.isHardwareAccelerated,
        rendererApiSupported = isRenderEffectSupported(),
        runtimeShaderSupported = isRuntimeShaderSupported(),
        crossWindowBlurEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            isCrossWindowBlurEnabled(context)
        } else {
            false
        },
    )

    internal fun detect(
        sdkInt: Int,
        hardwareAccelerated: Boolean,
        crossWindowBlurEnabled: Boolean,
        rendererApiSupported: Boolean = sdkInt >= Build.VERSION_CODES.S,
        runtimeShaderSupported: Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU,
    ): LiquidGlassCapabilities {
        val rendererAvailable = sdkInt >= Build.VERSION_CODES.S &&
            rendererApiSupported &&
            hardwareAccelerated
        return LiquidGlassCapabilities(
            rendererAvailable = rendererAvailable,
            blurSupported = rendererAvailable,
            lensSupported = rendererAvailable &&
                sdkInt >= Build.VERSION_CODES.TIRAMISU &&
                runtimeShaderSupported,
            crossWindowBlurSupported = rendererAvailable && crossWindowBlurEnabled,
        )
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isCrossWindowBlurEnabled(context: Context): Boolean =
        context.getSystemService(WindowManager::class.java)?.isCrossWindowBlurEnabled == true
}

internal fun shouldUseUpstreamBackdrop(
    effectiveTier: LiquidGlassTier,
    surfaceRole: GlassSurfaceRole,
    hasUpstreamLayer: Boolean,
): Boolean = hasUpstreamLayer &&
    (effectiveTier == LiquidGlassTier.BLUR ||
        effectiveTier == LiquidGlassTier.BLUR_AND_LENS) &&
    (surfaceRole == GlassSurfaceRole.NAVIGATION ||
        surfaceRole == GlassSurfaceRole.SELECTED_INDICATOR)

/**
 * Compatibility-preserving factory. This overload never creates an upstream
 * LayerBackdrop; the production host below creates one only after capability
 * and effective-tier checks have passed.
 */
@Composable
fun rememberNamiBackdrop(enabled: Boolean = true): NamiBackdrop =
    remember(enabled) { NamiBackdrop(enabled = enabled, upstreamLayer = null) }

/**
 * Applies the upstream layer backdrop to the content/root sampling layer.
 * Compatibility and Standard modes are no-ops and never load the renderer.
 */
fun Modifier.namiBackdropSource(backdrop: NamiBackdrop): Modifier =
    backdrop.upstreamLayer?.let { upstream ->
        this.layerBackdrop(upstream)
    } ?: this

/**
 * Creates a surface-only view of the same renderer source. This is used for
 * safe local compositions such as the selected tab lens: the source can be a
 * CombinedBackdrop while the root layer remains reserved for the content host.
 */
internal fun NamiBackdrop.withRenderBackdrop(renderBackdrop: Backdrop): NamiBackdrop =
    NamiBackdrop(
        enabled = enabled,
        upstreamLayer = null,
        renderBackdrop = renderBackdrop,
    )

/**
 * Creates the shell backdrop topology.
 *
 * The content layer receives [namiBackdropSource]. Navigation surfaces are
 * rendered by a sibling slot (the Scaffold bottomBar) through drawBackdrop;
 * they are never placed inside the content layer that they sample.
 */
@Composable
fun NamiGlassBackdropHost(
    modifier: Modifier = Modifier,
    backgroundModifier: Modifier = Modifier,
    enabled: Boolean = true,
    rendererTier: LiquidGlassTier = LiquidGlassTier.TINTED,
    content: @Composable (NamiBackdrop) -> Unit,
) {
    val view = LocalView.current
    val rendererApiSupported = enabled &&
        (rendererTier == LiquidGlassTier.BLUR ||
            (rendererTier == LiquidGlassTier.BLUR_AND_LENS &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        isRenderEffectSupported()
    val rendererEnabled = NamiBackdropCapabilities.shouldCreateBackdropLayer(
        enabled = enabled,
        rendererTier = rendererTier,
        sdkInt = Build.VERSION.SDK_INT,
        hardwareAccelerated = view.isHardwareAccelerated,
        rendererApiSupported = rendererApiSupported,
    )
    // Capability detection gates this call. Unsupported API levels and
    // non-accelerated views never create or invoke the upstream renderer.
    val upstreamLayer = if (rendererEnabled) rememberLayerBackdrop() else null
    val backdrop = remember(enabled, upstreamLayer) {
        NamiBackdrop(enabled = enabled, upstreamLayer = upstreamLayer)
    }

    Box(modifier) {
        if (enabled) {
            val colors = MaterialTheme.colorScheme
            Canvas(backgroundModifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            colors.primary.copy(alpha = 0.055f),
                            Color.Transparent,
                            colors.tertiary.copy(alpha = 0.045f),
                        ),
                        start = Offset.Zero,
                        end = Offset(size.width, size.height),
                    ),
                )
                drawCircle(
                    color = colors.primary.copy(alpha = 0.06f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width * 0.08f, size.height * 0.18f),
                )
                drawCircle(
                    color = colors.tertiary.copy(alpha = 0.045f),
                    radius = size.minDimension * 0.36f,
                    center = Offset(size.width * 0.94f, size.height * 0.72f),
                )
            }
        }
        content(backdrop)
    }
}

@Composable
@Suppress("ModifierParameter")
fun NamiGlassOverlay(
    backdrop: NamiBackdrop? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
    surfaceRole: GlassSurfaceRole = GlassSurfaceRole.CONTENT,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val visualStyle = LocalNamiVisualStyle.current
    val isSelectedIndicator = surfaceRole == GlassSurfaceRole.SELECTED_INDICATOR
    val selectedLensSupported = isSelectedIndicator && (
        visualStyle.effectiveTier == LiquidGlassTier.BLUR ||
            visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS
        )
    val useClearTreatment = (visualStyle.isClearGlass || selectedLensSupported) && (
        surfaceRole == GlassSurfaceRole.NAVIGATION ||
            surfaceRole == GlassSurfaceRole.SELECTED_INDICATOR ||
            surfaceRole == GlassSurfaceRole.CONTROL
        )
    // Clear Glass cards/dialog-like surfaces can remain translucent even when
    // they do not have a safe root backdrop source. Standard surfaces keep the
    // old opaque fallback so legacy screens do not become unreadable.
    val glassTint = if (backdrop?.enabled == true || visualStyle.isClearGlass) {
        tint
    } else {
        tint.copy(alpha = 1f)
    }
    val renderBackdrop = backdrop?.renderBackdrop
    val useUpstreamBlur = shouldUseUpstreamBackdrop(
        effectiveTier = visualStyle.effectiveTier,
        surfaceRole = surfaceRole,
        hasUpstreamLayer = renderBackdrop != null,
    )
    val upstreamModifier = if (useUpstreamBlur && renderBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = renderBackdrop,
            shape = { shape },
            effects = {
                if (useClearTreatment) {
                    // This is the actual Liquid Glass treatment: increase
                    // local vibrancy, bend the sampled backdrop, and add
                    // chromatic dispersion around the rounded silhouette.
                    vibrancy()
                    // Blur the selected text immediately while preserving the
                    // refraction detail at the pill's glass edge.
                    blur(if (isSelectedIndicator) 10.dp.toPx() else 7.dp.toPx())
                    if (visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS) {
                        lens(
                            refractionHeight = (if (isSelectedIndicator) 9 else 24).dp.toPx(),
                            refractionAmount = (if (isSelectedIndicator) 20 else 24).dp.toPx(),
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    }
                } else {
                    blur(8.dp.toPx())
                }
            },
            highlight = {
                if (useClearTreatment) {
                    Highlight.Default.copy(alpha = if (isSelectedIndicator) 0.76f else 0.78f)
                } else {
                    Highlight.Default
                }
            },
            shadow = {
                if (useClearTreatment) {
                    Shadow(
                        radius = if (isSelectedIndicator) 14.dp else 18.dp,
                        alpha = if (isSelectedIndicator) 0.28f else 0.28f,
                    )
                } else {
                    Shadow.Default
                }
            },
            innerShadow = if (useClearTreatment) {
                {
                    InnerShadow(
                        radius = if (isSelectedIndicator) 7.dp else 8.dp,
                        alpha = if (isSelectedIndicator) 0.42f else 0.34f,
                    )
                }
            } else {
                null
            },
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(upstreamModifier)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        glassTint.copy(
                            alpha = if (useClearTreatment) {
                                (glassTint.alpha * if (isSelectedIndicator) 0.2f else 0.72f)
                                    .coerceIn(0f, 1f)
                            } else {
                                glassTint.alpha
                            },
                        ),
                        colors.surfaceContainer.copy(
                            alpha = if (useClearTreatment) {
                                (glassTint.alpha * if (isSelectedIndicator) 0.08f else 0.42f)
                                    .coerceIn(0f, 1f)
                            } else {
                                (glassTint.alpha * 0.9f).coerceIn(0f, 1f)
                            },
                        ),
                    ),
                ),
            )
            .border(
                width = when {
                    isSelectedIndicator && useClearTreatment -> 1.5.dp
                    else -> 1.dp
                },
                brush = when {
                    isSelectedIndicator && useClearTreatment -> Brush.linearGradient(
                        colors = listOf(
                            // A selected tab is clear glass, not a blue
                            // button. The neutral edge makes the lens and its
                            // chromatic fringe visible without tinting the
                            // entire navigation surface.
                            Color.White.copy(alpha = 0.82f),
                            Color.White.copy(alpha = 0.34f),
                            colors.outlineVariant.copy(alpha = 0.22f),
                        ),
                    )
                    backdrop?.enabled == true && useClearTreatment ->
                        Brush.linearGradient(
                            colors = listOf(
                                colors.outlineVariant.copy(alpha = 0.5f),
                                colors.outlineVariant.copy(alpha = 0.18f),
                            ),
                        )
                    else -> SolidColor(
                        if (backdrop?.enabled == true) colors.outlineVariant.copy(alpha = 0.82f)
                        else colors.outlineVariant,
                    )
                },
                shape = shape,
            ),
    ) {
        if (backdrop?.enabled == true && (surfaceRole != GlassSurfaceRole.NAVIGATION || useClearTreatment)) {
            Box(
                Modifier
                    // This decorative layer must not size a wrap-content glass
                    // surface to the full available height. It should follow
                    // the measured surface after the content determines size.
                    // Regular navigation keeps a restrained single surface
                    // wash; Clear adds this localized highlight to preserve
                    // the lighter, more open material treatment.
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.surfaceBright.copy(
                                    alpha = when {
                                        isSelectedIndicator && useClearTreatment -> 0.05f
                                        useClearTreatment -> 0.1f
                                        else -> 0.18f
                                    },
                                ),
                                Color.Transparent,
                                colors.surfaceDim.copy(
                                    alpha = if (useClearTreatment) 0.04f else 0.1f,
                                ),
                            ),
                        ),
                    ),
            )
        }
        content()
    }
}
