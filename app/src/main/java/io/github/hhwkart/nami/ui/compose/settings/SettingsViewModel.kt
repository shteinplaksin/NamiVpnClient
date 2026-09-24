package io.github.hhwkart.nami.ui.compose.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.database.DataStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

private const val DOMAIN_STRATEGY_REMOTE = "domain_strategy_for_remote"
private const val DOMAIN_STRATEGY_DIRECT = "domain_strategy_for_direct"
private const val DOMAIN_STRATEGY_SERVER = "domain_strategy_for_server"

data class SettingsUiState(
    val themeMode: Int = DataStore.themeMode,
    val dynamicColors: Boolean = DataStore.composeDynamicColors,
    val amoledDark: Boolean = DataStore.amoledDark,
    val interfaceStyle: Int = DataStore.interfaceStyle,
    val liquidGlassQuality: Int = DataStore.liquidGlassQuality,
    val autoConnect: Boolean = DataStore.persistAcrossReboot,
    val appTheme: Int = DataStore.appTheme,
    val nightTheme: Int = DataStore.nightTheme,
    val serviceMode: String = DataStore.serviceMode,
    val tunImplementation: Int = DataStore.tunImplementation,
    val mtu: Int = DataStore.mtu,
    val speedInterval: Int = DataStore.speedInterval,
    val profileTrafficStatistics: Boolean = DataStore.profileTrafficStatistics,
    val showDirectSpeed: Boolean = DataStore.showDirectSpeed,
    val showGroupInNotification: Boolean = DataStore.showGroupInNotification,
    val alwaysShowAddress: Boolean = DataStore.alwaysShowAddress,
    val meteredNetwork: Boolean = DataStore.meteredNetwork,
    val acquireWakeLock: Boolean = DataStore.acquireWakeLock,
    val logLevel: Int = DataStore.logLevel,
    val globalCustomConfig: String = DataStore.globalCustomConfig,
    val proxyApps: Boolean = DataStore.proxyApps,
    val bypassLan: Boolean = DataStore.bypassLan,
    val bypassLanInCore: Boolean = DataStore.bypassLanInCore,
    val trafficSniffing: Int = DataStore.trafficSniffing,
    val resolveDestination: Boolean = DataStore.resolveDestination,
    val ipv6Mode: Int = DataStore.ipv6Mode,
    val rulesProvider: Int = DataStore.rulesProvider,
    val remoteDns: String = DataStore.remoteDns,
    val remoteDomainStrategy: String = globalString(DOMAIN_STRATEGY_REMOTE),
    val directDns: String = DataStore.directDns,
    val directDomainStrategy: String = globalString(DOMAIN_STRATEGY_DIRECT),
    val serverDomainStrategy: String = globalString(DOMAIN_STRATEGY_SERVER),
    val enableDnsRouting: Boolean = DataStore.enableDnsRouting,
    val enableFakeDns: Boolean = DataStore.enableFakeDns,
    val mixedPort: Int = DataStore.mixedPort,
    val appendHttpProxy: Boolean = DataStore.appendHttpProxy,
    val socks5Auth: Boolean = DataStore.socks5Auth,
    val allowAccess: Boolean = DataStore.allowAccess,
    val connectionTestUrl: String = DataStore.connectionTestURL,
    val enableClashApi: Boolean = DataStore.enableClashAPI,
    val networkChangeResetConnections: Boolean = DataStore.networkChangeResetConnections,
    val wakeResetConnections: Boolean = DataStore.wakeResetConnections,
    val globalAllowInsecure: Boolean = DataStore.globalAllowInsecure,
    val allowInsecureOnRequest: Boolean = DataStore.allowInsecureOnRequest,
    val appTlsVersion: String = DataStore.appTLSVersion,
    val showBottomBar: Boolean = DataStore.showBottomBar,
)

sealed interface SettingsEffect {
    data object ReloadRequired : SettingsEffect
    data object ForceConfigReloadRequired : SettingsEffect
    data object RestartRequired : SettingsEffect
    data object ThemeChanged : SettingsEffect
    data object AppearanceChanged : SettingsEffect
    data object ServiceModeChanged : SettingsEffect
    data class ClashApiChanged(val enabled: Boolean) : SettingsEffect
    data object ProxyAppsChanged : SettingsEffect
}

private fun globalString(key: String): String =
    DataStore.configurationStore.getString(key) ?: "auto"

data class ThemeAppearanceSelection(
    val mode: Int,
    val dynamicColors: Boolean,
    val amoledDark: Boolean,
)

object ThemeAppearancePolicy {
    fun resolve(mode: Int, amoledDark: Boolean): ThemeAppearanceSelection =
        ThemeAppearanceSelection(
            mode = mode,
            dynamicColors = mode == Key.THEME_MODE_DYNAMIC,
            amoledDark = amoledDark,
        )
}

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _effects = MutableSharedFlow<SettingsEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<SettingsEffect> = _effects.asSharedFlow()

    fun setThemeMode(value: Int) = update(
        {
            DataStore.themeMode = value
            DataStore.composeDynamicColors = (value == Key.THEME_MODE_DYNAMIC)
            DataStore.interfaceStyle = if (value == Key.THEME_MODE_LIQUID_GLASS) {
                Key.INTERFACE_STYLE_CLEAR_GLASS
            } else {
                Key.INTERFACE_STYLE_STANDARD
            }
        },
        SettingsEffect.ThemeChanged,
    ) {
        copy(
            themeMode = value,
            dynamicColors = (value == Key.THEME_MODE_DYNAMIC),
            interfaceStyle = if (value == Key.THEME_MODE_LIQUID_GLASS) {
                Key.INTERFACE_STYLE_CLEAR_GLASS
            } else {
                Key.INTERFACE_STYLE_STANDARD
            },
            appTheme = appTheme,
        )
    }

    /** Persist the complete theme appearance as one UI update and one effect. */
    fun setThemeAppearance(mode: Int, amoledDark: Boolean) = update(
        {
            val selection = ThemeAppearancePolicy.resolve(mode, amoledDark)
            DataStore.themeMode = selection.mode
            DataStore.composeDynamicColors = selection.dynamicColors
            DataStore.amoledDark = selection.amoledDark
            DataStore.interfaceStyle = if (selection.mode == Key.THEME_MODE_LIQUID_GLASS) {
                Key.INTERFACE_STYLE_CLEAR_GLASS
            } else {
                Key.INTERFACE_STYLE_STANDARD
            }
        },
        SettingsEffect.ThemeChanged,
    ) {
        val selection = ThemeAppearancePolicy.resolve(mode, amoledDark)
        copy(
            themeMode = selection.mode,
            dynamicColors = selection.dynamicColors,
            amoledDark = selection.amoledDark,
            interfaceStyle = if (selection.mode == Key.THEME_MODE_LIQUID_GLASS) {
                Key.INTERFACE_STYLE_CLEAR_GLASS
            } else {
                Key.INTERFACE_STYLE_STANDARD
            },
            appTheme = appTheme,
        )
    }
    fun setClassicTheme(value: Int) = update(
        {
            DataStore.appTheme = value
            DataStore.themeMode = Key.THEME_MODE_CLASSIC
            DataStore.composeDynamicColors = false
            DataStore.interfaceStyle = Key.INTERFACE_STYLE_STANDARD
        },
        SettingsEffect.ThemeChanged,
    ) {
        copy(
            appTheme = value,
            themeMode = Key.THEME_MODE_CLASSIC,
            dynamicColors = false,
            interfaceStyle = Key.INTERFACE_STYLE_STANDARD,
        )
    }
    fun setDynamicColors(value: Boolean) = update(
        {
            DataStore.composeDynamicColors = value
            DataStore.themeMode = if (value) Key.THEME_MODE_DYNAMIC else Key.THEME_MODE_GREEN
            DataStore.interfaceStyle = Key.INTERFACE_STYLE_STANDARD
        },
        SettingsEffect.ThemeChanged,
    ) {
        copy(
            dynamicColors = value,
            themeMode = if (value) Key.THEME_MODE_DYNAMIC else Key.THEME_MODE_GREEN,
            interfaceStyle = Key.INTERFACE_STYLE_STANDARD,
        )
    }
    fun setAmoledDark(value: Boolean) = update(
        { DataStore.amoledDark = value },
        SettingsEffect.ThemeChanged,
    ) { copy(amoledDark = value) }
    fun setInterfaceStyle(value: Int) = update(
        {
            val style = io.github.hhwkart.nami.ui.compose.style.InterfaceStyle.fromPersisted(value)
            DataStore.interfaceStyle = style.persistedValue
            DataStore.themeMode = if (style == io.github.hhwkart.nami.ui.compose.style.InterfaceStyle.STANDARD) {
                Key.THEME_MODE_GREEN
            } else {
                Key.THEME_MODE_LIQUID_GLASS
            }
        },
        SettingsEffect.AppearanceChanged,
    ) {
        val style = io.github.hhwkart.nami.ui.compose.style.InterfaceStyle.fromPersisted(value)
        copy(
            interfaceStyle = style.persistedValue,
            themeMode = if (style == io.github.hhwkart.nami.ui.compose.style.InterfaceStyle.STANDARD) {
                Key.THEME_MODE_GREEN
            } else {
                Key.THEME_MODE_LIQUID_GLASS
            },
        )
    }
    fun setLiquidGlassQuality(value: Int) = update(
        { DataStore.liquidGlassQuality = io.github.hhwkart.nami.ui.compose.style.LiquidGlassQuality.fromPersisted(value).persistedValue },
        SettingsEffect.AppearanceChanged,
    ) { copy(liquidGlassQuality = io.github.hhwkart.nami.ui.compose.style.LiquidGlassQuality.fromPersisted(value).persistedValue) }
    fun setAutoConnect(value: Boolean) = update({ DataStore.configurationStore.putBoolean(Key.PERSIST_ACROSS_REBOOT, value) }) { copy(autoConnect = value) }
    fun setAppTheme(value: Int) = setClassicTheme(value)
    fun setNightTheme(value: Int) = update({ DataStore.nightTheme = value }, SettingsEffect.ThemeChanged) { copy(nightTheme = value) }
    fun setServiceMode(value: String) = update({ DataStore.serviceMode = value }, SettingsEffect.ServiceModeChanged) { copy(serviceMode = value) }
    fun setTunImplementation(value: Int) = update({ DataStore.tunImplementation = value }, SettingsEffect.ReloadRequired) { copy(tunImplementation = value) }
    fun setMtu(value: Int) = update({ DataStore.mtu = value }, SettingsEffect.ReloadRequired) { copy(mtu = value) }
    fun setSpeedInterval(value: Int) = update({ DataStore.speedInterval = value }, SettingsEffect.ReloadRequired) { copy(speedInterval = value) }
    fun setProfileTrafficStatistics(value: Boolean) = update({ DataStore.profileTrafficStatistics = value }, SettingsEffect.ReloadRequired) { copy(profileTrafficStatistics = value) }
    fun setShowDirectSpeed(value: Boolean) = update({ DataStore.showDirectSpeed = value }, SettingsEffect.ReloadRequired) { copy(showDirectSpeed = value) }
    fun setShowGroupInNotification(value: Boolean) = update({ DataStore.showGroupInNotification = value }, SettingsEffect.ReloadRequired) { copy(showGroupInNotification = value) }
    fun setAlwaysShowAddress(value: Boolean) = update({ DataStore.alwaysShowAddress = value }, SettingsEffect.ReloadRequired) { copy(alwaysShowAddress = value) }
    fun setMeteredNetwork(value: Boolean) = update({ DataStore.meteredNetwork = value }, SettingsEffect.ReloadRequired) { copy(meteredNetwork = value) }
    fun setAcquireWakeLock(value: Boolean) = update({ DataStore.acquireWakeLock = value }, SettingsEffect.ReloadRequired) { copy(acquireWakeLock = value) }
    fun setLogLevel(value: Int) = update({ DataStore.logLevel = value }, SettingsEffect.RestartRequired) { copy(logLevel = value) }
    fun setGlobalCustomConfig(value: String) = update({ DataStore.globalCustomConfig = value }, SettingsEffect.ReloadRequired) { copy(globalCustomConfig = value) }
    fun setProxyApps(value: Boolean) = update({ DataStore.proxyApps = value; if (value) DataStore.dirty = true }, SettingsEffect.ReloadRequired) { copy(proxyApps = value) }
    fun setBypassLan(value: Boolean) = update({ DataStore.bypassLan = value }, SettingsEffect.ReloadRequired) { copy(bypassLan = value) }
    fun setBypassLanInCore(value: Boolean) = update({ DataStore.bypassLanInCore = value }, SettingsEffect.ReloadRequired) { copy(bypassLanInCore = value) }
    fun setTrafficSniffing(value: Int) = update({ DataStore.trafficSniffing = value }, SettingsEffect.ReloadRequired) { copy(trafficSniffing = value) }
    fun setResolveDestination(value: Boolean) = update({ DataStore.resolveDestination = value }, SettingsEffect.ReloadRequired) { copy(resolveDestination = value) }
    fun setIpv6Mode(value: Int) = update({ DataStore.ipv6Mode = value }, SettingsEffect.ReloadRequired) { copy(ipv6Mode = value) }
    fun setRulesProvider(value: Int) = update({ DataStore.rulesProvider = value }, SettingsEffect.ReloadRequired) { copy(rulesProvider = value) }
    fun setRemoteDns(value: String) = update({ DataStore.remoteDns = value }, SettingsEffect.ReloadRequired) { copy(remoteDns = value) }
    fun setRemoteDomainStrategy(value: String) = setGlobalString(DOMAIN_STRATEGY_REMOTE, value) { copy(remoteDomainStrategy = value) }
    fun setDirectDns(value: String) = update({ DataStore.directDns = value }, SettingsEffect.ReloadRequired) { copy(directDns = value) }
    fun setDirectDomainStrategy(value: String) = setGlobalString(DOMAIN_STRATEGY_DIRECT, value) { copy(directDomainStrategy = value) }
    fun setServerDomainStrategy(value: String) = setGlobalString(DOMAIN_STRATEGY_SERVER, value) { copy(serverDomainStrategy = value) }
    fun setEnableDnsRouting(value: Boolean) = update({ DataStore.enableDnsRouting = value }, SettingsEffect.ReloadRequired) { copy(enableDnsRouting = value) }
    fun setEnableFakeDns(value: Boolean) = update({ DataStore.enableFakeDns = value }, SettingsEffect.ReloadRequired) { copy(enableFakeDns = value) }
    fun setMixedPort(value: Int) = update({ DataStore.mixedPort = value }, SettingsEffect.ReloadRequired) { copy(mixedPort = value) }
    fun setAppendHttpProxy(value: Boolean) = update({ DataStore.appendHttpProxy = value }, SettingsEffect.ReloadRequired) { copy(appendHttpProxy = value) }
    fun setSocks5Auth(value: Boolean) = update({ DataStore.socks5Auth = value }, SettingsEffect.ReloadRequired) { copy(socks5Auth = value) }
    fun setAllowAccess(value: Boolean) = update({ DataStore.allowAccess = value }, SettingsEffect.ForceConfigReloadRequired) { copy(allowAccess = value) }
    fun setConnectionTestUrl(value: String) = update({ DataStore.connectionTestURL = value }) { copy(connectionTestUrl = value) }
    fun setEnableClashApi(value: Boolean) = update({ DataStore.enableClashAPI = value }, SettingsEffect.ClashApiChanged(value)) { copy(enableClashApi = value) }
    fun setNetworkChangeResetConnections(value: Boolean) = update({ DataStore.networkChangeResetConnections = value }) { copy(networkChangeResetConnections = value) }
    fun setWakeResetConnections(value: Boolean) = update({ DataStore.wakeResetConnections = value }) { copy(wakeResetConnections = value) }
    fun setGlobalAllowInsecure(value: Boolean) = update({ DataStore.globalAllowInsecure = value }) { copy(globalAllowInsecure = value) }
    fun setAllowInsecureOnRequest(value: Boolean) = update({ DataStore.allowInsecureOnRequest = value }) { copy(allowInsecureOnRequest = value) }
    fun setAppTlsVersion(value: String) = update({ DataStore.appTLSVersion = value }) { copy(appTlsVersion = value) }
    fun setShowBottomBar(value: Boolean) = update({ DataStore.showBottomBar = value }) { copy(showBottomBar = value) }

    private fun setGlobalString(
        key: String,
        value: String,
        state: SettingsUiState.() -> SettingsUiState,
    ) = update({ DataStore.configurationStore.putString(key, value) }, SettingsEffect.ReloadRequired, state)

    private fun update(
        write: () -> Unit,
        effect: SettingsEffect? = null,
        state: SettingsUiState.() -> SettingsUiState,
    ) {
        write()
        _uiState.update(state)
        effect?.let { _effects.tryEmit(it) }
    }
}
