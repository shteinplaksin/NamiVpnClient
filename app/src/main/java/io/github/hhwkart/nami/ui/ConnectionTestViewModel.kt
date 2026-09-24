package io.github.hhwkart.nami.ui

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.bg.proto.UrlTest
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.GroupManager
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.ktx.Logs
import io.github.hhwkart.nami.ktx.app
import io.github.hhwkart.nami.ktx.isIpAddress
import io.github.hhwkart.nami.ktx.readableMessage
import io.github.hhwkart.nami.plugin.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.internal.closeQuietly
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import io.github.hhwkart.nami.core.ui.ConnectionTestNotification
import io.github.hhwkart.nami.core.Protocols
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Runs group-wide connection tests (URL test / TCP ping) outside the Fragment
 * so that rotation / activity recreation never crashes and results are not
 * lost (issues #918, #886, #1141). All jobs are cancelled and results are
 * persisted in [onCleared]; no UI callbacks are held here.
 */
@HiltViewModel
class ConnectionTestViewModel @Inject constructor() : ViewModel() {

    var isRunning = false
        private set

    var groupId = -1L
        private set
    var groupTitle = ""
        private set
    var icmpPing = false
        private set
    var proxyN = 0
        private set

    val finishedN = AtomicInteger(0)
    val results: MutableSet<ProxyEntity> = ConcurrentHashMap.newKeySet()

    /** 0: running 1: minimized 2: cancelled */
    val status = AtomicInteger(0)

    /** per-profile progress for a live dialog; collected by the fragment */
    private val _updates = MutableSharedFlow<ProxyEntity>(extraBufferCapacity = 512)
    val updates = _updates.asSharedFlow()

    /** completed (or cancelled) test; fragment dismisses its dialog on it */
    private val _finished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val finished = _finished.asSharedFlow()

    var notification: ConnectionTestNotification? = null
    private val jobs = mutableListOf<Job>()

    fun minimize(context: android.content.Context) {
        status.compareAndSet(0, 1)
        if (notification == null) {
            notification = ConnectionTestNotification(
                app, "[$groupTitle] ${context.getString(R.string.connection_test)}"
            )
        }
    }

    fun cancel() {
        status.set(2)
        stopJobs(persist = true)
        _finished.tryEmit(Unit)
    }

    private fun stopJobs(persist: Boolean) {
        isRunning = false
        jobs.forEach { it.cancel() }
        jobs.clear()
        if (persist) persistResults()
        notification?.cancel()
        notification = null
    }

    private fun persistResults() {
        viewModelScope.launch(Dispatchers.Default) {
            results.forEach {
                try {
                    ProfileManager.updateProfile(it)
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }
            GroupManager.postReload(DataStore.currentGroupId())
        }
    }

    override fun onCleared() {
        stopJobs(persist = true)
        super.onCleared()
    }

    fun start(icmp: Boolean, groupId: Long, groupTitle: String) {
        if (isRunning) return
        isRunning = true
        icmpPing = icmp
        this.groupId = groupId
        this.groupTitle = groupTitle
        finishedN.set(0)
        results.clear()
        status.set(0)

        val profilesQueue = ConcurrentLinkedQueue<ProxyEntity>()
        val scope = viewModelScope

        scope.launch(Dispatchers.Default) {
            val profiles = SagerDatabase.proxyDao.getByGroup(groupId).filter {
                if (icmp) it.requireBean().canICMPing() else it.requireBean().canTCPing()
            }
            proxyN = profiles.size
            if (profiles.isEmpty()) {
                if (isActive) {
                    stopJobs(persist = false)
                    _finished.tryEmit(Unit)
                }
                return@launch
            }
            profilesQueue.addAll(profiles)

            val testWorkers = mutableListOf<Job>()
            repeat(DataStore.connectionTestConcurrent.coerceIn(1, 32)) {
                val worker = scope.launch(Dispatchers.IO) {
                    val urlTest = UrlTest() // note: NOT in bg process
                    while (isActive) {
                        val profile = profilesQueue.poll() ?: break
                        profile.status = 0

                        if (icmp) {
                            withTimeoutOrNull(5000L) {
                                tcpPing(profile)
                            } ?: run {
                                profile.status = 2
                                profile.error = app.getString(R.string.connection_test_timeout)
                            }
                        } else {
                            try {
                                val result = withTimeoutOrNull(5000L) {
                                    urlTest.doTest(profile)
                                }
                                if (result != null && result > 0) {
                                    profile.status = 1
                                    profile.ping = result
                                } else {
                                    profile.status = 2
                                    profile.error = app.getString(R.string.connection_test_timeout)
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: PluginManager.PluginNotFoundException) {
                                profile.status = 2
                                profile.error = e.readableMessage
                            } catch (e: Exception) {
                                profile.status = 3
                                profile.error = e.readableMessage
                            }
                        }

                        // persist immediately so a rotation never loses results
                        try {
                            ProfileManager.updateProfile(profile)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                        results.add(profile)
                        finishedN.incrementAndGet()
                        _updates.tryEmit(profile)
                    }
                }
                jobs.add(worker)
                testWorkers.add(worker)
            }

            withTimeoutOrNull(60_000L) {
                testWorkers.joinAll()
            }
            if (isActive) {
                isRunning = false
                jobs.clear()
                notification?.cancel()
                notification = null
                _finished.tryEmit(Unit)
            }
        }.also { jobs.add(it) }
    }

    private suspend fun tcpPing(profile: ProxyEntity) {
        var address = profile.requireBean().serverAddress
        if (!address.isIpAddress()) {
            try {
                SagerNet.underlyingNetwork!!.getAllByName(address).apply {
                    if (isNotEmpty()) {
                        address = this[0].hostAddress
                    }
                }
            } catch (ignored: UnknownHostException) {
            }
        }
        if (!address.isIpAddress()) {
            profile.status = 2
            profile.error = app.getString(R.string.connection_test_domain_not_found)
            return
        }
        val socket = SagerNet.underlyingNetwork?.socketFactory?.createSocket() ?: Socket()
        try {
            socket.soTimeout = 3000
            socket.bind(InetSocketAddress(0))
            val start = SystemClock.elapsedRealtime()
            socket.connect(InetSocketAddress(address, profile.requireBean().serverPort), 3000)
            profile.status = 1
            profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.readableMessage
            profile.status = 2
            when {
                !message.contains("failed:") -> profile.error =
                    app.getString(R.string.connection_test_timeout)

                message.contains("ECONNREFUSED") -> profile.error =
                    app.getString(R.string.connection_test_refused)

                message.contains("ENETUNREACH") -> profile.error =
                    app.getString(R.string.connection_test_unreachable)

                else -> {
                    profile.status = 3
                    profile.error = message
                }
            }
        } finally {
            socket.closeQuietly()
        }
    }
}
