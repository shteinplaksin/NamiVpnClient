package io.github.hhwkart.nami.ui.compose

import android.os.SystemClock
import io.github.hhwkart.nami.aidl.SpeedDisplayData
import io.github.hhwkart.nami.bg.BaseService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Bridges live service state from MainActivity (the only SagerConnection
 * holder) into Compose ViewModels. Fed on the main thread; collected by
 * screens. Session start time is elapsedRealtime of the Connected transition.
 */
object ConnectionBus {

    private val _state = MutableStateFlow(BaseService.State.Idle)
    val state: StateFlow<BaseService.State> = _state.asStateFlow()

    // Ephemeral credentials returned by the active service Binder; never persisted.
    private val _localProxyAuth = MutableStateFlow<Pair<String, String>?>(null)
    val localProxyAuth: StateFlow<Pair<String, String>?> = _localProxyAuth.asStateFlow()

    private val _speed = MutableStateFlow(SpeedDisplayData())
    val speed: StateFlow<SpeedDisplayData> = _speed.asStateFlow()

    private val _statusText = MutableStateFlow("")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    private val _connectedAt = MutableStateFlow(0L)
    val connectedAt: StateFlow<Long> = _connectedAt.asStateFlow()

    /** set by MainActivity; opens VPN permission flow or stops the service */
    var onConnectionToggle: (() -> Unit)? = null

    /** set by MainActivity; tests URL on the active VPN connection */
    var onTestUrl: (() -> Int)? = null

    fun toggleConnection() {
        onConnectionToggle?.invoke()
    }

    fun postState(state: BaseService.State, message: String? = null) {
        if (state != BaseService.State.Connected) _localProxyAuth.value = null
        _state.value = state
        if (message != null) _statusText.value = message
        if (state == BaseService.State.Connected) {
            if (_connectedAt.value == 0L) _connectedAt.value = SystemClock.elapsedRealtime()
        } else if (state == BaseService.State.Idle || state == BaseService.State.Stopped) {
            _connectedAt.value = 0L
            _speed.value = SpeedDisplayData()
        }
    }

    fun postLocalProxyAuth(credentials: Pair<String, String>?) {
        _localProxyAuth.value = credentials.takeIf { _state.value == BaseService.State.Connected }
    }

    fun postSpeed(stats: SpeedDisplayData) {
        _speed.value = stats
    }

    fun resetSessionClock() {
        _connectedAt.value = if (_state.value == BaseService.State.Connected)
            SystemClock.elapsedRealtime() else 0L
    }
}

sealed interface NavigationRequest {
    data class Open(val destination: Destination) : NavigationRequest
    data class ReturnTo(val destination: Destination) : NavigationRequest
    data class ReturnToPrevious(val screenCount: Int) : NavigationRequest
}

/** One-shot navigation requests from Android entry points and transient flows. */
object NavigationBus {
    // Navigation is an event, not durable UI state. A StateFlow could suppress a
    // repeated request for the same destination (for example Home after Quick
    // Setup), or clear a request before the NavController processed it.
    // Navigation requests are infrequent and small; an unbounded channel is
    // preferable to silently dropping a deep-link/startup request when the
    // NavHost is briefly busy processing another transition.
    private val _requests = Channel<NavigationRequest>(capacity = Channel.UNLIMITED)
    val requests: Flow<NavigationRequest> = _requests.receiveAsFlow()

    fun open(destination: Destination) {
        _requests.trySend(NavigationRequest.Open(destination))
    }

    fun returnTo(destination: Destination) {
        _requests.trySend(NavigationRequest.ReturnTo(destination))
    }

    fun returnToPrevious(screenCount: Int = 1) {
        _requests.trySend(NavigationRequest.ReturnToPrevious(screenCount.coerceAtLeast(1)))
    }
}
