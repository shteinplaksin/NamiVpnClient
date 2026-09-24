package io.github.hhwkart.nami

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.github.hhwkart.nami.bg.NotificationChannels
import io.github.hhwkart.nami.bg.SagerConnection
import io.github.hhwkart.nami.data.backup.LegacyRestoreRecovery
import io.github.hhwkart.nami.data.settings.StorageBootstrap
import io.github.hhwkart.nami.data.settings.StorageBootstrapState
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.ktx.Logs
import io.github.hhwkart.nami.ktx.isOss
import io.github.hhwkart.nami.ktx.isPreview
import io.github.hhwkart.nami.ktx.runOnDefaultDispatcher
import io.github.hhwkart.nami.ui.MainActivity
import io.github.hhwkart.nami.utils.*
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import libcore.Libcore
import io.github.hhwkart.nami.core.NativeInterface
import io.github.hhwkart.nami.core.net.LocalResolverImpl
import io.github.hhwkart.nami.core.utils.JavaUtil
import io.github.hhwkart.nami.core.utils.cleanWebview
import java.io.File
import androidx.work.Configuration as WorkConfiguration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SagerNet : Application(),
    WorkConfiguration.Provider {
    @Inject
    lateinit var storageBootstrap: StorageBootstrap

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        application = this
    }

    private val nativeInterface = NativeInterface()

    val externalAssets: File by lazy { getExternalFilesDir(null) ?: filesDir }
    val process: String = JavaUtil.getProcessName()
    private val isMainProcess = process == BuildConfig.APPLICATION_ID
    val isBgProcess = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess) {
            registerServiceLifecycleReceiver()
        }

        if (isMainProcess || isBgProcess) {
            // Notification channels belong to the package-wide system state,
            // but background services can start before the main process.
            NotificationChannels.ensure(this)
            externalAssets.mkdirs()
            Seq.setContext(this)
            Libcore.initCore(
                process,
                cacheDir.absolutePath + "/",
                filesDir.absolutePath + "/",
                externalAssets.absolutePath + "/",
                DataStore.logBufSize,
                DataStore.logLevel > 0,
                nativeInterface, nativeInterface, LocalResolverImpl
            )

            // Start the lossless Room -> multiprocess Preferences migration in
            // both processes. Existing callers still use the legacy facade in
            // this checkpoint, so a migration failure can safely fall back to
            // configuration.db while the next adapter unit is implemented.
            applicationScope.launch {
                when (val state = storageBootstrap.awaitReady()) {
                    StorageBootstrapState.Ready ->
                        Logs.i("Configuration DataStore bootstrap ready ($process)")

                    is StorageBootstrapState.LegacyFallback ->
                        Logs.w("Configuration DataStore unavailable; keeping legacy fallback ($process)", state.cause)

                    is StorageBootstrapState.Failed ->
                        Logs.e("Configuration storage bootstrap failed ($process)", state.cause)

                    StorageBootstrapState.NotStarted,
                    StorageBootstrapState.Starting,
                    -> Unit
                }
            }

            applicationScope.launch(Dispatchers.IO) {
                runCatching { LegacyRestoreRecovery.recoverPending(this@SagerNet) }
                    .onFailure { error ->
                        Logs.e("Pending restore recovery failed", error)
                    }
            }

            // fix multi process issue in Android 9+
            JavaUtil.handleWebviewDir(this)

            runOnDefaultDispatcher {
                PackageCache.register()
                cleanWebview()
            }
        }

        if (isMainProcess) {
            Theme.apply(this)
            Theme.applyNightTheme()
            runOnDefaultDispatcher {
                DefaultNetworkListener.start(this) {
                    underlyingNetwork = it
                }
            }
        }

        if (BuildConfig.DEBUG) {
            System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        NotificationChannels.ensure(this)
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:bg")
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        Libcore.forceGc()
    }

    @SuppressLint("InlinedApi")
    companion object {

        lateinit var application: SagerNet

        val isTv by lazy {
            uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it,
                    0,
                    Intent(
                        application, MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val uiMode by lazy { application.getSystemService<UiModeManager>()!! }
        val power by lazy { application.getSystemService<PowerManager>()!! }

        // Main-process serialization for service launch versus backup/restore.
        // The :bg service entrypoint still has its own readiness gate; this
        // mutex prevents app-owned launchers from racing a main-process
        // restore operation.
        private val serviceLaunchMutex = Mutex()
        private val serviceStoppedEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        private val serviceStoppedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Action.SERVICE_STOPPED) {
                    serviceStoppedEvents.tryEmit(Unit)
                }
            }
        }

        fun registerServiceLifecycleReceiver() {
            val filter = IntentFilter(Action.SERVICE_STOPPED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                application.registerReceiver(
                    serviceStoppedReceiver,
                    filter,
                    Context.RECEIVER_NOT_EXPORTED,
                )
            } else {
                application.registerReceiver(serviceStoppedReceiver, filter)
            }
        }

        fun getClipboardText(): String {
            return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: ""
        }

        fun trySetPrimaryClip(clip: String) = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, clip))
            true
        } catch (e: RuntimeException) {
            Logs.w(e)
            false
        }

        fun startService() {
            application.applicationScope.launch {
                serviceLaunchMutex.withLock {
                    val recovery = runCatching {
                        LegacyRestoreRecovery.recoverPending(application)
                    }
                    if (recovery.isFailure) {
                        recovery.exceptionOrNull()?.let { error ->
                            Logs.e(
                                "Refusing to start service: pending restore recovery failed",
                                error,
                            )
                        }
                        return@withLock
                    }
                    when (val state = application.storageBootstrap.awaitReady()) {
                        StorageBootstrapState.Ready,
                        is StorageBootstrapState.LegacyFallback,
                        -> {
                            // Resolve serviceMode only after storage readiness.
                            // The service entrypoint has a second defensive
                            // gate for bind-auto-create and sticky redelivery.
                            ContextCompat.startForegroundService(
                                application,
                                Intent(application, SagerConnection.serviceClass),
                            )
                        }

                        is StorageBootstrapState.Failed ->
                            Logs.e("Refusing to start service: configuration storage is unavailable", state.cause)

                        StorageBootstrapState.NotStarted,
                        StorageBootstrapState.Starting,
                        -> Unit
                    }
                }
            }
        }

        suspend fun <T> withServiceLaunchLock(block: suspend () -> T): T {
            serviceLaunchMutex.lock()
            return try {
                block()
            } finally {
                serviceLaunchMutex.unlock()
            }
        }

        suspend fun awaitServiceStopped(timeoutMillis: Long) {
            withTimeout(timeoutMillis) {
                if (DataStore.serviceState == io.github.hhwkart.nami.bg.BaseService.State.Idle ||
                    DataStore.serviceState == io.github.hhwkart.nami.bg.BaseService.State.Stopped
                ) {
                    return@withTimeout
                }
                // BaseService emits this only after process cleanup and state
                // transition, so this is stronger than polling alone.
                serviceStoppedEvents.first()
            }
        }

        // main-process copy of the local inbound credentials, fetched over the
        // app's own Binder (never files/prefs); null when unknown — clients
        // then skip the local proxy (direct fallback), never anonymous-through
        @Volatile
        var localProxyAuth: Pair<String, String>? = null

        fun reloadService(forceConfigRebuild: Boolean = false) {
            val intent = Intent(Action.RELOAD).setPackage(application.packageName)
            if (forceConfigRebuild) intent.putExtra(Action.EXTRA_FORCE_CONFIG_REBUILD, true)
            application.sendBroadcast(intent)
        }

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

        var underlyingNetwork: Network? = null

        var appVersionNameForDisplay = {
            var n = BuildConfig.VERSION_NAME
            if (BuildConfig.PRE_VERSION_NAME.isNotBlank()) {
                n += " " + BuildConfig.PRE_VERSION_NAME
            }
            if (!isOss && !isPreview) {
                n += " ${BuildConfig.FLAVOR}"
            }
            if (BuildConfig.DEBUG) {
                n += " DEBUG"
            }
            n
        }()
    }

}
