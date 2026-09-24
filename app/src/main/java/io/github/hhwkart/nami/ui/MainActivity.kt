package io.github.hhwkart.nami.ui

import android.Manifest.permission.POST_NOTIFICATIONS
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.RemoteException
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import io.github.hhwkart.nami.BuildConfig
import io.github.hhwkart.nami.GroupType
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.LocalNetworkPermissionTransition
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.hasLocalNetworkPermission
import io.github.hhwkart.nami.aidl.ISagerNetService
import io.github.hhwkart.nami.aidl.SpeedDisplayData
import io.github.hhwkart.nami.aidl.TrafficData
import io.github.hhwkart.nami.bg.BaseService
import io.github.hhwkart.nami.bg.SagerConnection
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.GroupManager
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.database.SubscriptionBean
import io.github.hhwkart.nami.fmt.AbstractBean
import io.github.hhwkart.nami.fmt.KryoConverters
import io.github.hhwkart.nami.fmt.PluginEntry
import io.github.hhwkart.nami.group.GroupInterfaceAdapter
import io.github.hhwkart.nami.group.GroupUpdater
import io.github.hhwkart.nami.ktx.alert
import io.github.hhwkart.nami.ktx.isPreview
import io.github.hhwkart.nami.ktx.launchCustomTab
import io.github.hhwkart.nami.ktx.onMainDispatcher
import io.github.hhwkart.nami.ktx.parseProxies
import io.github.hhwkart.nami.ktx.readableMessage
import io.github.hhwkart.nami.ktx.runOnDefaultDispatcher
import io.github.hhwkart.nami.ktx.showWithNamiLiquidGlassBlur
import io.github.hhwkart.nami.ktx.triggerFullRestart
import io.github.hhwkart.nami.ui.compose.ConnectionBus
import io.github.hhwkart.nami.ui.compose.Destination
import io.github.hhwkart.nami.ui.compose.NamiAppShell
import io.github.hhwkart.nami.ui.compose.ConfirmationDialog
import io.github.hhwkart.nami.ui.compose.NavigationBus
import io.github.hhwkart.nami.ui.compose.common.UserFacingError
import io.github.hhwkart.nami.ui.compose.routing.RoutingPreset
import io.github.hhwkart.nami.ui.compose.routing.RoutingPresetManager
import io.github.hhwkart.nami.ui.compose.startup.InstallFacts
import io.github.hhwkart.nami.ui.compose.startup.InstallState
import io.github.hhwkart.nami.ui.compose.startup.InstallStateDetector
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupResult
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupEntryPoint
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupReturnPolicy
import io.github.hhwkart.nami.ui.compose.startup.QuickSetupSource
import io.github.hhwkart.nami.ui.compose.startup.WHATS_NEW_CONTENT_VERSION
import io.github.hhwkart.nami.ui.compose.startup.WhatsNewPolicy
import io.github.hhwkart.nami.ui.compose.theme.NamiTheme
import io.github.hhwkart.nami.core.utils.Util

/** The single user-facing activity. All app screens are hosted by Compose navigation. */
@dagger.hilt.android.AndroidEntryPoint
class MainActivity : ThemedActivity(), SagerConnection.Callback {

    private var localProxyAuthRequestId = 0L
    var localNetworkPermissionGranted by mutableStateOf(false)
        private set

    private sealed interface PendingExternalAction {
        data object ScanQr : PendingExternalAction
        data class OpenUri(val uri: Uri) : PendingExternalAction
    }

    private var startupGateActive = true
    private var startupInstallState: InstallState? = null
    private var pendingExternalAction: PendingExternalAction? = null
    private var externalActionInFlight = false
    private var startupDialogsRequested = false
    private var showWhatsNewAfterExternalAction = false
    private var quickSetupEntryPoint = QuickSetupEntryPoint.INITIAL_ONBOARDING
    private var confirmationDialog by mutableStateOf<ConfirmationDialog?>(null)

    val connection = SagerConnection(
        SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND,
        true,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
            ),
        )
        super.onCreate(savedInstanceState)
        localNetworkPermissionGranted = hasLocalNetworkPermission()

        quickSetupEntryPoint = savedInstanceState?.getString(QUICK_SETUP_RETURN_DESTINATION)
            ?.let { value -> runCatching { QuickSetupEntryPoint.valueOf(value) }.getOrNull() }
            ?: QuickSetupEntryPoint.INITIAL_ONBOARDING

        captureExternalIntent(intent)

        setContent {
            NamiTheme {
                NamiAppShell(
                    activity = this,
                    confirmationDialog = confirmationDialog,
                    onDismissConfirmation = { confirmationDialog = null },
                    onConfirmConfirmation = ::confirmDialogAction,
                )
            }
        }
        ConnectionBus.onConnectionToggle = {
            if (DataStore.serviceState.canStop) SagerNet.stopService() else connect.launch(null)
        }
        ConnectionBus.onTestUrl = {
            if (DataStore.serviceState.started) {
                try {
                    connection.service?.urlTest() ?: -1
                } catch (_: Exception) {
                    -1
                }
            } else {
                -1
            }
        }
        ConnectionBus.resetSessionClock()

        changeState(BaseService.State.Idle)
        connection.connect(this, this)
        GroupManager.userInterface = GroupInterfaceAdapter(this)

        startStartupFlow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (captureExternalIntent(intent) && !startupGateActive) {
            dispatchPendingExternalAction()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(
            QUICK_SETUP_RETURN_DESTINATION,
            quickSetupEntryPoint.name,
        )
        super.onSaveInstanceState(outState)
    }

    private fun captureExternalIntent(intent: Intent?): Boolean {
        intent ?: return false
        return when {
            intent.action == ACTION_SCAN_QR -> {
                pendingExternalAction = PendingExternalAction.ScanQr
                true
            }
            intent.action == Intent.ACTION_VIEW && intent.data != null -> {
                pendingExternalAction = PendingExternalAction.OpenUri(intent.data!!)
                true
            }
            else -> false
        }
    }

    private fun startStartupFlow() {
        runOnDefaultDispatcher {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val facts = InstallFacts(
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime,
                hasUserCreatedProfiles = SagerDatabase.proxyDao.getAll().isNotEmpty(),
                hasUserCreatedSubscriptions = SagerDatabase.groupDao.subscriptions().any {
                    !it.subscription?.link.isNullOrBlank() || !it.subscription?.token.isNullOrBlank()
                },
            )
            val state = InstallStateDetector.detect(facts)
            onMainDispatcher {
                startupInstallState = state
                if (state == InstallState.FRESH_INSTALL && !DataStore.onboardingSeen) {
                    NavigationBus.open(Destination.Onboarding)
                } else {
                    DataStore.onboardingSeen = true
                    continueStartupAfterOnboarding()
                }
            }
        }
    }

    fun onOnboardingFirstPageBack() {
        pendingExternalAction = null
        showWhatsNewAfterExternalAction = false
        NavigationBus.open(Destination.Home)
        finishStartupGate()
    }

    fun onOnboardingSkipped() {
        DataStore.onboardingSeen = true
        if (startupInstallState == InstallState.FRESH_INSTALL) {
            DataStore.whatsNewVersion = WHATS_NEW_CONTENT_VERSION
        }
        NavigationBus.open(Destination.Home)
        finishStartupGate()
    }

    fun onOnboardingGetStarted() {
        DataStore.onboardingSeen = true
        if (startupInstallState == InstallState.FRESH_INSTALL) {
            DataStore.whatsNewVersion = WHATS_NEW_CONTENT_VERSION
        }
        if (quickSetupEntryPoint != QuickSetupEntryPoint.SETTINGS_OR_ABOUT) {
            quickSetupEntryPoint = QuickSetupEntryPoint.INITIAL_ONBOARDING
        }
        NavigationBus.open(Destination.QuickSetup)
    }

    fun onQuickSetupCancelled(): Destination {
        finishStartupGate()
        return QuickSetupReturnPolicy.destination(quickSetupEntryPoint)
    }

    fun openQuickSetupFromProfiles() {
        quickSetupEntryPoint = QuickSetupEntryPoint.PROFILES
        NavigationBus.open(Destination.QuickSetup)
    }

    fun onQuickSetupFinished(result: QuickSetupResult): Destination {
        result.resultGroupId?.takeIf { it > 0L }?.let { groupId ->
            // Keep the actual operation target selected after import completes.
            DataStore.selectedGroup = groupId
            DataStore.editingGroup = groupId
        }
        finishStartupGate()
        return QuickSetupReturnPolicy.destination(quickSetupEntryPoint)
    }

    fun onWhatsNewOpened() {
        DataStore.whatsNewVersion = WHATS_NEW_CONTENT_VERSION
    }

    fun onWhatsNewDismissed() {
        DataStore.whatsNewVersion = WHATS_NEW_CONTENT_VERSION
        NavigationBus.open(Destination.Home)
        if (startupGateActive) finishStartupGate() else requestStartupDialogs()
    }

    fun openOnboardingFromSettings() {
        quickSetupEntryPoint = QuickSetupEntryPoint.SETTINGS_OR_ABOUT
        NavigationBus.open(Destination.Onboarding)
    }

    fun openWhatsNewFromSettings() {
        NavigationBus.open(Destination.WhatsNew)
    }

    private fun continueStartupAfterOnboarding() {
        val whatsNewDue = WhatsNewPolicy.isDue(DataStore.whatsNewVersion)
        if (pendingExternalAction != null) {
            showWhatsNewAfterExternalAction = whatsNewDue
            finishStartupGate()
        } else if (whatsNewDue) {
            NavigationBus.open(Destination.WhatsNew)
        } else {
            finishStartupGate()
        }
    }

    private fun finishStartupGate() {
        if (!startupGateActive) return
        startupGateActive = false
        if (pendingExternalAction != null) {
            dispatchPendingExternalAction()
        } else {
            requestStartupDialogs()
        }
    }

    private fun dispatchPendingExternalAction() {
        val action = pendingExternalAction ?: run {
            requestStartupDialogs()
            return
        }
        pendingExternalAction = null
        externalActionInFlight = true
        when (action) {
            PendingExternalAction.ScanQr -> NavigationBus.open(Destination.QrScanner)
            is PendingExternalAction.OpenUri -> runOnDefaultDispatcher {
                if (action.uri.scheme == "sn" && action.uri.host == "subscription" || action.uri.scheme == "clash") {
                    importSubscription(action.uri)
                } else {
                    importProfile(action.uri)
                }
            }
        }
    }

    fun onExternalActionSettled() {
        if (!externalActionInFlight) return
        externalActionInFlight = false
        if (showWhatsNewAfterExternalAction && WhatsNewPolicy.isDue(DataStore.whatsNewVersion)) {
            showWhatsNewAfterExternalAction = false
            NavigationBus.open(Destination.WhatsNew)
        } else {
            showWhatsNewAfterExternalAction = false
            requestStartupDialogs()
        }
    }

    fun onQrScannerFinished() {
        onExternalActionSettled()
    }

    private fun requestStartupDialogs() {
        if (startupDialogsRequested) return
        startupDialogsRequested = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(POST_NOTIFICATIONS), 0)
        }
        if (isPreview) {
            MaterialAlertDialogBuilder(this)
                .setTitle(BuildConfig.PRE_VERSION_NAME)
                .setMessage(R.string.preview_version_hint)
                .setPositiveButton(android.R.string.ok, null)
                .showWithNamiLiquidGlassBlur(this@MainActivity)
        }
    }

    fun urlTest(): Int {
        if (!DataStore.serviceState.connected || connection.service == null) error("not started")
        return connection.service!!.urlTest()
    }

    suspend fun importSubscription(uri: Uri, onResult: ((Long?) -> Unit)? = null) {
        val group = parseSubscription(uri) ?: run {
            onResult?.invoke(null)
            onMainDispatcher { onExternalActionSettled() }
            return
        }
        val name = group.name.takeIf { !it.isNullOrBlank() }
            ?: group.subscription?.link
            ?: group.subscription?.token
        if (name.isNullOrBlank()) {
            onResult?.invoke(null)
            onMainDispatcher {
                alert("The subscription did not contain a usable name or URL.").show()
                onExternalActionSettled()
            }
            return
        }
        group.name = group.name.takeIf { !it.isNullOrBlank() }
            ?: "Subscription #${System.currentTimeMillis()}"

        onMainDispatcher {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.subscription_import)
                .setMessage(getString(R.string.subscription_import_message, name))
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher {
                        runCatching {
                            GroupManager.createGroup(group)
                            GroupUpdater.startUpdate(group, true)
                        }.onSuccess { onResult?.invoke(group.id) }
                            .onFailure { onResult?.invoke(null) }
                        onMainDispatcher { onExternalActionSettled() }
                    }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    onResult?.invoke(null)
                    onExternalActionSettled()
                }
                .setOnCancelListener {
                    onResult?.invoke(null)
                    onExternalActionSettled()
                }
                .showWithNamiLiquidGlassBlur(this@MainActivity)
        }
    }

    private suspend fun parseSubscription(uri: Uri): ProxyGroup? {
        val url = uri.getQueryParameter("url")
        if (!url.isNullOrBlank()) {
            return ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
                subscription = SubscriptionBean().also { it.link = url }
                name = uri.getQueryParameter("name")
            }
        }
        val data = uri.encodedQuery.takeIf { !it.isNullOrBlank() } ?: return null
        return try {
            KryoConverters.deserialize(
                ProxyGroup().apply { export = true },
                Util.zlibDecompress(Util.b64Decode(data)),
            ).apply { export = false }
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
                onExternalActionSettled()
            }
            null
        }
    }

    suspend fun importProfile(uri: Uri) {
        val profile = try {
            parseProxies(uri.toString()).firstOrNull()
                ?: error(getString(R.string.no_proxies_found))
        } catch (e: Exception) {
            onMainDispatcher {
                alert(e.readableMessage).show()
                onExternalActionSettled()
            }
            return
        }

        onMainDispatcher {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.profile_import)
                .setMessage(getString(R.string.profile_import_message, profile.displayName()))
                .setPositiveButton(R.string.yes) { _, _ ->
                    runOnDefaultDispatcher { finishImportProfile(profile) }
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> onExternalActionSettled() }
                .setOnCancelListener { onExternalActionSettled() }
                .showWithNamiLiquidGlassBlur(this@MainActivity)
        }
    }

    private suspend fun finishImportProfile(profile: AbstractBean) {
        runCatching {
            ProfileManager.createProfile(DataStore.selectedGroupForImport(), profile)
        }.onSuccess {
            onMainDispatcher {
                Toast.makeText(
                    this@MainActivity,
                    resources.getQuantityString(R.plurals.added, 1, 1),
                    Toast.LENGTH_LONG,
                ).show()
                onExternalActionSettled()
            }
        }.onFailure { error ->
            onMainDispatcher {
                alert(error.readableMessage).show()
                onExternalActionSettled()
            }
        }
    }

    fun quickSetupSubscription(url: String, onResult: (QuickSetupResult) -> Unit) {
        runOnDefaultDispatcher {
            val group = ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
                name = "Quick subscription"
                subscription = SubscriptionBean().also { it.link = url }
            }
            runCatching {
                GroupManager.createGroup(group)
                GroupUpdater.startUpdate(group, true)
            }.onSuccess {
                onMainDispatcher {
                    onResult(QuickSetupResult(true, group.id, QuickSetupSource.SUBSCRIPTION))
                }
            }.onFailure { error ->
                onMainDispatcher {
                    onResult(
                        QuickSetupResult(
                            completed = false,
                            resultGroupId = group.id.takeIf { it > 0L },
                            source = QuickSetupSource.SUBSCRIPTION,
                            error = UserFacingError.fromThrowable(error, url),
                        ),
                    )
                }
            }
        }
    }

    fun applyRoutingPreset(
        preset: RoutingPreset,
        onResult: (Result<Unit>) -> Unit = {},
    ) {
        runOnDefaultDispatcher {
            val result = RoutingPresetManager.apply(preset)
            onMainDispatcher { onResult(result) }
        }
    }

    override fun missingPlugin(profileName: String, pluginName: String) {
        val pluginEntity = PluginEntry.find(pluginName)
        if (pluginEntity == null) {
            Toast.makeText(
                this,
                getString(R.string.plugin_unknown, pluginName),
                Toast.LENGTH_LONG,
            ).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.missing_plugin)
            .setMessage(getString(R.string.profile_requiring_plugin, profileName, pluginEntity.displayName))
            .setPositiveButton(R.string.action_download) { _, _ -> showDownloadDialog(pluginEntity) }
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.action_learn_more) { _, _ ->
                launchCustomTab("https://matsuridayo.github.io/nb4a-plugin/")
            }
            .showWithNamiLiquidGlassBlur(this)
    }

    private fun showDownloadDialog(pluginEntry: PluginEntry) {
        val links = buildList {
            if (pluginEntry.downloadSource.playStore) {
                add(getString(R.string.install_from_play_store) to
                    "https://play.google.com/store/apps/details?id=${pluginEntry.packageName}")
            }
            if (pluginEntry.downloadSource.fdroid) {
                add(getString(R.string.install_from_fdroid) to
                    "https://f-droid.org/packages/${pluginEntry.packageName}/")
            }
            add(getString(R.string.download) to pluginEntry.downloadSource.downloadLink)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(pluginEntry.name)
            .setItems(links.map { it.first }.toTypedArray()) { _, which ->
                launchCustomTab(links[which].second)
            }
            .showWithNamiLiquidGlassBlur(this)
    }

    fun importSubscriptionFromCompose(uri: Uri, onResult: ((Long?) -> Unit)? = null) {
        runOnDefaultDispatcher { importSubscription(uri, onResult) }
    }

    fun resetSettingsFromCompose() {
        val onboardingSeen = DataStore.onboardingSeen
        val whatsNewVersion = DataStore.whatsNewVersion
        DataStore.configurationStore.reset()
        DataStore.configurationStore.putBoolean(Key.ONBOARDING_SEEN, onboardingSeen)
        DataStore.configurationStore.putString(Key.WHATS_NEW_VERSION, whatsNewVersion)
        RoutingPresetManager.clearOwnership()
        triggerFullRestart(this)
    }

    fun restartAfterImport() {
        triggerFullRestart(this)
    }

    fun openFaq() {
        launchCustomTab("https://matsuridayo.github.io/")
    }

    fun confirmServiceReload() {
        if (!DataStore.serviceState.started) return
        confirmationDialog = ConfirmationDialog.RELOAD_SERVICE
    }

    fun synchronizeLocalNetworkPermission() {
        val permissionIsGranted = hasLocalNetworkPermission()
        localNetworkPermissionGranted = permissionIsGranted
        if (localNetworkPermissionTransition.shouldForceReload(
                permissionIsGranted = permissionIsGranted,
                serviceStarted = DataStore.serviceState.started,
                sdkInt = Build.VERSION.SDK_INT,
            )
        ) {
            SagerNet.reloadService(forceConfigRebuild = true)
        }
    }

    fun confirmForceConfigReload() {
        if (!DataStore.serviceState.started) return
        confirmationDialog = ConfirmationDialog.FORCE_CONFIG_RELOAD_SERVICE
    }

    fun confirmAppRestart() {
        confirmationDialog = ConfirmationDialog.RESTART_APP
    }

    private fun confirmDialogAction(dialog: ConfirmationDialog) {
        confirmationDialog = null
        when (dialog) {
            ConfirmationDialog.RELOAD_SERVICE -> SagerNet.reloadService()
            ConfirmationDialog.FORCE_CONFIG_RELOAD_SERVICE ->
                SagerNet.reloadService(forceConfigRebuild = true)
            ConfirmationDialog.RESTART_APP -> triggerFullRestart(this)
        }
    }

    fun applyThemeChange() {
        if (DataStore.serviceState.started) SagerNet.reloadService()
        ActivityCompat.recreate(this)
    }

    fun applyAppearanceChange() {
        ActivityCompat.recreate(this)
    }

    fun applyServiceModeChange() {
        if (DataStore.serviceState.started) SagerNet.stopService()
    }

    private fun changeState(state: BaseService.State, msg: String? = null) {
        DataStore.serviceState = state
        if (state == BaseService.State.Connected) {
            connection.service?.let(::refreshLocalProxyAuth) ?: clearLocalProxyAuth()
        } else {
            clearLocalProxyAuth()
        }
        ConnectionBus.postState(state, msg)
        if (msg != null) {
            Toast.makeText(this, getString(R.string.vpn_error, msg), Toast.LENGTH_LONG).show()
        }
    }

    private fun clearLocalProxyAuth() {
        localProxyAuthRequestId += 1
        SagerNet.localProxyAuth = null
        ConnectionBus.postLocalProxyAuth(null)
    }

    private fun refreshLocalProxyAuth(service: ISagerNetService) {
        val requestId = ++localProxyAuthRequestId
        val binder = service.asBinder()
        runOnDefaultDispatcher {
            val credentials = try {
                val user = service.proxyAuthUser
                val pass = service.proxyAuthPass
                if (user.isNotBlank() && pass.isNotBlank()) user to pass else null
            } catch (_: RemoteException) {
                null
            }

            onMainDispatcher {
                if (requestId != localProxyAuthRequestId ||
                    connection.service?.asBinder() != binder ||
                    DataStore.serviceState != BaseService.State.Connected
                ) {
                    return@onMainDispatcher
                }
                SagerNet.localProxyAuth = credentials
                ConnectionBus.postLocalProxyAuth(credentials.takeIf { DataStore.allowAccess })
            }
        }
    }

    override fun snackbarInternal(text: CharSequence): Snackbar =
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {
        changeState(state, msg)
        if (state.connected) synchronizeLocalNetworkPermission()
    }

    override fun onServiceConnected(service: ISagerNetService) {
        changeState(
            try {
                BaseService.State.values()[service.state]
            } catch (_: RemoteException) {
                BaseService.State.Idle
            },
        )
        if (DataStore.serviceState.connected) synchronizeLocalNetworkPermission()
    }

    override fun onServiceDisconnected() {
        clearLocalProxyAuth()
        changeState(BaseService.State.Idle)
    }

    override fun onBinderDied() {
        clearLocalProxyAuth()
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(VpnRequestActivity.StartService()) { denied ->
        if (denied) {
            Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    override fun cbSpeedUpdate(stats: SpeedDisplayData) {
        ConnectionBus.postSpeed(stats)
    }

    override fun cbTrafficUpdate(data: TrafficData) {
        runOnDefaultDispatcher { ProfileManager.postUpdate(data) }
    }

    override fun cbSelectorUpdate(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        DataStore.currentProfile = id
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
    }

    override fun onStart() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_FOREGROUND)
        super.onStart()
    }

    override fun onStop() {
        connection.updateConnectionId(SagerConnection.CONNECTION_ID_MAIN_ACTIVITY_BACKGROUND)
        super.onStop()
    }

    override fun onDestroy() {
        ConnectionBus.onConnectionToggle = null
        ConnectionBus.onTestUrl = null
        GroupManager.userInterface = null
        clearLocalProxyAuth()
        connection.disconnect(this)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SCAN_QR = "io.github.hhwkart.nami.action.SCAN_QR"
        private const val QUICK_SETUP_RETURN_DESTINATION = "quickSetupReturnDestination"
        private val localNetworkPermissionTransition = LocalNetworkPermissionTransition()
    }
}
