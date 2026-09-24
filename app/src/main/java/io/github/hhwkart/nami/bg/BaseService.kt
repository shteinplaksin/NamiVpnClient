package io.github.hhwkart.nami.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.widget.Toast
import io.github.hhwkart.nami.Action
import io.github.hhwkart.nami.BootReceiver
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.aidl.ISagerNetService
import io.github.hhwkart.nami.aidl.ISagerNetServiceCallback
import io.github.hhwkart.nami.bg.proto.ProxyInstance
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.ktx.*
import io.github.hhwkart.nami.plugin.PluginManager
import io.github.hhwkart.nami.utils.DefaultNetworkListener
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Libcore
import io.github.hhwkart.nami.core.Protocols
import io.github.hhwkart.nami.core.utils.Util
import java.net.UnknownHostException

class BaseService {

    enum class State(
        val canStop: Boolean = false,
        val started: Boolean = false,
        val connected: Boolean = false,
    ) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle, Connecting(true, true, false), Connected(true, true, true), Stopping, Stopped,
    }

    interface ExpectedException

    class Data internal constructor(private val service: Interface) {
        var state = State.Stopped
        var proxy: ProxyInstance? = null
        var notification: ServiceNotification? = null

        // Storage readiness may outlive the framework callback that requested
        // the service. Keep the wait tied to this service instance so destroy
        // cancels it instead of leaving a process-global coroutine behind.
        val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        val receiver = broadcastReceiver { ctx, intent ->
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> service.persistStats()
                Action.RELOAD -> service.reload(
                    forceConfigRebuild = intent.getBooleanExtra(Action.EXTRA_FORCE_CONFIG_REBUILD, false),
                )
                // Action.SWITCH_WAKE_LOCK -> runOnDefaultDispatcher { service.switchWakeLock() }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (SagerNet.power.isDeviceIdleMode) {
                            proxy?.box?.sleep()
                        } else {
                            proxy?.box?.wake()
                            if (DataStore.wakeResetConnections) {
                                Libcore.resetAllConnections(true)
                            }
                        }
                    }
                }

                Action.RESET_UPSTREAM_CONNECTIONS -> runOnDefaultDispatcher {
                    Libcore.resetAllConnections(true)
                    runOnMainDispatcher {
                        Util.collapseStatusBar(ctx)
                        Toast.makeText(ctx, "Reset upstream connections done", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                else -> service.stopRunner()
            }
        }
        var closeReceiverRegistered = false

        val binder = Binder(this)
        var connectingJob: Job? = null

        fun changeState(s: State, msg: String? = null) {
            if (state == s && msg == null) return
            state = s
            DataStore.serviceState = s
            binder.stateChanged(s, msg)
        }

        fun close() {
            startupScope.cancel()
            binder.close()
        }
    }

    class Binder(private var data: Data? = null) : ISagerNetService.Stub(), CoroutineScope,
        AutoCloseable {
        private val callbacks = object : RemoteCallbackList<ISagerNetServiceCallback>() {
            override fun onCallbackDied(callback: ISagerNetServiceCallback?, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
            }
        }

        val callbackIdMap = mutableMapOf<ISagerNetServiceCallback, Int>()

        override val coroutineContext = Dispatchers.Main.immediate + Job()

        override fun getState(): Int = (data?.state ?: State.Idle).ordinal
        override fun getProfileName(): String = data?.proxy?.displayProfileName ?: "Idle"

        override fun registerCallback(cb: ISagerNetServiceCallback, id: Int) {
            if (id == SagerConnection.CONNECTION_ID_RESTART_BG) {
                Runtime.getRuntime().exit(0)
                return
            }
            if (!callbackIdMap.contains(cb)) {
                callbacks.register(cb)
            }
            callbackIdMap[cb] = id
        }

        private val broadcastMutex = Mutex()

        suspend fun broadcast(work: (ISagerNetServiceCallback) -> Unit) {
            broadcastMutex.withLock {
                val count = callbacks.beginBroadcast()
                try {
                    repeat(count) {
                        try {
                            work(callbacks.getBroadcastItem(it))
                        } catch (_: RemoteException) {
                        } catch (_: Exception) {
                        }
                    }
                } finally {
                    callbacks.finishBroadcast()
                }
            }
        }

        override fun unregisterCallback(cb: ISagerNetServiceCallback) {
            callbackIdMap.remove(cb)
            callbacks.unregister(cb)
        }

        override fun urlTest(): Int {
            if (data?.proxy?.box == null) {
                error("core not started")
            }
            try {
                return Libcore.urlTest(
                    data!!.proxy!!.box, DataStore.connectionTestURL, 3000
                )
            } catch (e: Exception) {
                error(Protocols.genFriendlyMsg(e.readableMessage))
            }
        }

        // R5: credentials cross the process boundary only via this app's own
        // Binder (signature-protected service); never files / prefs.
        override fun getProxyAuthUser(): String = ProxyAuth.username
        override fun getProxyAuthPass(): String = ProxyAuth.password

        fun stateChanged(s: State, msg: String?) = launch {
            val profileName = profileName
            broadcast { it.stateChanged(s.ordinal, profileName, msg) }
        }

        fun missingPlugin(pluginName: String) = launch {
            val profileName = profileName
            broadcast { it.missingPlugin(profileName, pluginName) }
        }

        override fun close() {
            callbacks.kill()
            cancel()
            data = null
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? =
            if (intent.action == Action.SERVICE) data.binder else null

        fun reload(forceConfigRebuild: Boolean = false) {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, (this as Context).getString(R.string.profile_empty))
            }
            if (BaseServiceReloadPolicy.canUseSelectorFastPath(forceConfigRebuild) { canReloadSelector() }) {
                val ent = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                val tag = data.proxy!!.config.profileTagMap[ent?.id] ?: ""
                if (tag.isNotBlank() && ent != null) {
                    // select from GUI
                    data.proxy!!.box.selectOutbound(tag)
                    // or select from webui
                    // => selector_OnProxySelected
                }
                return
            }
            val s = data.state
            when {
                s == State.Stopped -> startRunner()
                s.canStop -> stopRunner(true)
                else -> Logs.w("Illegal state $s when invoking use")
            }
        }

        fun canReloadSelector(): Boolean {
            if ((data.proxy?.config?.selectorGroupId ?: -1L) < 0) return false
            val ent = SagerDatabase.proxyDao.getById(DataStore.selectedProxy) ?: return false
            val tmpBox = ProxyInstance(ent)
            tmpBox.buildConfigTmp()
            if (tmpBox.lastSelectorGroupId == data.proxy?.lastSelectorGroupId) {
                return true
            }
            return false
        }

        suspend fun startProcesses() {
            data.proxy!!.launch()
        }

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, javaClass))
            else startService(Intent(this, javaClass))
        }

        fun killProcesses() {
            data.proxy?.close()
            wakeLock?.apply {
                release()
                wakeLock = null
            }
            runOnDefaultDispatcher {
                DefaultNetworkListener.stop(this)
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            DataStore.baseService = null
            DataStore.vpnService = null

            if (data.state == State.Stopping) return
            data.notification?.destroy()
            data.notification = null
            this as Service

            data.changeState(State.Stopping)

            runOnMainDispatcher {
                data.connectingJob?.cancelAndJoin() // ensure stop connecting first
                // we use a coroutineScope here to allow clean-up in parallel
                coroutineScope {
                    killProcesses()
                    val data = data
                    if (data.closeReceiverRegistered) {
                        unregisterReceiver(data.receiver)
                        data.closeReceiverRegistered = false
                    }
                    data.proxy = null
                }

                // change the state
                data.changeState(State.Stopped, msg)
                // A stopped state is published only after proxy processes,
                // receivers, and service-owned cleanup have completed. The
                // main process uses this acknowledgement before logical
                // backup/restore mutation.
                sendBroadcast(Intent(Action.SERVICE_STOPPED).setPackage(packageName))
                // stop the service if nothing has bound to it
                if (restart) startRunner() else {
                    stopSelf()
                }
            }
        }

        fun persistStats() {
            // TODO NEW save app stats?
        }

        // networks
        var upstreamInterfaceName: String?

        suspend fun preInit() {
            DefaultNetworkListener.start(this) {
                SagerNet.connectivity.getLinkProperties(it)?.also { link ->
                    SagerNet.underlyingNetwork = it
                    DataStore.vpnService?.updateUnderlyingNetwork()
                    //
                    val oldName = upstreamInterfaceName
                    if (oldName != link.interfaceName) {
                        upstreamInterfaceName = link.interfaceName
                    }
                    if (oldName != null && upstreamInterfaceName != null && oldName != upstreamInterfaceName) {
                        Logs.d("Network changed: $oldName -> $upstreamInterfaceName")
                        if (DataStore.networkChangeResetConnections) {
                            Libcore.resetAllConnections(true)
                        }
                    }
                }
            }
        }

        var wakeLock: PowerManager.WakeLock?
        fun acquireWakeLock()

        suspend fun lateInit() {
            wakeLock?.apply {
                release()
                wakeLock = null
            }

            if (DataStore.acquireWakeLock) {
                acquireWakeLock()
                data.notification?.postNotificationWakeLockStatus(true)
            } else {
                data.notification?.postNotificationWakeLockStatus(false)
            }
        }

        fun awaitStorageReadyThen(
            intent: Intent?,
            flags: Int,
            startId: Int,
            onReady: () -> Unit,
        ): Int {
            data.startupScope.launch {
                when (val state = SagerNet.application.storageBootstrap.awaitReady()) {
                    io.github.hhwkart.nami.data.settings.StorageBootstrapState.Ready,
                    is io.github.hhwkart.nami.data.settings.StorageBootstrapState.LegacyFallback,
                    -> withContext(Dispatchers.Main.immediate) {
                        onReady()
                    }

                    is io.github.hhwkart.nami.data.settings.StorageBootstrapState.Failed ->
                        withContext(Dispatchers.Main.immediate) {
                            Logs.e(
                                "Refusing service startup: configuration storage is unavailable",
                                state.cause,
                            )
                            (this@Interface as Service).stopSelfResult(startId)
                        }

                    io.github.hhwkart.nami.data.settings.StorageBootstrapState.NotStarted,
                    io.github.hhwkart.nami.data.settings.StorageBootstrapState.Starting,
                    -> error("Storage bootstrap did not reach a terminal state")
                }
            }
            // Keep the service sticky while the asynchronous readiness gate is
            // running; the body below is entered only from onReady().
            return Service.START_STICKY
        }

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
            awaitStorageReadyThen(intent, flags, startId) {
                startAfterStorageReady(intent, flags, startId)
            }

        fun startAfterStorageReady(intent: Intent?, flags: Int, startId: Int) {
            DataStore.baseService = this

            val data = data
            if (data.state != State.Stopped) return
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
            this as Context
            if (profile == null) { // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return
            }

            val proxy = ProxyInstance(profile, this)
            data.proxy = proxy
            BootReceiver.enabled = DataStore.persistAcrossReboot
            if (!data.closeReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Action.RELOAD)
                    addAction(Intent.ACTION_SHUTDOWN)
                    addAction(Action.CLOSE)
                    // addAction(Action.SWITCH_WAKE_LOCK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                    addAction(Action.RESET_UPSTREAM_CONNECTIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null,
                        Context.RECEIVER_EXPORTED
                    )
                } else {
                    registerReceiver(
                        data.receiver,
                        filter,
                        "$packageName.SERVICE",
                        null
                    )
                }
                data.closeReceiverRegistered = true
            }

            data.changeState(State.Connecting)
            runOnMainDispatcher {
                try {
                    // R2/R4: fresh random credentials per service start;
                    // startup aborts (fail closed) if generation fails.
                    if (!ProxyAuth.generate()) {
                        stopRunner(false, "failed to generate local proxy credentials")
                        return@runOnMainDispatcher
                    }
                    data.notification = createNotification(ServiceNotification.genTitle(profile))

                    Executable.killAll()    // clean up old processes
                    preInit()
                    proxy.init()
                    DataStore.currentProfile = profile.id

                    proxy.processes = GuardedProcessPool {
                        Logs.w(it)
                        stopRunner(false, it.readableMessage)
                    }

                    startProcesses()
                    data.changeState(State.Connected)

                    lateInit()
                } catch (_: CancellationException) { // if the job was cancelled, it is canceller's responsibility to call stopRunner
                } catch (_: UnknownHostException) {
                    stopRunner(false, getString(R.string.invalid_server))
                } catch (e: PluginManager.PluginNotFoundException) {
                    Toast.makeText(this@Interface, e.readableMessage, Toast.LENGTH_SHORT).show()
                    Logs.w(e)
                    data.binder.missingPlugin(e.plugin)
                    stopRunner(false, null)
                } catch (exc: Throwable) {
                    if (exc.javaClass.name.endsWith("proxyerror")) {
                        // error from golang
                        Logs.w(exc.readableMessage)
                    } else {
                        Logs.w(exc)
                    }
                    stopRunner(
                        false, "${getString(R.string.service_failed)}: ${exc.readableMessage}"
                    )
                } finally {
                    data.connectingJob = null
                }
            }
            // sticky restart: after process death the system redelivers a null
            // intent; onStartCommand re-reads DataStore.selectedProxy and
            // reconnects cleanly (issues #280, #1020).
        }
    }

}
