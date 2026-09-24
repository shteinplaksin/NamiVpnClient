package io.github.hhwkart.nami.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
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
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupSource
import io.github.hhwkart.nami.ui.compose.startup.WhatsNewScreen
import io.github.hhwkart.nami.ui.compose.tools.AssetsScreen
import io.github.hhwkart.nami.ui.compose.tools.StunTestScreen
import io.github.hhwkart.nami.ui.compose.utility.AboutScreen
import io.github.hhwkart.nami.ui.compose.utility.DashboardScreen
import io.github.hhwkart.nami.ui.compose.utility.LogScreen
import io.github.hhwkart.nami.ui.compose.utility.ToolsScreen
import kotlinx.coroutines.flow.collect
import com.kyant.backdrop.backdrops.layerBackdrop
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

private val floatingTabBarShape = RoundedCornerShape(percent = 50)

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

    fun navigateAfterQuickSetup(destination: Destination) {
        // Quick Setup is a transient flow. Returning to Home must not restore
        // Onboarding/QuickSetup state; returning to Profiles may restore the
        // user's saved Profiles list state.
        val preserveTargetState = destination != Destination.Home
        navController.navigate(destination) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = preserveTargetState
            }
            launchSingleTop = true
            restoreState = preserveTargetState
        }
    }

    LaunchedEffect(Unit) {
        NavigationBus.destination.collect { destination ->
            if (mainDestinations.any { it.route == destination }) {
                navigateTopLevel(destination)
            } else {
                navController.navigate(destination) { launchSingleTop = true }
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
            backgroundModifier = if (showFloatingTabBar) {
                Modifier
                    .navigationBarsPadding()
                    .padding(bottom = FloatingTabBarMetrics.reservedHeight)
            } else {
                Modifier
            },
            enabled = LocalNamiVisualStyle.current.liquidEnabled,
            rendererTier = LocalNamiVisualStyle.current.effectiveTier,
        ) { backdrop ->
            Box(modifier = Modifier.fillMaxSize()) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color.Transparent,
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    bottomBar = {
                        if (showFloatingTabBar) {
                            // Keep Scaffold's measured content inset while the visible bar is
                            // anchored to the shell root below. This avoids parent navigation
                            // layouts positioning the floating surface at the top of the content.
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .height(FloatingTabBarMetrics.reservedHeight),
                            )
                        }
                    },
                ) { _ ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .namiBackdropSource(backdrop),
                    ) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.Home,
                ) {
                composable<Destination.Home> {
                    HomeScreen()
                }
                composable<Destination.Profiles> {
                    ProfilesScreen(
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
                        onEditGroup = { group ->
                            navController.navigate(Destination.GroupEditor(group.id))
                        },
                    )
                }
                composable<Destination.Routing> {
                    RoutingScreen(
                        onAddRule = { navController.navigate(Destination.RouteEditor(0L)) },
                        onEditRule = { rule -> navController.navigate(Destination.RouteEditor(rule.id)) },
                        onAdvanced = { navController.navigate(Destination.RouteAssets) },
                        onReloadRequired = activity::confirmServiceReload,
                    )
                }
                composable<Destination.Settings> {
                    SettingsScreen(
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
                        onOpenOnboarding = activity::openOnboardingFromSettings,
                        onOpenWhatsNew = activity::openWhatsNewFromSettings,
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
    val tabsBackdrop = if (rootRenderBackdrop != null && visualStyle.liquidEnabled) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val combinedTabBackdrop = if (rootRenderBackdrop != null && tabsBackdrop != null) {
        rememberCombinedBackdrop(rootRenderBackdrop, tabsBackdrop)
    } else {
        null
    }
    val selectedTabBackdrop = combinedTabBackdrop?.let(backdrop::withRenderBackdrop)
    val latestSelectedIndex = rememberUpdatedState(selectedIndex)
    val latestOnDestinationSelected = rememberUpdatedState(onDestinationSelected)
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }
    var dragStartIndex by remember { mutableIntStateOf(selectedIndex) }
    var indicatorTargetIndex by remember { mutableIntStateOf(selectedIndex) }
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
    val indicatorWidth = FloatingTabBarMetrics.indicatorDiameter
    val indicatorWidthPx = with(density) { indicatorWidth.toPx() }
    val indicatorHeight = FloatingTabBarMetrics.indicatorDiameter
    val indicatorPosition by animateFloatAsState(
        targetValue = if (isDragging) dragPosition else indicatorTargetIndex.toFloat(),
        animationSpec = if (visualStyle.reduceMotion || isDragging) {
            snap()
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            )
        },
        label = "floatingTabIndicatorPosition",
    )
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
        if (!isDragging) {
            indicatorTargetIndex = selectedIndex
            dragPosition = selectedIndex.toFloat()
        }
    }

    fun selectDestination(index: Int) {
        val targetIndex = index.coerceIn(destinations.indices)
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
                    .padding(
                        horizontal = FloatingTabBarMetrics.outerHorizontalPadding,
                        vertical = FloatingTabBarMetrics.outerVerticalPadding,
                    ),
                contentAlignment = Alignment.Center,
            ) {
        NamiNavigationSurface(
            modifier = Modifier
                .fillMaxWidth()
                .height(FloatingTabBarMetrics.surfaceHeight),
            shape = floatingTabBarShape,
            backdrop = backdrop,
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = FloatingTabBarMetrics.contentHorizontalPadding)
                    .fillMaxSize()
                    .onSizeChanged { contentWidthPx = it.width },
            ) {
                if (rootRenderBackdrop != null && tabsBackdrop != null) {
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
                                backdrop = rootRenderBackdrop,
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
                                            (slotWidthPx - indicatorWidthPx) / 2f
                                        ).roundToInt(),
                                    y = indicatorVerticalOffsetPx.roundToInt(),
                                )
                            }
                            .width(indicatorWidth)
                            .height(indicatorHeight)
                            .graphicsLayer {
                                scaleX = indicatorScale
                                scaleY = indicatorScale
                            }
                    if (visualStyle.liquidEnabled) {
                        NamiGlassOverlay(
                            backdrop = selectedTabBackdrop ?: backdrop,
                            modifier = indicatorModifier,
                            shape = androidx.compose.foundation.shape.CircleShape,
                            // The selected item is a neutral piece of glass.
                            // Accent blue belongs to actions, not to the lens
                            // itself; a colored fill hides the refraction that
                            // should be visible at its edge.
                            tint = androidx.compose.material3.MaterialTheme.colorScheme
                                .surfaceBright.copy(
                                    alpha = if (visualStyle.isClearGlass) 0.16f else 0.22f,
                                ),
                            surfaceRole = GlassSurfaceRole.SELECTED_INDICATOR,
                        ) {}
                    } else {
                        Box(
                            modifier = indicatorModifier
                                .clip(androidx.compose.foundation.shape.CircleShape)
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
                                    isDragging = true
                                    dragStartIndex = latestSelectedIndex.value
                                        .coerceIn(destinations.indices)
                                    // Continue from the selected tab. Using
                                    // the raw pointer coordinate here makes a
                                    // drag jump to the finger and feels broken
                                    // when it starts on an icon.
                                    dragPosition = dragStartIndex.toFloat()
                                },
                                onDragCancel = {
                                    isDragging = false
                                    indicatorTargetIndex = latestSelectedIndex.value
                                        .coerceIn(destinations.indices)
                                },
                                onDragEnd = {
                                    val currentIndex = latestSelectedIndex.value
                                        .coerceIn(destinations.indices)
                                    isDragging = false
                                    if (currentIndex != dragStartIndex) {
                                        // A different navigation event won while the finger was down.
                                        // Keep that committed destination instead of replaying stale drag state.
                                        indicatorTargetIndex = currentIndex
                                        dragPosition = currentIndex.toFloat()
                                    } else {
                                        val targetIndex = dragPosition
                                            .roundToInt()
                                            .coerceIn(destinations.indices)
                                        indicatorTargetIndex = targetIndex
                                        latestOnDestinationSelected.value(destinations[targetIndex].route)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (slotStridePx > 0f) {
                                        val direction = if (isLtr) 1f else -1f
                                        dragPosition = (
                                            dragPosition + dragAmount.x / slotStridePx * direction
                                        ).coerceIn(0f, destinations.lastIndex.toFloat())
                                    }
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
