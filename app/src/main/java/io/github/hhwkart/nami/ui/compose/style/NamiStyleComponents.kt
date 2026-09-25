package io.github.hhwkart.nami.ui.compose.style

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button as MaterialButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Functional surface treatment used by navigation and other opt-in controls.
 * Standard mode deliberately keeps the existing Material 3 surface metrics.
 */
@Composable
fun NamiNavigationSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge,
    backdrop: NamiBackdrop? = null,
    surfaceRole: GlassSurfaceRole = GlassSurfaceRole.NAVIGATION,
    containerAlpha: Float? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val visualStyle = LocalNamiVisualStyle.current
    val colors = MaterialTheme.colorScheme
    val resolvedContainerAlpha = containerAlpha?.coerceIn(0f, 1f)

    if (!visualStyle.liquidEnabled) {
        Surface(
            modifier = modifier.shadow(14.dp, shape, clip = false),
            shape = shape,
            color = colors.surfaceContainerHigh.copy(alpha = resolvedContainerAlpha ?: 1f),
            tonalElevation = if (resolvedContainerAlpha == null) 8.dp else 0.dp,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.72f)),
        ) {
            Box(content = content)
        }
        return
    }

    NamiGlassOverlay(
        modifier = modifier.shadow(18.dp, shape, clip = false),
        shape = shape,
        backdrop = backdrop,
        tint = colors.surfaceContainerHigh.copy(
            alpha = resolvedContainerAlpha ?: if (visualStyle.isClearGlass) {
                // Clear stays visually open only when a real backdrop is
                // available. Its fallback remains dense enough to preserve
                // navigation contrast on older or constrained devices.
                when (visualStyle.effectiveTier) {
                    LiquidGlassTier.TINTED -> 0.86f
                    LiquidGlassTier.BLUR -> 0.16f
                    LiquidGlassTier.BLUR_AND_LENS -> 0.09f
                    LiquidGlassTier.MATERIAL -> 0.86f
                }
            } else {
                when (visualStyle.effectiveTier) {
                    LiquidGlassTier.TINTED -> 0.82f
                    LiquidGlassTier.BLUR -> 0.69f
                    LiquidGlassTier.BLUR_AND_LENS -> 0.63f
                    LiquidGlassTier.MATERIAL -> 0.82f
                }
            },
        ),
        surfaceRole = surfaceRole,
        content = content,
    )
}

/** Alias for bounded glass controls that are not navigation surfaces. */
@Composable
fun NamiGlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    backdrop: NamiBackdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    NamiNavigationSurface(
        modifier = modifier,
        shape = shape,
        backdrop = backdrop,
        surfaceRole = GlassSurfaceRole.CONTENT,
        content = content,
    )
}

/**
 * Shared Base/Elevated card chrome. Liquid Glass cards use a translucent
 * elevated wash and a light edge; Standard mode keeps a conventional opaque
 * Material surface with the same shape and click semantics.
 */
@Composable
fun NamiCard(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    tint: androidx.compose.ui.graphics.Color? = null,
    showMaterialBorder: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val visualStyle = LocalNamiVisualStyle.current
    val interactionModifier = onClick?.let {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = it)
    } ?: Modifier

    if (visualStyle.liquidEnabled) {
        NamiGlassOverlay(
            modifier = modifier.then(interactionModifier),
            shape = shape,
            tint = (tint ?: MaterialTheme.colorScheme.surfaceContainerHigh).copy(
                alpha = if (visualStyle.isClearGlass) 0.54f else 0.82f,
            ),
            surfaceRole = GlassSurfaceRole.CARD,
            content = content,
        )
    } else {
        Surface(
            modifier = modifier.then(interactionModifier),
            shape = shape,
            color = tint ?: MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 2.dp,
            border = if (showMaterialBorder) {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
            } else {
                null
            },
        ) {
            Box(content = content)
        }
    }
}

/**
 * Filled action button adapted from upstream LiquidButton.
 * [backdrop], when supplied, must exclude this button from its source layer.
 * The NavHost backdrop cannot be supplied because it contains this button.
 */
@Composable
fun NamiLiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isInteractive: Boolean = true,
    backdrop: NamiBackdrop? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val visualStyle = LocalNamiVisualStyle.current
    if (!visualStyle.liquidEnabled) {
        MaterialButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            content = content,
        )
        return
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlightSpring = if (visualStyle.reduceMotion) {
        snap()
    } else {
        spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    }
    val highlightProgress by animateFloatAsState(
        targetValue = if (isInteractive && isPressed) 1f else 0f,
        animationSpec = highlightSpring,
        label = "liquidButtonHighlightProgress",
    )
    var highlightStart by remember { mutableStateOf(Offset.Zero) }
    var highlightPointer by remember { mutableStateOf(Offset.Zero) }
    var buttonSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            if (interaction is PressInteraction.Press) {
                highlightStart = interaction.pressPosition
                highlightPointer = interaction.pressPosition
            }
        }
    }
    val highlightPositionSpring = if (visualStyle.reduceMotion) {
        snap()
    } else {
        spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.5f)
    }
    val highlightX by animateFloatAsState(
        targetValue = if (isPressed) highlightPointer.x else highlightStart.x,
        animationSpec = highlightPositionSpring,
        label = "liquidButtonHighlightX",
    )
    val highlightY by animateFloatAsState(
        targetValue = if (isPressed) highlightPointer.y else highlightStart.y,
        animationSpec = highlightPositionSpring,
        label = "liquidButtonHighlightY",
    )
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(percent = 50)
    val source = backdrop?.renderBackdrop
    val canRefractSource = source != null &&
        (visualStyle.effectiveTier == LiquidGlassTier.BLUR ||
            visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS)
    val sourceModifier = if (canRefractSource) {
        Modifier.drawBackdrop(
            backdrop = source,
            shape = { shape },
            effects = {
                vibrancy()
                blur(2.dp.toPx())
                if (visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS) {
                    lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
                }
            },
            layerBlock = if (isInteractive) {
                {
                    val width = size.width
                    val height = size.height
                    val progress = highlightProgress
                    val scale = 1f + (4.dp.toPx() / size.height) * progress
                    val maxOffset = size.minDimension
                    val offsetX = highlightX - highlightStart.x
                    val offsetY = highlightY - highlightStart.y
                    val initialDerivative = 0.05f
                    translationX = maxOffset * tanh(initialDerivative * offsetX / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offsetY / maxOffset)

                    val maxDragScale = 4.dp.toPx() / size.height
                    val offsetAngle = atan2(offsetY, offsetX)
                    scaleX = scale + maxDragScale *
                        abs(cos(offsetAngle) * offsetX / maxOf(width, height)) *
                        (width / height).coerceAtMost(1f)
                    scaleY = scale + maxDragScale *
                        abs(sin(offsetAngle) * offsetY / maxOf(width, height)) *
                        (height / width).coerceAtMost(1f)
                }
            } else {
                null
            },
            highlight = { Highlight.Default.copy(alpha = 0.48f + 0.32f * highlightProgress) },
            shadow = { Shadow.Default },
            onDrawSurface = {
                drawRect(colors.surfaceBright.copy(alpha = if (isPressed) 0.1f else 0.16f))
            },
        )
    } else {
        Modifier
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.surfaceBright.copy(alpha = if (visualStyle.isClearGlass) 0.34f else 0.86f),
                        colors.surfaceContainerHigh.copy(alpha = if (visualStyle.isClearGlass) 0.2f else 0.72f),
                    ),
                ),
                shape,
            )
            .border(
                BorderStroke(
                    1.dp,
                    colors.outlineVariant.copy(alpha = if (visualStyle.isClearGlass) 0.74f else 0.9f),
                ),
                shape,
            )
    }
    val pointerHighlightModifier = Modifier.drawWithContent {
        if (isInteractive && highlightProgress > 0f) {
            drawRect(Color.White.copy(alpha = 0.08f * highlightProgress), blendMode = BlendMode.Plus)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.15f * highlightProgress),
                        Color.Transparent,
                    ),
                    center = Offset(highlightX, highlightY),
                    radius = size.minDimension * 1.5f,
                ),
                blendMode = BlendMode.Plus,
            )
        }
        drawContent()
    }
    val fallbackScale = 1f + (4.dp.value / 48f) * highlightProgress

    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 64.dp)
            .then(sourceModifier)
            .onSizeChanged { buttonSize = it }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = if (isInteractive) null else LocalIndication.current,
                role = Role.Button,
                onClick = onClick,
            )
            .pointerInput(isInteractive, enabled) {
                if (isInteractive && enabled) {
                    detectDragGestures(
                        onDragStart = { highlightPointer = it },
                        onDragEnd = { highlightPointer = highlightStart },
                        onDragCancel = { highlightPointer = highlightStart },
                        onDrag = { change, _ ->
                            change.consume()
                            highlightPointer = change.position
                        },
                    )
                }
            }
            .then(pointerHighlightModifier)
            .graphicsLayer {
                if (!canRefractSource) {
                    val width = buttonSize.width.toFloat().coerceAtLeast(1f)
                    val height = buttonSize.height.toFloat().coerceAtLeast(1f)
                    val offsetX = highlightX - highlightStart.x
                    val offsetY = highlightY - highlightStart.y
                    val maxOffset = minOf(width, height)
                    val maxDimension = maxOf(width, height)
                    val offsetAngle = atan2(offsetY, offsetX)
                    val maxDragScale = 4.dp.toPx() / height
                    val scale = fallbackScale
                    scaleX = scale + maxDragScale *
                        abs(cos(offsetAngle) * offsetX / maxDimension) *
                        (width / height).coerceAtMost(1f)
                    scaleY = scale + maxDragScale *
                        abs(sin(offsetAngle) * offsetY / maxDimension) *
                        (height / width).coerceAtMost(1f)
                    translationX = maxOffset * tanh(0.05f * offsetX / maxOffset)
                    translationY = maxOffset * tanh(0.05f * offsetY / maxOffset)
                }
                alpha = if (enabled) 1f else 0.5f
            }
            .height(48.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/**
 * Apple-like Clear Glass switch. The track exports only its own local layer;
 * the thumb samples that layer through the upstream Liquid Glass pipeline.
 * This mirrors the reference component topology and avoids sampling the
 * NavHost root, which would include the switch itself and recurse on RenderThread.
 */
@Composable
fun NamiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String? = null,
) {
    val descriptionModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }
    val visualStyle = LocalNamiVisualStyle.current

    if (!visualStyle.liquidEnabled) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier.then(descriptionModifier),
            enabled = enabled,
        )
        return
    }

    val interactive = enabled && onCheckedChange != null
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragDirection = if (isLtr) 1f else -1f
    // LiquidToggle uses a 20dp travel range inside a 64dp capsule track.
    val travel = 20.dp
    val travelPx = with(density) { travel.toPx() }
    val animationScope = rememberCoroutineScope()
    // Release velocity is normalized to the thumb's fraction range, so its
    // visibility threshold must be small enough to retain the spring response.
    val velocityAnimation = remember { Animatable(0f, 0.001f) }
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember(checked) {
        mutableFloatStateOf(if (checked) 1f else 0f)
    }
    LaunchedEffect(checked) {
        if (!isDragging) {
            dragFraction = if (checked) 1f else 0f
        }
    }
    val dragSpring = if (visualStyle.reduceMotion) {
        snap()
    } else {
        spring<Float>(dampingRatio = 1f, stiffness = 1000f, visibilityThreshold = 0.001f)
    }
    val fraction by animateFloatAsState(
        targetValue = if (isDragging) dragFraction else if (checked) 1f else 0f,
        animationSpec = dragSpring,
        label = "liquidSwitchPosition",
    )
    val pressedProgress by animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1f else 0f,
        animationSpec = dragSpring,
        label = "liquidSwitchPressProgress",
    )
    val animatedScaleX by animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1.5f else 1f,
        animationSpec = if (visualStyle.reduceMotion) snap() else spring(0.6f, 250f, 0.001f),
        label = "liquidSwitchScaleX",
    )
    val animatedScaleY by animateFloatAsState(
        targetValue = if (isPressed || isDragging) 1.5f else 1f,
        animationSpec = if (visualStyle.reduceMotion) snap() else spring(0.7f, 250f, 0.001f),
        label = "liquidSwitchScaleY",
    )
    val isLightTheme = !isSystemInDarkTheme()
    val trackWidth = 64.dp
    val trackHeight = 28.dp
    val thumbWidth = 40.dp
    val thumbHeight = 24.dp
    val trackShape = RoundedCornerShape(percent = 50)
    val accentColor = if (isLightTheme) {
        androidx.compose.ui.graphics.Color(0xFF34C759)
    } else {
        androidx.compose.ui.graphics.Color(0xFF30D158)
    }.copy(alpha = if (enabled) 1f else 0.42f)
    val inactiveTrackColor = if (isLightTheme) {
        androidx.compose.ui.graphics.Color(0xFF787878).copy(alpha = if (enabled) 0.2f else 0.12f)
    } else {
        androidx.compose.ui.graphics.Color(0xFF787880).copy(alpha = if (enabled) 0.36f else 0.2f)
    }
    val trackColor = lerp(inactiveTrackColor, accentColor, fraction)
    val useBackdrop = visualStyle.effectiveTier == LiquidGlassTier.BLUR ||
        visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS
    val trackBackdrop = if (useBackdrop) rememberLayerBackdrop() else null
    val normalizedVelocity = velocityAnimation.value / 50f

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = trackWidth, minHeight = 48.dp)
            .then(descriptionModifier)
            .then(
                if (interactive) {
                    Modifier
                        .toggleable(
                            value = checked,
                            enabled = true,
                            role = Role.Switch,
                            interactionSource = interactionSource,
                            indication = null,
                            onValueChange = { onCheckedChange(it) },
                        )
                        .draggable(
                            state = rememberDraggableState { delta ->
                                if (travelPx > 0f) {
                                    dragFraction = (dragFraction + delta / travelPx * dragDirection)
                                        .coerceIn(0f, 1f)
                                }
                            },
                            orientation = Orientation.Horizontal,
                            enabled = true,
                            onDragStarted = {
                                isDragging = true
                                dragFraction = if (checked) 1f else 0f
                            },
                            onDragStopped = { velocity ->
                                animationScope.launch {
                                    velocityAnimation.snapTo(velocity / travelPx * dragDirection)
                                    velocityAnimation.animateTo(
                                        targetValue = 0f,
                                        animationSpec = if (visualStyle.reduceMotion) {
                                            snap()
                                        } else {
                                            spring(0.5f, 300f, 0.01f)
                                        },
                                    )
                                }
                                val target = dragFraction >= 0.5f
                                isDragging = false
                                dragFraction = if (target) 1f else 0f
                                if (target != checked) {
                                    onCheckedChange(target)
                                }
                            },
                        )
                } else {
                    Modifier
                },
            )
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!interactive) disabled()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(trackBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier)
                .clip(trackShape)
                .size(trackWidth, trackHeight)
                .drawBehind { drawRect(trackColor) },
        )
        // offset(x) is relative to layout direction, so the same positive
        // logical value moves the capsule inward from either end of the track.
        val thumbOffset = 2.dp + travel * fraction
        val thumbSurface = if (trackBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = trackBackdrop,
                shape = { trackShape },
                effects = {
                    blur(8.dp.toPx() * (1f - pressedProgress))
                    if (visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS) {
                        lens(
                            refractionHeight = 5.dp.toPx() * pressedProgress,
                            refractionAmount = 10.dp.toPx() * pressedProgress,
                            chromaticAberration = true,
                        )
                    }
                },
                layerBlock = {
                    scaleX = animatedScaleX
                    scaleY = animatedScaleY
                    scaleX /= 1f - (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f)
                    scaleY *= 1f - (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f)
                },
                highlight = {
                    Highlight.Ambient.copy(
                        width = Highlight.Ambient.width / 1.5f,
                        blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                        alpha = pressedProgress * if (enabled) 1f else 0.42f,
                    )
                },
                shadow = {
                    Shadow(
                        radius = 4.dp,
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.05f),
                    )
                },
                innerShadow = {
                    InnerShadow(
                        radius = 4.dp * pressedProgress,
                        alpha = pressedProgress,
                    )
                },
                onDrawSurface = {
                    drawRect(
                        androidx.compose.ui.graphics.Color.White.copy(
                            alpha = (1f - pressedProgress) * if (enabled) 1f else 0.48f,
                        ),
                    )
                },
            )
        } else {
            Modifier
                .graphicsLayer {
                    scaleX = animatedScaleX /
                        (1f - (normalizedVelocity * 0.75f).coerceIn(-0.2f, 0.2f))
                    scaleY = animatedScaleY *
                        (1f - (normalizedVelocity * 0.25f).coerceIn(-0.2f, 0.2f))
                }
                .shadow(4.dp, trackShape, clip = false)
                .clip(trackShape)
                .background(
                    androidx.compose.ui.graphics.Color.White.copy(
                        alpha = if (enabled) 0.08f else 0.04f,
                    ),
                    trackShape,
                )
        }
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbWidth, thumbHeight)
                .then(thumbSurface)
                .clip(trackShape),
        )
    }
}
