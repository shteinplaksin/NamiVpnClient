package io.github.hhwkart.nami.ui.compose.routeeditor

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.BuildConfig
import io.github.hhwkart.nami.data.routing.GeositeAssetMatcher
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.RuleEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.ktx.readableMessage
import io.github.hhwkart.nami.ui.compose.routing.RoutingPresetManager
import io.github.hhwkart.nami.domain.routing.WebsiteBypassPolicy
import io.github.hhwkart.nami.domain.routing.WebsiteBypassRuleSnapshot
import io.github.hhwkart.nami.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class RouteProfileOption(
    val id: Long,
    val name: String,
    val type: String,
)

data class RouteEditorUiState(
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val ruleId: Long = 0L,
    val name: String = "",
    val config: String = "",
    val packages: Set<String> = emptySet(),
    val domains: String = "",
    val ip: String = "",
    val port: String = "",
    val source: String = "",
    val sourcePort: String = "",
    val network: String = "",
    val protocol: String = "",
    val outboundMode: Int = OUTBOUND_PROXY,
    val outboundProfileId: Long = 0L,
    val appPackages: List<String> = emptyList(),
    val appLabels: Map<String, String> = emptyMap(),
    val appSearch: String = "",
    val profiles: List<RouteProfileOption> = emptyList(),
    val error: String? = null,
    val showEmptyRuleWarning: Boolean = false,
    val showDiscardWarning: Boolean = false,
) {
    val hasCriteria: Boolean
        get() = config.isNotBlank() || packages.isNotEmpty() ||
            domains.isNotBlank() || ip.isNotBlank() || port.isNotBlank() ||
            source.isNotBlank() || sourcePort.isNotBlank() ||
            network.isNotBlank() || protocol.isNotBlank()

    companion object {
        const val OUTBOUND_PROXY = 0
        const val OUTBOUND_BYPASS = 1
        const val OUTBOUND_BLOCK = 2
        const val OUTBOUND_PROFILE = 3
    }
}

@HiltViewModel
class RouteEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    companion object {
        private const val DRAFT_KEY = "routeEditorDraft"
    }

    private val ruleId = savedStateHandle.get<Long>("ruleId") ?: 0L
    private val _uiState = MutableStateFlow(RouteEditorUiState(ruleId = ruleId))
    val uiState: StateFlow<RouteEditorUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { load() }.onFailure { throwable ->
                _uiState.update {
                    it.copy(loading = false, error = throwable.readableMessage)
                }
            }
        }
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        val entity = ruleId.takeIf { it != 0L }?.let { SagerDatabase.rulesDao.getById(it) }
            ?: if (ruleId == 0L) RuleEntity() else error("Route rule no longer exists")

        PackageCache.awaitLoadSync()
        val apps = PackageCache.installedPackages.keys
            .filter { it != BuildConfig.APPLICATION_ID }
            .sorted()
        val labels = apps.associateWith { packageName ->
            runCatching { PackageCache.loadLabel(packageName) }.getOrDefault(packageName)
        }
        val profiles = SagerDatabase.proxyDao.getAll()
            .filter { it.id != 0L }
            .map { RouteProfileOption(it.id, it.displayName(), it.displayType()) }
            .sortedBy { it.name.lowercase() }

        val loaded = RouteEditorUiState(
            loading = false,
            ruleId = ruleId,
            name = entity.name,
            config = entity.config,
            packages = entity.packages,
            domains = entity.domains,
            ip = entity.ip,
            port = entity.port,
            source = entity.source,
            sourcePort = entity.sourcePort,
            network = entity.network,
            protocol = entity.protocol,
            outboundMode = when (entity.outbound) {
                -1L -> RouteEditorUiState.OUTBOUND_BYPASS
                -2L -> RouteEditorUiState.OUTBOUND_BLOCK
                0L -> RouteEditorUiState.OUTBOUND_PROXY
                else -> RouteEditorUiState.OUTBOUND_PROFILE
            },
            outboundProfileId = entity.outbound.takeIf { it > 0 } ?: 0L,
            appPackages = apps,
            appLabels = labels,
            profiles = profiles,
        )
        _uiState.value = savedStateHandle.get<Bundle>(DRAFT_KEY)?.let { loaded.restoreDraft(it) }
            ?: loaded
    }

    fun updateName(value: String) = update { it.copy(name = value) }
    fun updateConfig(value: String) = update { it.copy(config = value) }
    fun updateDomains(value: String) = update { it.copy(domains = value) }
    fun updateIp(value: String) = update { it.copy(ip = value) }
    fun updatePort(value: String) = update { it.copy(port = value) }
    fun updateSource(value: String) = update { it.copy(source = value) }
    fun updateSourcePort(value: String) = update { it.copy(sourcePort = value) }
    fun updateNetwork(value: String) = update { it.copy(network = value) }
    fun updateProtocol(value: String) = update { it.copy(protocol = value) }

    fun updateAppSearch(value: String) {
        _uiState.update { it.copy(appSearch = value) }
    }

    fun togglePackage(packageName: String) {
        update { state ->
            val packages = state.packages.toMutableSet()
            if (!packages.add(packageName)) packages.remove(packageName)
            state.copy(packages = packages)
        }
    }

    fun loadAppLabel(packageName: String) {
        if (_uiState.value.appLabels.containsKey(packageName)) return
        viewModelScope.launch(Dispatchers.IO) {
            val label = runCatching { PackageCache.loadLabel(packageName) }
                .getOrDefault(packageName)
            _uiState.update { it.copy(appLabels = it.appLabels + (packageName to label)) }
        }
    }

    fun updateOutboundMode(mode: Int) {
        update { state ->
            state.copy(
                outboundMode = mode,
                outboundProfileId = if (mode == RouteEditorUiState.OUTBOUND_PROFILE) {
                    state.outboundProfileId
                } else {
                    0L
                },
            )
        }
    }

    fun updateOutboundProfile(profileId: Long) {
        update {
            it.copy(
                outboundMode = RouteEditorUiState.OUTBOUND_PROFILE,
                outboundProfileId = profileId,
            )
        }
    }

    fun requestBack() {
        if (_uiState.value.dirty) {
            _uiState.update { it.copy(showDiscardWarning = true) }
        } else {
            _uiState.update { it.copy(saved = true) }
        }
    }

    fun dismissDiscardWarning() {
        _uiState.update { it.copy(showDiscardWarning = false) }
    }

    fun discardChanges() {
        _uiState.update { it.copy(showDiscardWarning = false, saved = true) }
    }

    fun dismissEmptyRuleWarning() {
        _uiState.update { it.copy(showEmptyRuleWarning = false) }
    }

    fun save() {
        val state = _uiState.value
        if (state.loading || state.saving) return
        if (!state.hasCriteria || (state.outboundMode == RouteEditorUiState.OUTBOUND_PROFILE &&
                state.outboundProfileId == 0L)
        ) {
            _uiState.update { it.copy(showEmptyRuleWarning = true) }
            return
        }
        if (!state.dirty) {
            _uiState.update { it.copy(saved = true) }
            return
        }

        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val current = if (state.ruleId == 0L) {
                    RuleEntity()
                } else {
                    SagerDatabase.rulesDao.getById(state.ruleId)
                        ?: error("Route rule no longer exists")
                }
                current.apply {
                    name = state.name
                    config = state.config
                    packages = state.packages
                    domains = state.domains
                    ip = state.ip
                    port = state.port
                    source = state.source
                    sourcePort = state.sourcePort
                    network = state.network
                    protocol = state.protocol
                    outbound = when (state.outboundMode) {
                        RouteEditorUiState.OUTBOUND_BYPASS -> -1L
                        RouteEditorUiState.OUTBOUND_BLOCK -> -2L
                        RouteEditorUiState.OUTBOUND_PROFILE -> state.outboundProfileId
                        else -> 0L
                    }
                    if (state.ruleId == 0L) enabled = true
                }
                if (DataStore.websiteBypassEnabled && DataStore.serviceMode == io.github.hhwkart.nami.Key.MODE_VPN) {
                    val decision = WebsiteBypassPolicy.safeRuntimeHosts(
                        hosts = DataStore.websiteBypassDomains.orEmpty().split('\n', ',', '\r'),
                        rules = SagerDatabase.rulesDao.allRules()
                            .filterNot { it.id == current.id }
                            .plus(current)
                            .map {
                                WebsiteBypassRuleSnapshot(
                                    name = it.displayName(),
                                    domains = it.domains,
                                    enabled = it.enabled,
                                    outbound = it.outbound,
                                )
                            },
                        geositeMatcher = GeositeAssetMatcher,
                    )
                    decision.rejected.firstOrNull()?.let { rejected ->
                        error(WebsiteBypassPolicy.describe(rejected))
                    }
                }
                if (state.ruleId == 0L) ProfileManager.createRule(current)
                else ProfileManager.updateRule(current)
                RoutingPresetManager.markCustom()
            }.onSuccess {
                savedStateHandle.remove<Bundle>(DRAFT_KEY)
                _uiState.update { it.copy(saving = false, saved = true) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(saving = false, error = throwable.readableMessage)
                }
            }
        }
    }

    fun delete() {
        val state = _uiState.value
        if (state.ruleId == 0L || state.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ProfileManager.deleteRule(state.ruleId)
                RoutingPresetManager.markCustom()
            }.onSuccess {
                savedStateHandle.remove<Bundle>(DRAFT_KEY)
                _uiState.update { it.copy(saving = false, saved = true) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(saving = false, error = throwable.readableMessage)
                }
            }
        }
    }

    private fun update(transform: (RouteEditorUiState) -> RouteEditorUiState) {
        _uiState.update {
            transform(it).copy(dirty = true, error = null).also(::persistDraft)
        }
    }

    private fun persistDraft(state: RouteEditorUiState) {
        savedStateHandle[DRAFT_KEY] = Bundle().apply {
            putString("name", state.name)
            putString("config", state.config)
            putStringArrayList("packages", ArrayList(state.packages))
            putString("domains", state.domains)
            putString("ip", state.ip)
            putString("port", state.port)
            putString("source", state.source)
            putString("sourcePort", state.sourcePort)
            putString("network", state.network)
            putString("protocol", state.protocol)
            putInt("outboundMode", state.outboundMode)
            putLong("outboundProfileId", state.outboundProfileId)
        }
    }

    private fun RouteEditorUiState.restoreDraft(draft: Bundle) = copy(
        dirty = true,
        name = draft.getString("name", name),
        config = draft.getString("config", config),
        packages = draft.getStringArrayList("packages")?.toSet() ?: packages,
        domains = draft.getString("domains", domains),
        ip = draft.getString("ip", ip),
        port = draft.getString("port", port),
        source = draft.getString("source", source),
        sourcePort = draft.getString("sourcePort", sourcePort),
        network = draft.getString("network", network),
        protocol = draft.getString("protocol", protocol),
        outboundMode = draft.getInt("outboundMode", outboundMode),
        outboundProfileId = draft.getLong("outboundProfileId", outboundProfileId),
    )
}
