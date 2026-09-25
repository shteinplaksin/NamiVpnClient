package io.github.hhwkart.nami.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selectableGroup
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.PI
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.DefaultNavTransitions
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.preference.OnPreferenceDataStoreChangeListener
import io.github.hhwkart.nami.ui.MainActivity
import io.github.hhwkart.nami.ui.compose.editor.ProtocolEditorScreen
import io.github.hhwkart.nami.ui.compose.editor.ProtocolKind
import io.github.hhwkart.nami.ui.compose.groups.GroupsScreen
import io.github.hhwkart.nami.ui.compose.groupeditor.GroupEditorScreen
import io.github.hhwkart.nami.ui.compose.home.HomeScreen
import io.github.hhwkart.nami.ui.compose.importexport.FunctionalAddImportScreen
import io.github.hhwkart.nami.ui.compose.importexport.FunctionalBackupRestoreScreen
import io.github.hhwkart.nami.ui.compose.perapp.PerAppProxyScreen
import io.github.hhwkart.nami.ui.compose.profiles.ProfilesScreen
import io.github.hhwkart.nami.ui.compose.routing.RoutingScreen
import io.github.hhwkart.nami.ui.compose.routeeditor.RouteEditorScreen
import io.github.hhwkart.nami.ui.compose.scanner.QrScannerScreen
import io.github.hhwkart.nami.ui.compose.settings.SettingsScreen
import io.github.hhwkart.nami.ui.compose.style.GlassSurfaceRole
import io.github.hhwkart.nami.ui.compose.style.LiquidGlassTier
import io.github.hhwkart.nami.ui.compose.style.NamiNavigationSurface
import io.github.hhwkart.nami.ui.compose.style.LocalNamiVisualStyle
import io.github.hhwkart.nami.ui.compose.style.NamiBackdrop
import io.github.hhwkart.nami.ui.compose.style.NamiGlassBackdropHost
import io.github.hhwkart.nami.ui.compose.style.NamiGlassOverlay
import io.github.hhwkart.nami.ui.compose.style.namiBackdropSource
import io.github.hhwkart.nami.ui.compose.style.withRenderBackdrop
import io.github.hhwkart.nami.ui.compose.theme.tvFocusable
import io.github.hhwkart.nami.ui.compose.common.UserFacingError
import io.github.hhwkart.nami.ui.compose.common.UserFacingErrorKind
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import io.github.hhwkart.nami.ui.compose.startup.OnboardingScreen
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupScreen
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupBus
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupResult
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupReturnTarget
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupSource
import io.github.hhwkart.nami.ui.compose.startup.WhatsNewScreen
import io.github.hhwkart.nami.ui.compose.tools.AssetsScreen
import io.github.hhwkart.nami.ui.compose.tools.StunTestScreen
import io.github.hhwkart.nami.ui.compose.utility.AboutScreen
import io.github.hhwkart.nami.ui.compose.utility.DashboardScreen
import io.github.hhwkart.nami.ui.compose.utility.LogScreen
import io.github.hhwkart.nami.ui.compose.utility.ToolsScreen
import kotlinx.coroutines.flow.collect
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

private data class MainDestination(
    val route: Destination,
    val icon: ImageVector,
    val labelRes: Int,
)

private val mainDestinations = listOf(
    MainDestination(Destination.Home, Icons.Filled.Home, R.string.menu_home),
    MainDestination(Destination.Profiles, Icons.Filled.Person, R.string.menu_profiles),
    MainDestination(Destination.Groups, Icons.AutoMirrored.Filled.List, R.string.menu_groups),
    MainDestination(Destination.Routing, Icons.Filled.Public, R.string.menu_routing),
    MainDestination(Destination.Settings, Icons.Filled.Settings, R.string.menu_settings),
)
private val mainDestinationRoutes = mainDestinations.map { it.route::class.qualifiedName }

private val floatingTabBarShape = RoundedCornerShape(percent = 50)
private val liquidTabBarPalette = listOf(
    Color.hsv(42f, saturation = 0.72f, value = 1f),
    Color.hsv(25f, saturation = 0.68f, value = 1f),
    Color.hsv(326f, saturation = 0.48f, value = 1f),
    Color.hsv(278f, saturation = 0.58f, value = 1f),
    Color.hsv(42f, saturation = 0.72f, value = 1f),
)

private object FloatingTabBarMetrics {
    // Keep the floating control compact on phones and wide windows alike. The
    // navigation gesture inset is reserved outside this surface, so reducing
    // the glass itself does not put content under the system bar.
    val surfaceHeight = 60.dp
    val contentVerticalPadding = 8.dp
    val outerHorizontalPadding = 8.dp
    val outerVerticalPadding = 10.dp
    val contentHorizontalPadding = 4.dp
    val itemSpacing = 4.dp
    val indicatorDiameter = 40.dp
    val reservedHeight = surfaceHeight + outerVerticalPadding * 2
    // 264dp surface + 16dp outer breathing room. The 8dp content inset and
    // 16dp between five tabs leave exactly 48dp for each target.
    val maxWidth = 280.dp
}

@Composable
private fun rememberLiquidTabBarEdgePhase(enabled: Boolean, reduceMotion: Boolean): State<Float>? {
    if (!enabled || reduceMotion) return null

    val transition = rememberInfiniteTransition(label = "liquidTabBarEdgeGlow")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 14_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "liquidTabBarEdgeGlowPhase",
    )
}

@Composable
private fun LiquidTabBarEdgeGlow(
    modifier: Modifier,
    colorStops: State<Array<Pair<Float, Color>>>,
    blurSupported: Boolean,
) {
    val bloomModifier = if (blurSupported) modifier.blur(12.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded) else modifier
    Canvas(bloomModifier) {
        val left = FloatingTabBarMetrics.outerHorizontalPadding.toPx()
        val top = FloatingTabBarMetrics.outerVerticalPadding.toPx()
        val width = (size.width - left * 2f).coerceAtLeast(0f)
        val height = FloatingTabBarMetrics.surfaceHeight.toPx()
        val center = Offset(left + width / 2f, top + height / 2f)
        val edgeBrush = Brush.sweepGradient(colorStops = colorStops.value, center = center)
        drawRoundRect(
            brush = edgeBrush,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(height / 2f),
            style = Stroke(width = if (blurSupported) 6.dp.toPx() else 2.dp.toPx()),
            blendMode = BlendMode.Screen,
        )
    }
}

private fun Modifier.liquidTabBarEdgeRim(
    colorStops: State<Array<Pair<Float, Color>>>,
): Modifier = drawWithContent {
    drawContent()
    val edgeBrush = Brush.sweepGradient(
        colorStops = colorStops.value,
        center = Offset(size.width / 2f, size.height / 2f),
    )
    drawRoundRect(
        brush = edgeBrush,
        topLeft = Offset.Zero,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(size.height / 2f),
        style = Stroke(width = 1.25.dp.toPx()),
        blendMode = BlendMode.Screen,
    )
}

private fun liquidTabBarEdgeColorStops(
    phase: Float,
    darkSurface: Boolean,
): Array<Pair<Float, Color>> {
    val baseAlpha = if (darkSurface) 0.12f else 0.08f
    val glowAlpha = if (darkSurface) 0.34f else 0.24f

    return Array(49) { index ->
        val position = index / 48f
        val shiftedPosition = (position + phase) % 1f
        val palettePosition = shiftedPosition * (liquidTabBarPalette.lastIndex)
        val paletteIndex = palettePosition.toInt().coerceAtMost(liquidTabBarPalette.lastIndex - 1)
        val fraction = palettePosition - paletteIndex
        val start = liquidTabBarPalette[paletteIndex]
        val end = liquidTabBarPalette[paletteIndex + 1]
        val hueColor = Color(
            red = start.red + (end.red - start.red) * fraction,
            green = start.green + (end.green - start.green) * fraction,
            blue = start.blue + (end.blue - start.blue) * fraction,
        )
        val pulse = ((cos(2f * PI.toFloat() * (position * 2f - phase)) + 1f) / 2f)
        val alpha = baseAlpha + pulse * glowAlpha
        position to hueColor.copy(alpha = alpha)
    }
}

private fun liquidDropletShape(stretch: Float, movingRight: Boolean): Shape = GenericShape { size, _ ->
    val height = size.height
    val width = size.width
    val middle = height * 0.5f
    val endRadius = minOf(height * 0.5f, width * 0.5f)
    val tailLength = width * 0.18f * stretch.coerceIn(0f, 1f)
    this.apply {
        if (movingRight) {
            moveTo(tailLength + endRadius, 0f)
            lineTo(width - endRadius, 0f)
            cubicTo(width - endRadius * 0.55f, 0f, width, endRadius * 0.55f, width, middle)
            cubicTo(width, height - endRadius * 0.55f, width - endRadius * 0.55f, height, width - endRadius, height)
            lineTo(tailLength + endRadius, height)
            cubicTo(
                tailLength + endRadius * 0.55f,
                height,
                tailLength,
                height * 0.77f,
                0f,
                middle,
            )
            cubicTo(
                tailLength,
                height * 0.23f,
                tailLength + endRadius * 0.55f,
                0f,
                tailLength + endRadius,
                0f,
            )
        } else {
            moveTo(width - tailLength - endRadius, 0f)
            lineTo(endRadius, 0f)
            cubicTo(endRadius * 0.55f, 0f, 0f, endRadius * 0.55f, 0f, middle)
            cubicTo(0f, height - endRadius * 0.55f, endRadius * 0.55f, height, endRadius, height)
            lineTo(width - tailLength - endRadius, height)
            cubicTo(
                width - tailLength - endRadius * 0.55f,
                height,
                width - tailLength,
                height * 0.77f,
                width,
                middle,
            )
            cubicTo(
                width - tailLength,
                height * 0.23f,
                width - tailLength - endRadius * 0.55f,
                0f,
                width - tailLength - endRadius,
                0f,
            )
        }
        close()
    }
}

internal data class LiquidTabIndicatorMotion(
    val position: Float,
    val stretch: Float,
    val impact: Float,
    val movingRight: Boolean,
    val impactSide: Float,
)

internal fun liquidTabIndicatorMotion(
    rawPosition: Float,
    velocity: Float,
    lastIndex: Int,
    isLtr: Boolean,
    reduceMotion: Boolean,
): LiquidTabIndicatorMotion {
    val position = rawPosition.coerceIn(0f, lastIndex.toFloat())
    val overshoot = rawPosition - position
    val impact = if (reduceMotion) 0f else (kotlin.math.abs(overshoot) * 3.5f).coerceIn(0f, 1f)
    val stretch = if (reduceMotion) 0f else
        (kotlin.math.abs(velocity) / 12f).coerceIn(0f, 1f) * (1f - impact * 0.75f)
    val physicalDirection = velocity * if (isLtr) 1f else -1f
    val impactSide = when {
        overshoot > 0f -> if (isLtr) 1f else -1f
        overshoot < 0f -> if (isLtr) -1f else 1f
        else -> 0f
    }
    return LiquidTabIndicatorMotion(
        position = position,
        stretch = stretch,
        impact = impact,
        movingRight = physicalDirection >= 0f,
        impactSide = impactSide,
    )
}
enum class ConfirmationDialog {
    RELOAD_SERVICE,
    FORCE_CONFIG_RELOAD_SERVICE,
    RESTART_APP,
}

@Composable
fun NamiAppShell(
    activity: MainActivity,
    confirmationDialog: ConfirmationDialog? = null,
    onDismissConfirmation: () -> Unit = {},
    onConfirmConfirmation: (ConfirmationDialog) -> Unit = {},
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    var clashApiEnabled by remember {
        mutableStateOf(DataStore.enableClashAPI)
    }
    var showBottomBar by remember {
        mutableStateOf(DataStore.showBottomBar)
    }
    DisposableEffect(Unit) {
        val listener = object : OnPreferenceDataStoreChangeListener {
            override fun onPreferenceDataStoreChanged(
                store: androidx.preference.PreferenceDataStore,
                key: String,
            ) {
                if (key == Key.SHOW_BOTTOM_BAR) {
                    showBottomBar = DataStore.showBottomBar
                }
            }
        }
        DataStore.configurationStore.registerChangeListener(listener)
        onDispose {
            DataStore.configurationStore.unregisterChangeListener(listener)
        }
    }
    DisposableEffect(activity, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                activity.synchronizeLocalNetworkPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun navigateTopLevel(destination: Destination) {
        navController.navigate(destination) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateAfterQuickSetup(target: QuickSetupReturnTarget) {
        when (target) {
            is QuickSetupReturnTarget.PopScreens -> {
                repeat(target.count) {
                    if (!navController.popBackStack()) return
                }
                return
            }
            is QuickSetupReturnTarget.ToDestination -> {
                val destination = target.destination
                if ((destination == Destination.Settings || destination == Destination.About) &&
                    navController.popBackStack(destination::class.qualifiedName!!, inclusive = false)
                ) {
                    return
                }
                // Quick Setup is a transient flow. Returning to Home must not
                // restore onboarding state; Profiles may keep its saved list.
                val preserveTargetState = destination != Destination.Home
                navController.navigate(destination) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = preserveTargetState
                    }
                    launchSingleTop = true
                    restoreState = preserveTargetState
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        NavigationBus.requests.collect { request ->
            val destination = when (request) {
                is NavigationRequest.Open -> request.destination
                is NavigationRequest.ReturnTo -> request.destination
                is NavigationRequest.ReturnToPrevious -> null
            }
            if (request is NavigationRequest.ReturnToPrevious) {
                repeat(request.screenCount) {
                    if (!navController.popBackStack()) return@collect
                }
            } else if (destination != null) {
                val route = destination::class.qualifiedName
                val returnedToExistingDestination = request is NavigationRequest.ReturnTo &&
                    route != null && navController.popBackStack(route, inclusive = false)
                if (!returnedToExistingDestination) {
                    if (mainDestinations.any { it.route == destination }) {
                        navigateTopLevel(destination)
                    } else {
                        navController.navigate(destination) { launchSingleTop = true }
                    }
                }
            }
        }
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val currentDestination = mainDestinations.find { dest ->
        currentRoute == dest.route::class.qualifiedName
    }

    val itemColors = NavigationSuiteDefaults.itemColors()
    val adaptiveInfo = currentWindowAdaptiveInfo()
    val navSuiteType = NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(adaptiveInfo)
    val isBottomBar = navSuiteType == NavigationSuiteType.NavigationBar

    val isTopLevel = if (isBottomBar) {
        currentDestination != null
    } else {
        currentDestination != null ||
            currentRoute == Destination.Tools::class.qualifiedName ||
            currentRoute == Destination.Log::class.qualifiedName ||
            currentRoute == Destination.About::class.qualifiedName ||
            (clashApiEnabled && currentRoute == Destination.Dashboard::class.qualifiedName)
    }

    // Keep the persisted opt-out behavior while using the floating surface on
    // every window size when it is enabled.
    val showFloatingTabBar = showBottomBar && currentDestination != null
    val mainBottomPadding = if (showFloatingTabBar) FloatingTabBarMetrics.reservedHeight else 0.dp
    val effectiveLayoutType = when {
        !isTopLevel -> NavigationSuiteType.None
        showFloatingTabBar -> NavigationSuiteType.None
        isBottomBar -> NavigationSuiteType.None
        else -> navSuiteType
    }

    NavigationSuiteScaffold(
        layoutType = effectiveLayoutType,
        containerColor = Color.Transparent,
        navigationSuiteItems = {
            mainDestinations.forEach { dest ->
                item(
                    selected = currentDestination?.route == dest.route,
                    onClick = { navigateTopLevel(dest.route) },
                    icon = { Icon(dest.icon, contentDescription = null) },
                    label = { Text(stringResource(dest.labelRes)) },
                    colors = itemColors,
                )
            }
            // Secondary Compose destinations remain in rail / drawer and are not
            // crammed into the compact primary tab bar.
            if (!isBottomBar) {
                item(
                    selected = currentRoute == Destination.Tools::class.qualifiedName,
                    onClick = { navController.navigate(Destination.Tools) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Place, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_tools)) },
                    colors = itemColors,
                )
                item(
                    selected = currentRoute == Destination.Log::class.qualifiedName,
                    onClick = { navController.navigate(Destination.Log) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_log)) },
                    colors = itemColors,
                )
                item(
                    selected = currentRoute == Destination.About::class.qualifiedName,
                    onClick = { navController.navigate(Destination.About) { launchSingleTop = true } },
                    icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                    label = { Text(stringResource(R.string.menu_about)) },
                    colors = itemColors,
                )
                if (clashApiEnabled) {
                    item(
                        selected = currentRoute == Destination.Dashboard::class.qualifiedName,
                        onClick = { navController.navigate(Destination.Dashboard) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        label = { Text(stringResource(R.string.menu_dashboard)) },
                        colors = itemColors,
                    )
                }
            }
        },
    ) {
        NamiGlassBackdropHost(
            modifier = Modifier.fillMaxSize(),
            enabled = LocalNamiVisualStyle.current.liquidEnabled,
            rendererTier = LocalNamiVisualStyle.current.effectiveTier,
        ) { backdrop ->
            val isMainDestinationTransition: (String?, String?) -> Boolean = { from, to ->
                from in mainDestinationRoutes && to in mainDestinationRoutes
            }
            val enterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
                if (isMainDestinationTransition(initialState.destination.route, targetState.destination.route)) {
                    EnterTransition.None
                } else {
                    DefaultNavTransitions.enterTransition(this)
                }
            }
            val exitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
                if (isMainDestinationTransition(initialState.destination.route, targetState.destination.route)) {
                    ExitTransition.None
                } else {
                    DefaultNavTransitions.exitTransition(this)
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .namiBackdropSource(backdrop),
                ) {
                                NavHost(
                    navController = navController,
                    startDestination = Destination.Home,
                    enterTransition = enterTransition,
                    exitTransition = exitTransition,
                    popEnterTransition = DefaultNavTransitions.popEnterTransition(enterTransition),
                    popExitTransition = DefaultNavTransitions.popExitTransition(exitTransition),
                ) {
                composable<Destination.Home> {
                    HomeScreen(bottomBarPadding = mainBottomPadding)
                }
                composable<Destination.Profiles> {
                    ProfilesScreen(
                        bottomBarPadding = mainBottomPadding,
                        onEditProfile = { profile ->
                            navController.navigate(
                                Destination.ProtocolEditor(
                                    profileId = profile.id,
                                    protocolType = ProtocolKind.fromEntity(profile.entity).routeKey,
                                )
                            )
                        },
                        onAddProfile = { navController.navigate(Destination.AddImport) },
                        onQuickSetup = activity::openQuickSetupFromProfiles,
                    )
                }
                composable<Destination.Groups> {
                    GroupsScreen(
                        bottomBarPadding = mainBottomPadding,
                        onEditGroup = { group ->
                            navController.navigate(Destination.GroupEditor(group.id))
                        },
                    )
                }
                composable<Destination.Routing> {
                    RoutingScreen(
                        bottomBarPadding = mainBottomPadding,
                        onAddRule = { navController.navigate(Destination.RouteEditor(0L)) },
                        onEditRule = { rule -> navController.navigate(Destination.RouteEditor(rule.id)) },
                        onAdvanced = { navController.navigate(Destination.RouteAssets) },
                        onReloadRequired = activity::confirmServiceReload,
                        onForceConfigReloadRequired = activity::confirmForceConfigReload,
                    )
                }
                composable<Destination.Settings> {
                    SettingsScreen(
                        bottomBarPadding = mainBottomPadding,
                        onOpenPerApp = { navController.navigate(Destination.PerAppProxy) },
                        onOpenAbout = { navController.navigate(Destination.About) },
                        onOpenOnboarding = activity::openOnboardingFromSettings,
                        onOpenWhatsNew = activity::openWhatsNewFromSettings,
                        onOpenFaq = { activity.openFaq() },
                        onOpenTools = { navController.navigate(Destination.Tools) },
                        onOpenLog = { navController.navigate(Destination.Log) },
                        onOpenDashboard = { navController.navigate(Destination.Dashboard) },
                        onReloadRequired = activity::confirmServiceReload,
                        onForceConfigReloadRequired = activity::confirmForceConfigReload,
                        onLocalNetworkPermissionCheck = activity::synchronizeLocalNetworkPermission,
                        localNetworkPermissionGranted = activity.localNetworkPermissionGranted,
                        onRestartRequired = activity::confirmAppRestart,
                        onThemeChanged = activity::applyThemeChange,
                        onAppearanceChanged = activity::applyAppearanceChange,
                        onServiceModeChanged = activity::applyServiceModeChange,
                        onClashApiChanged = { enabled ->
                            clashApiEnabled = enabled
                            activity.confirmServiceReload()
                        },
                        onProxyAppsChanged = {
                            navController.navigate(Destination.PerAppProxy)
                            activity.confirmServiceReload()
                        },
                    )
                }
                composable<Destination.PerAppProxy> {
                    PerAppProxyScreen(onBack = { navController.popBackStack() })
                }
                composable<Destination.ProtocolEditor> { entry ->
                    ProtocolEditorScreen(onBack = { navController.popBackStack() })
                }
                composable<Destination.GroupEditor> {
                    GroupEditorScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable<Destination.RouteEditor> {
                    RouteEditorScreen(onBack = { navController.popBackStack() })
                }
                composable<Destination.Tools> {
                    ToolsScreen(
                        onStun = { navController.navigate(Destination.StunTest) },
                        onBackup = { navController.navigate(Destination.BackupRestore) },
                        onRouteAssets = { navController.navigate(Destination.RouteAssets) },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Destination.Log> { LogScreen(onBack = { navController.popBackStack() }) }
                composable<Destination.About> {
                    AboutScreen(
                        onBack = { navController.popBackStack() },
                        onOpenOnboarding = activity::openOnboardingFromAbout,
                        onOpenWhatsNew = activity::openWhatsNewFromAbout,
                    )
                }
                composable<Destination.Onboarding> {
                    OnboardingScreen(
                        onSkip = activity::onOnboardingSkipped,
                        onGetStarted = activity::onOnboardingGetStarted,
                        onFirstPageBack = activity::onOnboardingFirstPageBack,
                    )
                }
                composable<Destination.WhatsNew> {
                    WhatsNewScreen(
                        onOpened = activity::onWhatsNewOpened,
                        onDismiss = activity::onWhatsNewDismissed,
                    )
                }
                composable<Destination.QuickSetup> {
                    QuickSetupScreen(
                        onScanQr = { navController.navigate(Destination.QrScanner) },
                        onManual = { groupId ->
                            navController.navigate(
                                Destination.ProtocolEditor(
                                    profileId = 0L,
                                    protocolType = ProtocolKind.SOCKS.routeKey,
                                    groupId = groupId,
                                ),
                            )
                        },
                        onSubscription = activity::quickSetupSubscription,
                        onApplyPreset = activity::applyRoutingPreset,
                        onDone = { result ->
                            navigateAfterQuickSetup(activity.onQuickSetupFinished(result))
                        },
                        onCancel = {
                            navigateAfterQuickSetup(activity.onQuickSetupCancelled())
                        },
                    )
                }
                composable<Destination.Dashboard> { DashboardScreen(onBack = { navController.popBackStack() }) }
                composable<Destination.AddImport> {
                    FunctionalAddImportScreen(
                        onScanQr = { navController.navigate(Destination.QrScanner) },
                        onSubscriptionUri = activity::importSubscriptionFromCompose,
                        onCreateProfile = { type ->
                            navController.navigate(
                                Destination.ProtocolEditor(
                                    profileId = 0L,
                                    protocolType = type.routeKey,
                                )
                            )
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Destination.QrScanner> {
                    QrScannerScreen(
                        onBack = {
                            activity.onQrScannerFinished()
                            navController.popBackStack()
                        },
                        onSubscriptionUri = { uri ->
                            navController.popBackStack()
                            activity.importSubscriptionFromCompose(uri) { groupId ->
                                QuickSetupBus.publish(
                                    QuickSetupResult(
                                        completed = groupId != null,
                                        resultGroupId = groupId,
                                        source = QuickSetupSource.SUBSCRIPTION,
                                        error = groupId?.let { null } ?: UserFacingError(
                                            kind = UserFacingErrorKind.SUBSCRIPTION,
                                            message = "Subscription import was cancelled or failed.",
                                            hint = "Check the URL and try again.",
                                        ),
                                    ),
                                )
                            }
                        },
                    )
                }
                composable<Destination.BackupRestore> {
                    FunctionalBackupRestoreScreen(
                        onResetSettings = activity::resetSettingsFromCompose,
                        onStopService = io.github.hhwkart.nami.SagerNet::stopService,
                        onRestartAfterImport = activity::restartAfterImport,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<Destination.StunTest> {
                    StunTestScreen(onBack = { navController.popBackStack() })
                }
                composable<Destination.RouteAssets> {
                    AssetsScreen(onBack = { navController.popBackStack() })
                }
                    }
                }
                if (showFloatingTabBar) {
                    FloatingTabBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        selectedDestination = currentDestination,
                        destinations = mainDestinations,
                        backdrop = backdrop,
                        onDestinationSelected = ::navigateTopLevel,
                    )
                }
            confirmationDialog?.let { dialog ->
                NamiAlertDialog(
                    onDismissRequest = onDismissConfirmation,
                    title = { Text(stringResource(R.string.confirm)) },
                    text = {
                        Text(
                            stringResource(
                                when (dialog) {
                                    ConfirmationDialog.RELOAD_SERVICE -> R.string.need_reload
                                    ConfirmationDialog.FORCE_CONFIG_RELOAD_SERVICE -> R.string.need_reload
                                    ConfirmationDialog.RESTART_APP -> R.string.need_restart
                                },
                            ),
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { onConfirmConfirmation(dialog) }) {
                            Text(stringResource(R.string.apply))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = onDismissConfirmation) {
                            Text(stringResource(android.R.string.cancel))
                        }
                    },
                )
            }
        }
    }
}
}

@Composable
private fun FloatingTabBar(
    modifier: Modifier = Modifier,
    selectedDestination: MainDestination?,
    destinations: List<MainDestination>,
    backdrop: NamiBackdrop,
    onDestinationSelected: (Destination) -> Unit,
) {
    if (destinations.isEmpty()) return

    val selectedIndex = destinations.indexOfFirst { destination ->
        destination.route == selectedDestination?.route
    }.coerceAtLeast(0)
    val visualStyle = LocalNamiVisualStyle.current
    val rootRenderBackdrop = backdrop.renderBackdrop
    val edgeGlowPhase = rememberLiquidTabBarEdgePhase(
        enabled = visualStyle.liquidEnabled,
        reduceMotion = visualStyle.reduceMotion,
    )
    val darkSurface = androidx.compose.material3.MaterialTheme.colorScheme.surface.luminance() < 0.28f
    val liquidEnabled = visualStyle.liquidEnabled
    // Read the animated phase only from the draw lambdas below to avoid recomposing the tab bar every frame.
    val edgeColorStops = remember(edgeGlowPhase, darkSurface, liquidEnabled) {
        derivedStateOf {
            if (liquidEnabled) {
                liquidTabBarEdgeColorStops(edgeGlowPhase?.value ?: 0f, darkSurface)
            } else {
                emptyArray()
            }
        }
    }
    val edgeGlowBackdrop = if (rootRenderBackdrop != null && visualStyle.liquidEnabled) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val navigationRenderBackdrop = if (rootRenderBackdrop != null && edgeGlowBackdrop != null) {
        rememberCombinedBackdrop(rootRenderBackdrop, edgeGlowBackdrop)
    } else {
        rootRenderBackdrop
    }
    val navigationBackdrop = navigationRenderBackdrop?.let(backdrop::withRenderBackdrop) ?: backdrop
    val tabsBackdrop = if (navigationRenderBackdrop != null && visualStyle.liquidEnabled) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val selectedTabRenderBackdrop = if (navigationRenderBackdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(navigationRenderBackdrop, tabsBackdrop)
    } else {
        navigationRenderBackdrop
    }
    val selectedTabBackdrop = selectedTabRenderBackdrop?.let(backdrop::withRenderBackdrop)
    val latestSelectedIndex = rememberUpdatedState(selectedIndex)
    val latestOnDestinationSelected = rememberUpdatedState(onDestinationSelected)
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragStartIndex by remember { mutableIntStateOf(selectedIndex) }
    var indicatorTargetIndex by remember { mutableIntStateOf(selectedIndex) }
    var dragVelocity by remember { mutableFloatStateOf(0f) }
    var dragMotionTick by remember { mutableIntStateOf(0) }
    var releasePending by remember { mutableStateOf(false) }
    var releasePosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var releaseVelocity by remember { mutableFloatStateOf(0f) }
    val indicatorMotion = remember { Animatable(selectedIndex.toFloat()) }
    var contentWidthPx by remember { mutableIntStateOf(0) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val gapPx = with(density) { FloatingTabBarMetrics.itemSpacing.toPx() }
    val slotWidthPx = if (contentWidthPx > 0) {
        ((contentWidthPx - gapPx * (destinations.lastIndex)) / destinations.size).coerceAtLeast(1f)
    } else {
        0f
    }
    val slotStridePx = slotWidthPx + gapPx
    val rawIndicatorPosition = if (isDragging || releasePending) dragPosition else indicatorMotion.value
    val indicatorVelocity = when {
        isDragging -> dragVelocity
        releasePending -> releaseVelocity
        else -> indicatorMotion.velocity
    }
    val indicatorMotionState = liquidTabIndicatorMotion(
        rawPosition = rawIndicatorPosition,
        velocity = if (visualStyle.liquidEnabled) indicatorVelocity else 0f,
        lastIndex = destinations.lastIndex,
        isLtr = isLtr,
        reduceMotion = visualStyle.reduceMotion,
    )
    val stretchAmount = indicatorMotionState.stretch
    val impactAmount = indicatorMotionState.impact
    val indicatorPosition = indicatorMotionState.position
    val indicatorWidth = FloatingTabBarMetrics.indicatorDiameter *
        (1f + stretchAmount * 0.28f - impactAmount * 0.14f)
    val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
    val indicatorHeight = FloatingTabBarMetrics.indicatorDiameter *
        (1f - stretchAmount * 0.04f + impactAmount * 0.18f)
    val indicatorScale by animateFloatAsState(
        targetValue = if (isDragging) 1.06f else 1f,
        animationSpec = if (visualStyle.reduceMotion) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium,
            )
        },
        label = "floatingTabIndicatorScale",
    )

    LaunchedEffect(selectedIndex) {
        if (!isDragging) indicatorTargetIndex = selectedIndex
    }

    LaunchedEffect(dragMotionTick, isDragging) {
        if (isDragging) {
            delay(90)
            dragVelocity = 0f
        }
    }

    LaunchedEffect(indicatorTargetIndex, isDragging, visualStyle.reduceMotion) {
        if (isDragging) {
            indicatorMotion.stop()
        } else {
            val initialVelocity = if (releasePending) releaseVelocity else indicatorMotion.velocity
            if (releasePending) {
                indicatorMotion.snapTo(releasePosition)
                releasePending = false
            }
            if (visualStyle.reduceMotion) {
                indicatorMotion.snapTo(indicatorTargetIndex.toFloat())
            } else {
                indicatorMotion.animateTo(
                    targetValue = indicatorTargetIndex.toFloat(),
                    animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                    initialVelocity = initialVelocity,
                )
            }
        }
    }

    fun selectDestination(index: Int) {
        val targetIndex = index.coerceIn(destinations.indices)
        releasePending = false
        isDragging = false
        indicatorTargetIndex = targetIndex
        latestOnDestinationSelected.value(destinations[targetIndex].route)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        val viewportNeedsScroll = maxWidth < FloatingTabBarMetrics.maxWidth
        val tabScrollState = rememberScrollState()
        val viewportModifier = if (viewportNeedsScroll) {
            Modifier.horizontalScroll(tabScrollState).fillMaxWidth()
        } else {
            Modifier.fillMaxWidth()
        }
        Box(
            modifier = viewportModifier
                .height(FloatingTabBarMetrics.reservedHeight),
            contentAlignment = if (viewportNeedsScroll) Alignment.CenterStart else Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(FloatingTabBarMetrics.maxWidth)
                    .height(FloatingTabBarMetrics.reservedHeight),
                contentAlignment = Alignment.Center,
        ) {
        if (visualStyle.liquidEnabled) {
            LiquidTabBarEdgeGlow(
                modifier = Modifier.matchParentSize().then(
                    edgeGlowBackdrop?.let { Modifier.layerBackdrop(it) } ?: Modifier,
                ),
                colorStops = edgeColorStops,
                blurSupported = visualStyle.effectiveTier == LiquidGlassTier.BLUR ||
                    visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = FloatingTabBarMetrics.outerHorizontalPadding,
                    vertical = FloatingTabBarMetrics.outerVerticalPadding,
                ),
            contentAlignment = Alignment.Center,
        ) {
        NamiNavigationSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingTabBarMetrics.surfaceHeight)
                .then(
                    if (visualStyle.liquidEnabled) {
                        Modifier.liquidTabBarEdgeRim(edgeColorStops)
                    } else {
                        Modifier
                    },
                ),
            shape = floatingTabBarShape,
            backdrop = navigationBackdrop,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = FloatingTabBarMetrics.contentHorizontalPadding)
                    .fillMaxSize()
                    .onSizeChanged { contentWidthPx = it.width },
            ) {
                if (navigationRenderBackdrop != null && tabsBackdrop != null) {
                    // The upstream catalog uses a separate exported layer for
                    // the tab contents. The selected lens then combines the
                    // page backdrop with this local tab layer, so the active
                    // capsule bends both the page text and the tab artwork.
                    Row(
                        modifier = Modifier
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = navigationRenderBackdrop,
                                shape = { floatingTabBarShape },
                                effects = {
                                    vibrancy()
                                    blur(8.dp.toPx())
                                    if (visualStyle.effectiveTier == LiquidGlassTier.BLUR_AND_LENS) {
                                        lens(
                                            refractionHeight = 24.dp.toPx(),
                                            refractionAmount = 24.dp.toPx(),
                                        )
                                    }
                                },
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        destinations.forEachIndexed { index, destination ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                    tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (index < destinations.lastIndex) {
                                Spacer(Modifier.width(FloatingTabBarMetrics.itemSpacing))
            }
        }
    }
}

                if (slotWidthPx > 0f) {
                    val indicatorHeightPx = with(density) { indicatorHeight.toPx() }
                    val indicatorShape = if (!visualStyle.liquidEnabled || stretchAmount < 0.02f) {
                        CircleShape
                    } else {
                        liquidDropletShape(stretchAmount, movingRight = indicatorMotionState.movingRight)
                    }
                    // The row is centered inside the full 64dp surface. Center the
                    // indicator against that same surface, not against the row's
                    // 48dp hit target; using 48dp puts the blue indicator 8dp too high.
                    val indicatorVerticalOffsetPx = (
                        with(density) { FloatingTabBarMetrics.surfaceHeight.toPx() } -
                            indicatorHeightPx
                    ) / 2f
                    val indicatorModifier = Modifier
                            .absoluteOffset {
                                androidx.compose.ui.unit.IntOffset(
                                    x = (
                                        (if (isLtr) indicatorPosition else destinations.lastIndex - indicatorPosition) *
                                            slotStridePx +
                                            (slotWidthPx - indicatorWidthPx) / 2f +
                                             with(density) { (4.dp * impactAmount).toPx() } *
                                             indicatorMotionState.impactSide
                                        ).roundToInt(),
                                    y = indicatorVerticalOffsetPx.roundToInt(),
                                )
                            }
                            .width(indicatorWidth)
                            .height(indicatorHeight)
                            .graphicsLayer {
                                scaleX = indicatorScale
                                scaleY = indicatorScale
                                rotationZ = (if (indicatorMotionState.movingRight) 1f else -1f) *
                                    4.5f * stretchAmount
                            }
                    if (visualStyle.liquidEnabled) {
                        NamiGlassOverlay(
                            backdrop = selectedTabBackdrop ?: backdrop,
                            // The outer clip preserves the droplet silhouette. The
                            // lens itself needs a CornerBasedShape to avoid a crash.
                            modifier = indicatorModifier.clip(indicatorShape),
                            shape = CircleShape,
                            // The selected item is a neutral piece of glass.
                            // Accent blue belongs to actions, not to the lens
                            // itself; a colored fill hides the refraction that
                            // should be visible at its edge.
                            tint = androidx.compose.material3.MaterialTheme.colorScheme
                                .surfaceBright.copy(
                                    alpha = if (visualStyle.isClearGlass) 0.48f else 0.56f,
                                ),
                            surfaceRole = GlassSurfaceRole.SELECTED_INDICATOR,
                        ) {}
                    } else {
                        Box(
                            modifier = indicatorModifier
                                .clip(indicatorShape)
                                .background(
                                    androidx.compose.material3.MaterialTheme.colorScheme
                                        .primaryContainer,
                                ),
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .semantics { selectableGroup() }
                        .then(if (viewportNeedsScroll) Modifier else Modifier.pointerInput(destinations.size, slotStridePx, isLtr) {
                            detectDragGestures(
                                onDragStart = {
                                    releasePending = false
                                    dragStartIndex = latestSelectedIndex.value.coerceIn(destinations.indices)
                                    dragPosition = indicatorMotion.value.coerceIn(0f, destinations.lastIndex.toFloat())
                                    dragVelocity = 0f
                                    dragMotionTick++
                                    isDragging = true
                                },
                                onDragCancel = {
                                    releasePosition = dragPosition
                                    releaseVelocity = 0f
                                    releasePending = true
                                    indicatorTargetIndex = latestSelectedIndex.value.coerceIn(destinations.indices)
                                    isDragging = false
                                },
                                onDragEnd = {
                                    val currentIndex = latestSelectedIndex.value.coerceIn(destinations.indices)
                                    releasePosition = dragPosition
                                    releaseVelocity = dragVelocity.coerceIn(-24f, 24f)
                                    releasePending = true
                                    if (currentIndex != dragStartIndex) {
                                        releaseVelocity = 0f
                                        indicatorTargetIndex = currentIndex
                                    } else {
                                        val targetIndex = (dragPosition + releaseVelocity * 0.10f)
                                            .roundToInt()
                                            .coerceIn(destinations.indices)
                                        indicatorTargetIndex = targetIndex
                                        latestOnDestinationSelected.value(destinations[targetIndex].route)
                                    }
                                    isDragging = false
                                },
                                onDrag = { change, dragAmount ->
                                    if (slotStridePx > 0f) {
                                        val direction = if (isLtr) 1f else -1f
                                        val indexDelta = dragAmount.x / slotStridePx * direction
                                        val elapsedMs = (change.uptimeMillis - change.previousUptimeMillis)
                                            .coerceAtLeast(1L)
                                        val instantVelocity = indexDelta * 1000f / elapsedMs
                                        dragVelocity = (dragVelocity * 0.3f + instantVelocity * 0.7f)
                                            .coerceIn(-24f, 24f)
                                        dragMotionTick++
                                        dragPosition = (dragPosition + indexDelta)
                                            .coerceIn(0f, destinations.lastIndex.toFloat())
                                    }
                                    change.consume()
                                },
                            )
                        }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val isVisuallySelected = if (isDragging) {
                            indicatorPosition.roundToInt() == index
                        } else {
                            indicatorTargetIndex == index
                        }
                        val isSemanticallySelected = selectedIndex == index
                        val bringIntoViewRequester = remember(index) { BringIntoViewRequester() }
                        LaunchedEffect(isSemanticallySelected, viewportNeedsScroll) {
                            if (isSemanticallySelected && viewportNeedsScroll) {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                        val label = stringResource(destination.labelRes)
                        val iconTint by animateColorAsState(
                            targetValue = if (isVisuallySelected) {
                                androidx.compose.material3.MaterialTheme.colorScheme.onSurface
                            } else {
                                androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            animationSpec = if (visualStyle.reduceMotion) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                )
                            },
                            label = "floatingTabIconTint",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (isVisuallySelected) 1.04f else 1f,
                            animationSpec = if (visualStyle.reduceMotion) {
                                snap()
                            } else {
                                spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                )
                            },
                            label = "floatingTabIconScale",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .tvFocusable(
                                    shape = RoundedCornerShape(24.dp),
                                    makeFocusable = true,
                                )
                                .clickable(
                                    role = Role.Tab,
                                    onClick = { selectDestination(index) },
                                )
                                .semantics {
                                    contentDescription = label
                                    role = Role.Tab
                                    selected = isSemanticallySelected
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer {
                                        scaleX = iconScale
                                        scaleY = iconScale
                                    },
                                imageVector = destination.icon,
                                contentDescription = null,
                                tint = iconTint,
                            )
                        }
                        if (index < destinations.lastIndex) {
                            Spacer(Modifier.width(FloatingTabBarMetrics.itemSpacing))
                        }
                    }
                }
            }
        }
        }
    }
}
}
}
