package io.github.hhwkart.nami.ui.compose.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.provider.Settings
import android.widget.Toast
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.ACCESS_LOCAL_NETWORK_PERMISSION
import io.github.hhwkart.nami.LocalNetworkPermissionPolicy
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.bg.BaseService
import io.github.hhwkart.nami.hasLocalNetworkPermission
import io.github.hhwkart.nami.ui.compose.ConnectionBus
import io.github.hhwkart.nami.ui.compose.theme.ClassicThemes
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import io.github.hhwkart.nami.ui.compose.common.NamiAutoFocusTextField
import io.github.hhwkart.nami.ui.compose.style.NamiSwitch
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import io.github.hhwkart.nami.ui.compose.style.LocalNamiVisualStyle
import kotlinx.coroutines.flow.collectLatest

private data class ChoiceDialogData(
    val title: String,
    val options: List<Pair<String, String>>,
    val selected: String,
    val onSelected: (String) -> Unit,
)

private data class TextDialogData(
    val title: String,
    val value: String,
    val multiline: Boolean = false,
    val keyboardType: KeyboardType = KeyboardType.Text,
    val onSave: (String) -> Unit,
    val validator: ((String) -> Boolean)? = null,
    val validationErrorRes: Int? = null,
)

private const val CLIPBOARD_EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE"

@Composable
fun SettingsScreen(
    onOpenPerApp: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenOnboarding: () -> Unit = {},
    onOpenWhatsNew: () -> Unit = {},
    onOpenFaq: () -> Unit,
    onOpenTools: () -> Unit = {},
    onOpenLog: () -> Unit = {},
    onOpenDashboard: () -> Unit = {},
    onReloadRequired: () -> Unit = {},
    onForceConfigReloadRequired: () -> Unit = {},
    onLocalNetworkPermissionCheck: () -> Unit = {},
    localNetworkPermissionGranted: Boolean = false,
    onRestartRequired: () -> Unit = {},
    onThemeChanged: () -> Unit = {},
    onAppearanceChanged: () -> Unit = {},
    onServiceModeChanged: () -> Unit = {},
    onClashApiChanged: (Boolean) -> Unit = {},
    onProxyAppsChanged: () -> Unit = onOpenPerApp,
    bottomBarPadding: Dp = 0.dp,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val serviceState by ConnectionBus.state.collectAsStateWithLifecycle()
    val localProxyAuth by ConnectionBus.localProxyAuth.collectAsStateWithLifecycle()
    var choiceDialog by remember { mutableStateOf<ChoiceDialogData?>(null) }
    var textDialog by remember { mutableStateOf<TextDialogData?>(null) }
    var showThemeModeDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showLanProxyCredentials by remember { mutableStateOf(false) }
    var showLanPermissionDenied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val lanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val permissionIsGranted = context.hasLocalNetworkPermission()
        onLocalNetworkPermissionCheck()
        if (granted && permissionIsGranted) {
            if (!state.allowAccess) viewModel.setAllowAccess(true)
        } else {
            showLanPermissionDenied = true
        }
    }

    LaunchedEffect(state.allowAccess, serviceState, localProxyAuth) {
        showLanProxyCredentials = false
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) showLanProxyCredentials = false
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                SettingsEffect.ReloadRequired -> onReloadRequired()
                SettingsEffect.ForceConfigReloadRequired -> onForceConfigReloadRequired()
                SettingsEffect.RestartRequired -> onRestartRequired()
                SettingsEffect.ThemeChanged -> onThemeChanged()
                SettingsEffect.AppearanceChanged -> onAppearanceChanged()
                SettingsEffect.ServiceModeChanged -> onServiceModeChanged()
                is SettingsEffect.ClashApiChanged -> onClashApiChanged(effect.enabled)
                SettingsEffect.ProxyAppsChanged -> onProxyAppsChanged()
            }
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + 4.dp,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 24.dp + bottomBarPadding,
            ),
        ) {
        item { SectionTitle(stringResource(R.string.settings_section_appearance)) }
        item {
            val currentModeLabel = when {
                state.themeMode == Key.THEME_MODE_DYNAMIC && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S ->
                    stringResource(R.string.settings_theme_mode_dynamic)
                state.themeMode == Key.THEME_MODE_CLASSIC ->
                    stringResource(R.string.settings_theme_mode_classic)
                state.themeMode == Key.THEME_MODE_LIQUID_GLASS ->
                    stringResource(R.string.settings_theme_mode_liquid_glass)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                    stringResource(R.string.settings_theme_mode_dynamic)
                else -> stringResource(R.string.settings_theme_mode_classic)
            }
            SettingChoiceItem(
                stringResource(R.string.settings_theme_mode),
                currentModeLabel,
                onClick = {
                    showThemeModeDialog = true
                },
            )
        }
        if (state.themeMode == Key.THEME_MODE_CLASSIC) {
            item {
                val classicName = ClassicThemes.getThemeName(state.appTheme)
                SettingItem(
                    stringResource(R.string.settings_classic_palette),
                    classicName,
                ) { showThemeDialog = true }
            }
        }
        item {
            val nightThemeLabel = getArrayLabel(context, R.array.night_mode, R.array.int_array_3, state.nightTheme)
            SettingChoiceItem(
                stringResource(R.string.night_mode), nightThemeLabel,
                onClick = { choiceDialog = arrayChoice(context, R.string.night_mode, R.array.night_mode, R.array.int_array_3, state.nightTheme.toString(), viewModel::setNightTheme) },
            )
        }
        item {
            SettingSwitchItem(stringResource(R.string.auto_connect), stringResource(R.string.auto_connect_summary), state.autoConnect, true, viewModel::setAutoConnect)
        }
        item {
            val values = context.resources.getStringArray(R.array.service_mode_values)
            val labels = context.resources.getStringArray(R.array.service_modes)
            val serviceModeLabel = getArrayLabel(context, R.array.service_modes, R.array.service_mode_values, state.serviceMode)
            SettingChoiceItem(stringResource(R.string.service_mode), serviceModeLabel, onClick = {
                choiceDialog = ChoiceDialogData(context.getString(R.string.service_mode), labels.zip(values), state.serviceMode) { viewModel.setServiceMode(it) }
            })
        }
        item {
            val tunLabel = getArrayLabel(context, R.array.tun_implementation, R.array.int_array_3, state.tunImplementation)
            SettingChoiceItem(stringResource(R.string.tun_implementation), tunLabel, onClick = {
                choiceDialog = arrayChoice(context, R.string.tun_implementation, R.array.tun_implementation, R.array.int_array_3, state.tunImplementation.toString(), { viewModel.setTunImplementation(it) })
            })
        }
        item {
            TextSettingItem(stringResource(R.string.mtu), state.mtu.toString()) {
                textDialog = numberDialog(
                    title = context.getString(R.string.mtu),
                    value = state.mtu.toString(),
                    validationErrorRes = R.string.settings_mtu_invalid,
                    validator = { it.toIntOrNull()?.let { value -> value in 576..65535 } == true },
                    onSave = { it.toIntOrNull()?.let(viewModel::setMtu) },
                )
            }
        }
        item {
            val speedIntervalLabel = getArrayLabel(context, R.array.notification_entry, R.array.notification_value, state.speedInterval)
            SettingChoiceItem(stringResource(R.string.speed_interval), speedIntervalLabel, onClick = {
                choiceDialog = arrayChoice(context, R.string.speed_interval, R.array.notification_entry, R.array.notification_value, state.speedInterval.toString(), viewModel::setSpeedInterval)
            })
        }
        item { SettingSwitchItem(stringResource(R.string.profile_traffic_statistics), "", state.profileTrafficStatistics, true, viewModel::setProfileTrafficStatistics) }
        item { SettingSwitchItem(stringResource(R.string.show_direct_speed), stringResource(R.string.show_direct_speed_sum), state.showDirectSpeed, true, viewModel::setShowDirectSpeed) }
        item { SettingSwitchItem(stringResource(R.string.show_group_in_notification), "", state.showGroupInNotification, true, viewModel::setShowGroupInNotification) }
        item { SettingSwitchItem(stringResource(R.string.always_show_address), stringResource(R.string.always_show_address_sum), state.alwaysShowAddress, true, viewModel::setAlwaysShowAddress) }
        item { SettingSwitchItem(stringResource(R.string.metered), stringResource(R.string.metered_summary), state.meteredNetwork, Build.VERSION.SDK_INT >= 28, viewModel::setMeteredNetwork) }
        item { SettingSwitchItem(stringResource(R.string.acquire_wake_lock), stringResource(R.string.acquire_wake_lock_summary), state.acquireWakeLock, true, viewModel::setAcquireWakeLock) }
        item {
            val logLevelLabel = getArrayLabel(context, R.array.log_level, R.array.int_array_5, state.logLevel)
            SettingChoiceItem(stringResource(R.string.log_level), logLevelLabel, onClick = {
                choiceDialog = arrayChoice(context, R.string.log_level, R.array.log_level, R.array.int_array_5, state.logLevel.toString(), viewModel::setLogLevel)
            })
        }
        item { TextSettingItem(stringResource(R.string.custom_config_json), state.globalCustomConfig) { textDialog = TextDialogData(context.getString(R.string.custom_config_json), state.globalCustomConfig, true, onSave = viewModel::setGlobalCustomConfig) } }

        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.cag_route)) }
        item {
            if (LocalNamiVisualStyle.current.liquidEnabled) {
                LiquidSettingSwitchRow(
                    title = stringResource(R.string.proxied_apps),
                    summary = stringResource(R.string.proxied_apps_summary),
                    checked = state.proxyApps,
                    enabled = true,
                    onChecked = viewModel::setProxyApps,
                    rowAction = Modifier.clickable(onClick = onOpenPerApp),
                )
            } else {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.proxied_apps)) },
                    supportingContent = { Text(stringResource(R.string.proxied_apps_summary)) },
                    trailingContent = { NamiSwitch(checked = state.proxyApps, onCheckedChange = viewModel::setProxyApps) },
                    modifier = settingsRowModifier().clickable(onClick = onOpenPerApp),
                    colors = settingsRowColors(),
                )
            }
        }
        item { SettingSwitchItem(stringResource(R.string.route_opt_bypass_lan), "", state.bypassLan, true, viewModel::setBypassLan) }
        item { SettingSwitchItem(stringResource(R.string.bypass_lan_in_core), "", state.bypassLanInCore, true, viewModel::setBypassLanInCore) }
        item {
            val trafficSniffingLabel = getArrayLabel(context, R.array.traffic_sniffing_values, R.array.int_array_3, state.trafficSniffing)
            SettingChoiceItem(stringResource(R.string.traffic_sniffing), trafficSniffingLabel, onClick = {
                choiceDialog = arrayChoice(context, R.string.traffic_sniffing, R.array.traffic_sniffing_values, R.array.int_array_3, state.trafficSniffing.toString(), viewModel::setTrafficSniffing)
            })
        }
        item { SettingSwitchItem(stringResource(R.string.resolve_destination), stringResource(R.string.resolve_destination_summary), state.resolveDestination, true, viewModel::setResolveDestination) }
        item {
            val ipv6Label = getArrayLabel(context, R.array.ipv6_mode, R.array.int_array_4, state.ipv6Mode)
            SettingChoiceItem(stringResource(R.string.ipv6), ipv6Label, onClick = {
                choiceDialog = arrayChoice(context, R.string.ipv6, R.array.ipv6_mode, R.array.int_array_4, state.ipv6Mode.toString(), viewModel::setIpv6Mode)
            })
        }
        item {
            val rulesProviderLabel = getArrayLabel(context, R.array.rules_dat_provider, R.array.int_array_4, state.rulesProvider)
            SettingChoiceItem(stringResource(R.string.route_rules_provider), rulesProviderLabel, onClick = {
                choiceDialog = arrayChoice(context, R.string.route_rules_provider, R.array.rules_dat_provider, R.array.int_array_4, state.rulesProvider.toString(), viewModel::setRulesProvider)
            })
        }

        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.cag_dns)) }
        item { TextSettingItem(stringResource(R.string.remote_dns), state.remoteDns) { textDialog = TextDialogData(context.getString(R.string.remote_dns), state.remoteDns, keyboardType = KeyboardType.Uri, onSave = viewModel::setRemoteDns) } }
        item { DomainStrategyItem(stringResource(R.string.domain_strategy_for_remote), state.remoteDomainStrategy, context, viewModel::setRemoteDomainStrategy) { choiceDialog = it } }
        item { TextSettingItem(stringResource(R.string.direct_dns), state.directDns) { textDialog = TextDialogData(context.getString(R.string.direct_dns), state.directDns, keyboardType = KeyboardType.Uri, onSave = viewModel::setDirectDns) } }
        item { DomainStrategyItem(stringResource(R.string.domain_strategy_for_direct), state.directDomainStrategy, context, viewModel::setDirectDomainStrategy) { choiceDialog = it } }
        item { DomainStrategyItem(stringResource(R.string.domain_strategy_for_server), state.serverDomainStrategy, context, viewModel::setServerDomainStrategy) { choiceDialog = it } }
        item { SettingSwitchItem(stringResource(R.string.enable_dns_routing), stringResource(R.string.dns_routing_message), state.enableDnsRouting, true, viewModel::setEnableDnsRouting) }
        item { SettingSwitchItem(stringResource(R.string.enable_fakedns), stringResource(R.string.fakedns_message), state.enableFakeDns, true, viewModel::setEnableFakeDns) }

        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.inbound_settings)) }
        item {
            TextSettingItem(stringResource(R.string.port_proxy), state.mixedPort.toString()) {
                textDialog = numberDialog(
                    title = context.getString(R.string.port_proxy),
                    value = state.mixedPort.toString(),
                    validationErrorRes = R.string.settings_proxy_port_invalid,
                    validator = { it.toIntOrNull()?.let { port -> port in 1..65535 } == true },
                    onSave = { it.toIntOrNull()?.let(viewModel::setMixedPort) },
                )
            }
        }
        item { SettingSwitchItem(stringResource(R.string.append_http_proxy), stringResource(R.string.append_http_proxy_sum), state.appendHttpProxy, true, viewModel::setAppendHttpProxy) }
        item { SettingSwitchItem(stringResource(R.string.socks5_auth), stringResource(R.string.socks5_auth_sum), state.socks5Auth, true, viewModel::setSocks5Auth) }
        item {
            SettingSwitchItem(
                stringResource(R.string.allow_access),
                stringResource(R.string.allow_access_sum),
                state.allowAccess,
                true,
            ) { enabled ->
                if (!enabled) {
                    viewModel.setAllowAccess(false)
                } else if (LocalNetworkPermissionPolicy.isPermissionRequired(Build.VERSION.SDK_INT) &&
                    !context.hasLocalNetworkPermission()
                ) {
                    lanPermissionLauncher.launch(ACCESS_LOCAL_NETWORK_PERMISSION)
                } else {
                    viewModel.setAllowAccess(true)
                }
            }
        }
        if (state.allowAccess &&
            LocalNetworkPermissionPolicy.isPermissionRequired(Build.VERSION.SDK_INT) &&
            !localNetworkPermissionGranted
        ) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.lan_permission_required_summary),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = {
                            lanPermissionLauncher.launch(ACCESS_LOCAL_NETWORK_PERMISSION)
                        },
                    ) {
                        Text(stringResource(R.string.lan_permission_grant))
                    }
                }
            }
        }
        if (state.allowAccess) {
            item {
                LanProxyCredentialsCard(
                    credentials = localProxyAuth.takeIf { serviceState == BaseService.State.Connected },
                    serviceConnected = serviceState == BaseService.State.Connected,
                    visible = showLanProxyCredentials,
                    onVisibilityChange = { showLanProxyCredentials = it },
                    onCopyUsername = { username ->
                        copySensitiveCredential(
                            context = context,
                            label = context.getString(R.string.lan_proxy_username),
                            value = username,
                        )
                    },
                    onCopyPassword = { password ->
                        copySensitiveCredential(
                            context = context,
                            label = context.getString(R.string.lan_proxy_password),
                            value = password,
                        )
                    },
                )
            }
        }

        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.cag_misc)) }
        item { TextSettingItem(stringResource(R.string.connection_test_url), state.connectionTestUrl) { textDialog = TextDialogData(context.getString(R.string.connection_test_url), state.connectionTestUrl, keyboardType = KeyboardType.Uri, onSave = viewModel::setConnectionTestUrl) } }
        item { SettingSwitchItem(stringResource(R.string.enable_clash_api), stringResource(R.string.enable_clash_api_summary), state.enableClashApi, true, viewModel::setEnableClashApi) }
        item { SettingSwitchItem(stringResource(R.string.network_change_reset_connections), "", state.networkChangeResetConnections, true, viewModel::setNetworkChangeResetConnections) }
        item { SettingSwitchItem(stringResource(R.string.wake_reset_connections), "", state.wakeResetConnections, true, viewModel::setWakeResetConnections) }
        item { SettingSwitchItem(stringResource(R.string.global_allow_insecure), "", state.globalAllowInsecure, true, viewModel::setGlobalAllowInsecure) }
        item { SettingSwitchItem(stringResource(R.string.allow_insecure_on_request_sum), "", state.allowInsecureOnRequest, true, viewModel::setAllowInsecureOnRequest) }
        item {
            val values = context.resources.getStringArray(R.array.app_tls_version)
            SettingChoiceItem(stringResource(R.string.app_tls_version), state.appTlsVersion, onClick = {
                choiceDialog = ChoiceDialogData(context.getString(R.string.app_tls_version), values.map { it to it }, state.appTlsVersion) { viewModel.setAppTlsVersion(it) }
            })
        }
        item { SettingSwitchItem(stringResource(R.string.show_bottom_bar), "", state.showBottomBar, true, viewModel::setShowBottomBar) }
        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.menu_tools)) }
        item { SettingItem(stringResource(R.string.menu_tools), "") { onOpenTools() } }
        item { SettingItem(stringResource(R.string.menu_log), "") { onOpenLog() } }
        if (state.enableClashApi) {
            item { SettingItem(stringResource(R.string.menu_dashboard), "") { onOpenDashboard() } }
        }

        item { HorizontalDivider() }
        item { SectionTitle(stringResource(R.string.settings_section_about)) }
        item { SettingItem(stringResource(R.string.perapp_title), "") { onOpenPerApp() } }
        item { SettingItem(stringResource(R.string.menu_about), "") { onOpenAbout() } }
        item { SettingItem("Onboarding", "Review the setup introduction") { onOpenOnboarding() } }
        item { SettingItem("What’s new", "Review the latest changes") { onOpenWhatsNew() } }
        item { SettingItem(stringResource(R.string.document), "") { onOpenFaq() } }
    }
    }

    choiceDialog?.let { dialog ->
        ChoiceDialog(dialog) { choiceDialog = null }
    }
    textDialog?.let { dialog ->
        TextValueDialog(dialog) { textDialog = null }
    }
    if (showThemeModeDialog) {
        ThemeModeDialog(
            initialMode = state.themeMode,
            initialAmoledDark = state.amoledDark,
            options = themeModeOptionsForDialog(context),
            onApply = { mode, amoled ->
                viewModel.setThemeAppearance(mode, amoled)
                showThemeModeDialog = false
            },
            onDismiss = { showThemeModeDialog = false },
        )
    }
    if (showThemeDialog) {
        ClassicThemeDialog(
            selected = state.appTheme,
            onSelected = { viewModel.setAppTheme(it); showThemeDialog = false },
            onDismiss = { showThemeDialog = false },
        )
    }

    if (showLanPermissionDenied) {
        NamiAlertDialog(
            onDismissRequest = { showLanPermissionDenied = false },
            title = { Text(stringResource(R.string.lan_permission_denied_title)) },
            text = { Text(stringResource(R.string.lan_permission_denied_message)) },
            confirmButton = {
                TextButton(onClick = { showLanPermissionDenied = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showLanPermissionDenied = false
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            },
                        )
                    },
                ) {
                    Text(stringResource(R.string.lan_permission_open_settings))
                }
            },
        )
    }
}

@Composable
private fun LanProxyCredentialsCard(
    credentials: Pair<String, String>?,
    serviceConnected: Boolean,
    visible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    onCopyUsername: (String) -> Unit,
    onCopyPassword: (String) -> Unit,
) {
    NamiCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.lan_proxy_credentials_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.lan_proxy_credentials_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )

            when {
                !serviceConnected -> {
                    Text(
                        text = stringResource(R.string.lan_proxy_credentials_connect_first),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                credentials == null -> {
                    Text(
                        text = stringResource(R.string.lan_proxy_credentials_unavailable),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                visible -> {
                    LanProxyCredentialRow(
                        label = stringResource(R.string.lan_proxy_username),
                        value = credentials.first,
                        copyLabel = stringResource(R.string.lan_proxy_copy_username),
                        onCopy = { onCopyUsername(credentials.first) },
                    )
                    LanProxyCredentialRow(
                        label = stringResource(R.string.lan_proxy_password),
                        value = credentials.second,
                        copyLabel = stringResource(R.string.lan_proxy_copy_password),
                        onCopy = { onCopyPassword(credentials.second) },
                    )
                    TextButton(onClick = { onVisibilityChange(false) }) {
                        Text(stringResource(R.string.lan_proxy_credentials_hide))
                    }
                }
                else -> {
                    TextButton(onClick = { onVisibilityChange(true) }) {
                        Text(stringResource(R.string.lan_proxy_credentials_show))
                    }
                }
            }
        }
    }
}

@Composable
private fun LanProxyCredentialRow(
    label: String,
    value: String,
    copyLabel: String,
    onCopy: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        TextButton(onClick = onCopy) { Text(copyLabel) }
    }
}

private fun copySensitiveCredential(context: Context, label: String, value: String) {
    val clipboardManager = context.getSystemService(ClipboardManager::class.java) ?: return
    val clipData = ClipData.newPlainText(label, value)
    clipData.description.extras = PersistableBundle().apply {
        putBoolean(CLIPBOARD_EXTRA_IS_SENSITIVE, true)
    }
    clipboardManager.setPrimaryClip(clipData)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.lan_proxy_credential_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun getArrayLabel(
    context: android.content.Context,
    labelsRes: Int,
    valuesRes: Int,
    value: String,
): String {
    val labels = context.resources.getStringArray(labelsRes)
    val values = context.resources.getStringArray(valuesRes)
    val index = values.indexOf(value)
    return if (index in labels.indices) labels[index] else value
}

private fun getArrayLabel(
    context: android.content.Context,
    labelsRes: Int,
    valuesRes: Int,
    value: Int,
): String = getArrayLabel(context, labelsRes, valuesRes, value.toString())

private fun arrayChoice(
    context: android.content.Context,
    titleRes: Int,
    labelsRes: Int,
    valuesRes: Int,
    selected: String,
    onSelected: (Int) -> Unit,
): ChoiceDialogData {
    val labels = context.resources.getStringArray(labelsRes)
    val values = context.resources.getStringArray(valuesRes)
    return ChoiceDialogData(context.getString(titleRes), labels.zip(values), selected) { onSelected(it.toInt()) }
}

@Composable
private fun DomainStrategyItem(
    title: String,
    selected: String,
    context: android.content.Context,
    onSelected: (String) -> Unit,
    show: (ChoiceDialogData) -> Unit,
) {
    SettingChoiceItem(title, selected) {
        val values = context.resources.getStringArray(R.array.dns_network_select)
        show(ChoiceDialogData(title, values.map { it to it }, selected, onSelected))
    }
}

private fun numberDialog(
    title: String,
    value: String,
    validationErrorRes: Int,
    validator: (String) -> Boolean,
    onSave: (String) -> Unit,
) = TextDialogData(
    title = title,
    value = value,
    keyboardType = KeyboardType.Number,
    onSave = onSave,
    validator = validator,
    validationErrorRes = validationErrorRes,
)

@Composable
private fun ChoiceDialog(data: ChoiceDialogData, onDismiss: () -> Unit) {
    var draft by remember(data) { mutableStateOf(data.selected) }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title) },
        text = {
            LazyColumn {
                items(data.options) { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draft = value }
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft == value, onClick = { draft = value })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { data.onSelected(draft); onDismiss() }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun TextValueDialog(data: TextDialogData, onDismiss: () -> Unit) {
    var value by remember(data) { mutableStateOf(data.value) }
    var validationError by remember(data) { mutableStateOf(false) }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(data.title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NamiAutoFocusTextField(
                    value = value,
                    onValueChange = {
                        value = it
                        validationError = false
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(data.title) },
                    singleLine = !data.multiline,
                    minLines = if (data.multiline) 4 else 1,
                    keyboardType = data.keyboardType,
                )
                if (validationError && data.validationErrorRes != null) {
                    Text(
                        text = stringResource(data.validationErrorRes),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (data.validator?.invoke(value) == false) {
                        validationError = true
                    } else {
                        data.onSave(value)
                        onDismiss()
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } },
    )
}

@Composable
private fun ClassicThemeDialog(selected: Int, onSelected: (Int) -> Unit, onDismiss: () -> Unit) {
    val themes = ClassicThemes.ALL
    var draft by remember(selected) { mutableStateOf(selected) }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_classic_palette)) },
        text = {
            LazyColumn {
                items(themes) { theme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draft = theme.id }
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .background(theme.primary, androidx.compose.foundation.shape.CircleShape),
                        )
                        Spacer(Modifier.size(8.dp))
                        RadioButton(selected = draft == theme.id, onClick = { draft = theme.id })
                        Spacer(Modifier.size(8.dp))
                        Text(theme.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(draft); onDismiss() }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

private fun themeModeOptionsForDialog(context: android.content.Context): List<Pair<String, String>> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(context.getString(R.string.settings_theme_mode_dynamic) to Key.THEME_MODE_DYNAMIC.toString())
    }
    add(context.getString(R.string.settings_theme_mode_classic) to Key.THEME_MODE_CLASSIC.toString())
    add(context.getString(R.string.settings_theme_mode_liquid_glass) to Key.THEME_MODE_LIQUID_GLASS.toString())
}

@Composable
private fun ThemeModeDialog(
    initialMode: Int,
    initialAmoledDark: Boolean,
    options: List<Pair<String, String>>,
    onApply: (mode: Int, amoledDark: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableModeValues = options.map { it.second }
    var draftMode by remember(initialMode, options) {
        mutableStateOf(
            initialMode.toString().takeIf { it in availableModeValues }
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Key.THEME_MODE_DYNAMIC.toString()
                } else {
                    Key.THEME_MODE_CLASSIC.toString()
                },
        )
    }
    var draftAmoled by remember(initialAmoledDark) { mutableStateOf(initialAmoledDark) }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_mode)) },
        text = {
            Column {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { draftMode = value }
                            .defaultMinSize(minHeight = 48.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = draftMode == value,
                            onClick = { draftMode = value },
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { draftAmoled = !draftAmoled }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = draftAmoled,
                        onCheckedChange = { draftAmoled = it },
                    )
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(stringResource(R.string.settings_amoled_dark))
                        Text(
                            stringResource(R.string.settings_theme_mode_amoled_summary),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        draftMode.toIntOrNull() ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Key.THEME_MODE_DYNAMIC
                        } else {
                            Key.THEME_MODE_CLASSIC
                        },
                        draftAmoled,
                    )
                },
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SettingChoiceItem(title: String, summary: String, onClick: () -> Unit) {
    SettingItem(title, summary, onClick)
}

@Composable
private fun TextSettingItem(title: String, summary: String, onClick: () -> Unit) {
    SettingItem(title, summary, onClick)
}

@Composable
fun SettingItem(title: String, summary: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary.isNotBlank()) ({ Text(summary) }) else null,
        modifier = settingsRowModifier().clickable(onClick = onClick),
        colors = settingsRowColors(),
    )
}

@Composable
fun SettingSwitchItem(title: String, summary: String, checked: Boolean, enabled: Boolean, onChecked: (Boolean) -> Unit) {
    if (LocalNamiVisualStyle.current.liquidEnabled) {
        LiquidSettingSwitchRow(
            title = title,
            summary = summary,
            checked = checked,
            enabled = enabled,
            onChecked = onChecked,
            switchSemanticsCoveredByRow = true,
            rowAction = Modifier.toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onChecked,
                ),
        )
        return
    }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (summary.isNotBlank()) ({ Text(summary) }) else null,
        // Keep the switch interactive so a horizontal drag can follow the
        // glass thumb and snap to either state. The outer row remains a
        // convenient full-width toggle target for keyboard and touch users.
        trailingContent = {
            NamiSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onChecked,
                modifier = Modifier.clearAndSetSemantics { },
            )
        },
        modifier = settingsRowModifier()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChecked,
            ),
        colors = settingsRowColors(),
    )
}

@Composable
private fun LiquidSettingSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    enabled: Boolean,
    onChecked: (Boolean) -> Unit,
    switchSemanticsCoveredByRow: Boolean = false,
    rowAction: Modifier,
) {
    Row(
        modifier = settingsRowModifier()
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f))
            .then(rowAction)
            .defaultMinSize(minHeight = 64.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary.isNotBlank()) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        NamiSwitch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onChecked,
            modifier = if (switchSemanticsCoveredByRow) {
                Modifier.clearAndSetSemantics { }
            } else {
                Modifier
            },
        )
    }
}

private val liquidSettingsRowShape = RoundedCornerShape(18.dp)

@Composable
private fun settingsRowModifier(): Modifier {
    val visualStyle = LocalNamiVisualStyle.current
    return if (visualStyle.liquidEnabled) {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(liquidSettingsRowShape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f),
                shape = liquidSettingsRowShape,
            )
    } else {
        Modifier.fillMaxWidth()
    }
}

@Composable
private fun settingsRowColors() = if (LocalNamiVisualStyle.current.liquidEnabled) {
    ListItemDefaults.colors(
        containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
    )
} else {
    ListItemDefaults.colors()
}
