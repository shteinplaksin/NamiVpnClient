package io.github.hhwkart.nami.ui.compose.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.aidl.SpeedDisplayData
import io.github.hhwkart.nami.bg.BaseService
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.fmt.AbstractBean
import io.github.hhwkart.nami.ui.compose.ConnectionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

data class RecentProfileUi(
    val id: Long,
    val name: String,
    val type: String,
    val selected: Boolean,
)

data class HomeUiState(
    val state: BaseService.State = BaseService.State.Idle,
    val statusText: String = "",
    val profileName: String = "",
    val serverLocation: String = "",
    val sessionDuration: String = "",
    val speed: SpeedDisplayData = SpeedDisplayData(),
    val speedHistory: List<Pair<Long, Long>> = emptyList(),
    val recentProfiles: List<RecentProfileUi> = emptyList(),
    val isTesting: Boolean = false,
    val testResult: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    // ring buffer of 60 one-second samples, lives only in the VM
    private val history = ArrayDeque<Pair<Long, Long>>(61).apply {
        repeat(60) { addLast(0L to 0L) }
    }

    private val _uiState = MutableStateFlow(HomeUiState(speedHistory = history.toList()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ConnectionBus.state.collect { state ->
                updateProfileInfo()
                _uiState.update { it.copy(state = state, statusText = ConnectionBus.statusText.value) }
            }
        }
        viewModelScope.launch {
            ConnectionBus.speed.collect { speed ->
                _uiState.update { it.copy(speed = speed) }
            }
        }
        // 1 s tickers: session duration + selected profile changes + live speed sampling
        viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val connected = ConnectionBus.connectedAt.value
                val duration = if (connected > 0L) {
                    formatDuration(((SystemClock.elapsedRealtime() - connected) / 1000).toInt())
                } else ""
                val currentSpeed = ConnectionBus.speed.value
                val isConnected = ConnectionBus.state.value == BaseService.State.Connected
                val rx = if (isConnected) currentSpeed.rxRateProxy else 0L
                val tx = if (isConnected) currentSpeed.txRateProxy else 0L
                history.addLast(rx to tx)
                while (history.size > 60) history.removeFirst()
                _uiState.update {
                    it.copy(
                        sessionDuration = duration,
                        profileName = currentProfileName(),
                        serverLocation = currentServerLocation(),
                        speed = currentSpeed,
                        speedHistory = history.toList(),
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                _uiState.update { it.copy(recentProfiles = loadRecentProfiles()) }
                delay(5000)
            }
        }
    }

    private fun currentProfileName(): String {
        val id = DataStore.currentProfile.takeIf { it > 0L } ?: DataStore.selectedProxy
        if (id <= 0L) return ""
        return try {
            ProfileManager.getProfile(id)?.displayName() ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun currentServerLocation(): String {
        val id = DataStore.currentProfile.takeIf { it > 0L } ?: DataStore.selectedProxy
        if (id <= 0L) return ""
        return try {
            val bean: AbstractBean? = ProfileManager.getProfile(id)?.requireBean()
            bean?.serverAddress?.takeIf { it.isNotBlank() } ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private fun updateProfileInfo() {
        _uiState.update {
            it.copy(
                profileName = currentProfileName(),
                serverLocation = currentServerLocation(),
            )
        }
    }

    private fun loadRecentProfiles(): List<RecentProfileUi> {
        return try {
            val profiles = SagerDatabase.proxyDao.getAll()
            val selected = DataStore.selectedProxy
            profiles.map {
                RecentProfileUi(it.id, it.displayName() ?: "", it.displayType(), it.id == selected)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun testConnection() {
        if (_uiState.value.isTesting) return
        val isConnected = ConnectionBus.state.value == BaseService.State.Connected
        val currentProfileId = DataStore.currentProfile.takeIf { it > 0L } ?: DataStore.selectedProxy
        if (!isConnected && currentProfileId <= 0L) {
            _uiState.update { it.copy(testResult = "No profile") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isTesting = true, testResult = null) }
            val result = withTimeoutOrNull(6000L) {
                try {
                    val livePing = if (isConnected) ConnectionBus.onTestUrl?.invoke() else null
                    if (livePing != null && livePing > 0) {
                        "$livePing ms"
                    } else if (currentProfileId > 0L) {
                        val profile = ProfileManager.getProfile(currentProfileId)
                        if (profile == null) {
                            "No profile"
                        } else {
                            val ping = io.github.hhwkart.nami.bg.proto.UrlTest().doTest(profile)
                            if (ping > 0) "$ping ms" else "Failed"
                        }
                    } else {
                        "Failed"
                    }
                } catch (e: Exception) {
                    e.message ?: "Error"
                }
            } ?: "Timeout"
            _uiState.update { it.copy(isTesting = false, testResult = result) }
        }
    }

    fun onToggleConnection() = ConnectionBus.toggleConnection()

    fun onSelectProfile(profileId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (DataStore.selectedProxy != profileId) {
                DataStore.selectedProxy = profileId
                DataStore.currentProfile = profileId
                if (ConnectionBus.state.value.canStop) SagerNet.reloadService()
            } else {
                ConnectionBus.toggleConnection()
            }
            _uiState.update {
                it.copy(
                    profileName = currentProfileName(),
                    serverLocation = currentServerLocation(),
                    recentProfiles = loadRecentProfiles(),
                )
            }
        }
    }

    fun statusLabel(state: BaseService.State): Int = when (state) {
        BaseService.State.Connected -> R.string.vpn_connected
        BaseService.State.Connecting -> R.string.connecting
        BaseService.State.Stopping -> R.string.stopping
        else -> R.string.not_connected
    }

    companion object {
        fun formatDuration(seconds: Int): String {
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return if (h > 0) "%d:%02d:%02d".format(h, m, s)
            else "%d:%02d".format(m, s)
        }

        fun groupDisplayName(group: ProxyGroup) = group.displayName()
    }
}
