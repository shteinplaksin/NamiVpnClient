package io.github.hhwkart.nami.ui.compose.routing

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.data.routing.GeositeAssetMatcher
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.RuleEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.database.preference.OnPreferenceDataStoreChangeListener
import androidx.preference.PreferenceDataStore
import io.github.hhwkart.nami.domain.routing.WebsiteBypassPolicy
import io.github.hhwkart.nami.domain.routing.WebsiteBypassRuleSnapshot
import io.github.hhwkart.nami.domain.routing.WebsiteBypassValidation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class RuleUi(
    val id: Long,
    val name: String,
    val summary: String,
    val outbound: String,
    val enabled: Boolean,
    val category: Int, // 0=domain 1=ip 2=geo/other
    val entity: RuleEntity,
)

data class RoutingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val allRuleCount: Int = 0,
    val filter: Int = -1, // -1 = all, 0=domain, 1=ip, 2=geo
    val domainRules: List<RuleUi> = emptyList(),
    val ipRules: List<RuleUi> = emptyList(),
    val geoRules: List<RuleUi> = emptyList(),
    val presetId: String = DataStore.routingPreset.orEmpty(),
    val presetApplying: Boolean = false,
    val websiteBypassEnabled: Boolean = DataStore.websiteBypassEnabled,
    val websiteBypassDomains: List<String> = websiteDomains(),
    val websiteBypassInput: String = "",
    val websiteBypassError: String? = null,
    val websiteBypassRequiresVpn: Boolean = DataStore.serviceMode != Key.MODE_VPN,
)

private fun websiteDomains(): List<String> = DataStore.websiteBypassDomains
    .orEmpty()
    .split('\n', ',', '\r')
    .map(String::trim)
    .filter(String::isNotBlank)
    .map { raw ->
        (WebsiteBypassPolicy.normalize(raw) as? WebsiteBypassValidation.Accepted)?.normalizedHost ?: raw
    }
    .distinct()

@HiltViewModel
class RoutingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RoutingUiState())
    val uiState: StateFlow<RoutingUiState> = _uiState.asStateFlow()

    private var allRules: List<RuleUi> = emptyList()
    private val pendingDeletes = ConcurrentHashMap<Long, Job>()
    private val preferenceListener = object : OnPreferenceDataStoreChangeListener {
        override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
            if (key == Key.SERVICE_MODE) {
                val requiresVpn = DataStore.serviceMode != Key.MODE_VPN
                _uiState.update {
                    it.copy(
                        websiteBypassRequiresVpn = requiresVpn,
                        websiteBypassError = if (requiresVpn) null else it.websiteBypassError,
                    )
                }
                reload()
            }
        }
    }

    init {
        DataStore.configurationStore.registerChangeListener(preferenceListener)
        reload()
        viewModelScope.launch(Dispatchers.IO) {
            ProfileManager.addListener(object : ProfileManager.RuleListener {
                override suspend fun onAdd(rule: RuleEntity) = reload()
                override suspend fun onUpdated(rule: RuleEntity) = reload()
                override suspend fun onRemoved(ruleId: Long) = reload()
                override suspend fun onCleared() = reload()
            })
        }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                ProfileManager.getRules().filterNot {
                    pendingDeletes.containsKey(it.id)
                }.map { r ->
                    RuleUi(
                        id = r.id,
                        name = r.displayName(),
                        summary = r.mkSummary(),
                        outbound = r.displayOutbound(),
                        enabled = r.enabled,
                        category = categoryOf(r),
                        entity = r,
                    )
                }
            }.onSuccess { loaded ->
                allRules = loaded
                applyFilter(_uiState.value.filter)
                val websiteDecision = if (DataStore.websiteBypassEnabled && DataStore.serviceMode == Key.MODE_VPN) {
                    WebsiteBypassPolicy.safeRuntimeHosts(
                        hosts = websiteDomains(),
                        rules = SagerDatabase.rulesDao.enabledRules().map(::websiteRuleSnapshot),
                        geositeMatcher = GeositeAssetMatcher,
                    )
                } else {
                    null
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = null,
                        allRuleCount = loaded.size,
                        presetId = DataStore.routingPreset.orEmpty(),
                        websiteBypassDomains = websiteDomains(),
                        websiteBypassError = websiteDecision?.rejected?.firstOrNull()?.let(::validationMessage),
                        websiteBypassRequiresVpn = DataStore.serviceMode != Key.MODE_VPN,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(loading = false, error = error.message ?: "Unable to load routing rules.")
                }
            }
        }
    }

    override fun onCleared() {
        DataStore.configurationStore.unregisterChangeListener(preferenceListener)
        super.onCleared()
    }

    fun setFilter(filter: Int) {
        applyFilter(filter)
    }

    fun updateWebsiteBypassInput(value: String) {
        _uiState.update { it.copy(websiteBypassInput = value, websiteBypassError = null) }
    }

    fun setWebsiteBypassEnabled(value: Boolean, onReloadRequired: () -> Unit = {}) {
        if (!value) {
            DataStore.websiteBypassEnabled = false
            _uiState.update { it.copy(websiteBypassEnabled = false, websiteBypassError = null) }
            onReloadRequired()
            return
        }
        if (DataStore.serviceMode != Key.MODE_VPN) {
            _uiState.update {
                it.copy(websiteBypassError = context.getString(R.string.website_bypass_requires_vpn))
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val decision = WebsiteBypassPolicy.safeRuntimeHosts(
                hosts = websiteDomains(),
                rules = SagerDatabase.rulesDao.enabledRules().map(::websiteRuleSnapshot),
                geositeMatcher = GeositeAssetMatcher,
            )
            withContext(Dispatchers.Main) {
                val rejection = decision.rejected.firstOrNull()
                if (rejection != null) {
                    _uiState.update {
                        it.copy(websiteBypassEnabled = false, websiteBypassError = validationMessage(rejection))
                    }
                } else {
                    DataStore.websiteBypassEnabled = true
                    _uiState.update { it.copy(websiteBypassEnabled = true, websiteBypassError = null) }
                    onReloadRequired()
                }
            }
        }
    }

    fun addWebsiteBypass(onReloadRequired: () -> Unit = {}) {
        val raw = _uiState.value.websiteBypassInput
        viewModelScope.launch(Dispatchers.IO) {
            val existingHosts = websiteDomains()
            val rules = SagerDatabase.rulesDao.enabledRules().map(::websiteRuleSnapshot)
            val result = WebsiteBypassPolicy.validate(
                raw = raw,
                existingHosts = existingHosts,
                rules = rules,
                geositeMatcher = GeositeAssetMatcher,
            )
            when (result) {
                is WebsiteBypassValidation.Accepted -> {
                    val updated = (existingHosts + result.normalizedHost).distinct()
                    DataStore.websiteBypassDomains = updated.joinToString("\n")
                    withContext(Dispatchers.Main) {
                        _uiState.update {
                            it.copy(
                                websiteBypassDomains = updated,
                                websiteBypassInput = "",
                                websiteBypassError = null,
                            )
                        }
                        onReloadRequired()
                    }
                }

                else -> {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(websiteBypassError = validationMessage(result)) }
                    }
                }
            }
        }
    }

    fun removeWebsiteBypass(host: String, onReloadRequired: () -> Unit = {}) {
        val updated = websiteDomains().filterNot { it.equals(host, ignoreCase = true) }
        DataStore.websiteBypassDomains = updated.joinToString("\n")
        _uiState.update { it.copy(websiteBypassDomains = updated, websiteBypassError = null) }
        onReloadRequired()
    }

    fun applyPreset(
        preset: RoutingPreset,
        onReloadRequired: () -> Unit = {},
        onFinished: (Result<Unit>) -> Unit = {},
    ) {
        if (_uiState.value.presetApplying) return
        _uiState.update { it.copy(presetApplying = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = RoutingPresetManager.apply(preset)
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        presetApplying = false,
                        presetId = if (result.isSuccess) preset.id else it.presetId,
                        error = result.exceptionOrNull()?.message,
                    )
                }
                if (result.isSuccess) {
                    reload()
                    onReloadRequired()
                }
                onFinished(result)
            }
        }
    }

    private fun applyFilter(filter: Int) {
        val visible = if (filter < 0) allRules else allRules.filter { it.category == filter }
        _uiState.update {
            it.copy(
                filter = filter,
                domainRules = visible.filter { r -> r.category == 0 },
                ipRules = visible.filter { r -> r.category == 1 },
                geoRules = visible.filter { r -> r.category == 2 },
            )
        }
    }

    fun setEnabled(rule: RuleUi, enabled: Boolean, onPersisted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            if (enabled && DataStore.websiteBypassEnabled && DataStore.serviceMode == Key.MODE_VPN) {
                val websiteConflict = WebsiteBypassPolicy.safeRuntimeHosts(
                    hosts = websiteDomains(),
                    rules = SagerDatabase.rulesDao.allRules().map {
                        websiteRuleSnapshot(it).copy(enabled = if (it.id == rule.id) enabled else it.enabled)
                    },
                    geositeMatcher = GeositeAssetMatcher,
                ).rejected.firstOrNull()
                if (websiteConflict != null) {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(error = validationMessage(websiteConflict)) }
                    }
                    return@launch
                }
            }
            rule.entity.enabled = enabled
            SagerDatabase.rulesDao.updateRule(rule.entity)
            RoutingPresetManager.markCustom()
            reload()
            withContext(Dispatchers.Main) { onPersisted() }
        }
    }

    fun stageDelete(rule: RuleUi, onPersisted: () -> Unit = {}) {
        if (pendingDeletes.containsKey(rule.id)) return
        allRules = allRules.filterNot { it.id == rule.id }
        applyFilter(_uiState.value.filter)
        pendingDeletes[rule.id] = viewModelScope.launch(Dispatchers.IO) {
            delay(PENDING_DELETE_MILLIS)
            val deleted = runCatching {
                ProfileManager.deleteRules(listOf(rule.entity))
                RoutingPresetManager.markCustom()
            }.isSuccess
            pendingDeletes.remove(rule.id)
            if (deleted) {
                withContext(Dispatchers.Main) { onPersisted() }
            } else {
                reload()
            }
        }
    }

    fun undoDelete(rule: RuleUi) {
        pendingDeletes.remove(rule.id)?.cancel()
        reload()
    }

    fun moveRule(rule: RuleUi, offset: Int, onPersisted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val rules = ProfileManager.getRules().toMutableList()
            val sameCategory = rules.filter { categoryOf(it) == rule.category }
            val from = sameCategory.indexOfFirst { it.id == rule.id }
            val to = from + offset
            if (from !in sameCategory.indices || to !in sameCategory.indices) return@launch
            val first = sameCategory[from]
            val second = sameCategory[to]
            val order = first.userOrder
            first.userOrder = second.userOrder
            second.userOrder = order
            SagerDatabase.rulesDao.updateRules(listOf(first, second))
            RoutingPresetManager.markCustom()
            reload()
            withContext(Dispatchers.Main) { onPersisted() }
        }
    }

    fun resetRules(onPersisted: () -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            SagerDatabase.rulesDao.reset()
            DataStore.rulesFirstCreate = false
            RoutingPresetManager.markCustom()
            reload()
            withContext(Dispatchers.Main) { onPersisted() }
        }
    }

    private fun categoryOf(rule: RuleEntity): Int = when {
        rule.domains.isNotBlank() -> 0
        rule.ip.isNotBlank() -> 1
        else -> 2
    }

    private fun websiteRuleSnapshot(rule: RuleEntity) = WebsiteBypassRuleSnapshot(
        name = rule.displayName(),
        domains = rule.domains,
        enabled = rule.enabled,
        outbound = rule.outbound,
    )

    private fun validationMessage(validation: WebsiteBypassValidation): String = when (validation) {
        is WebsiteBypassValidation.Accepted -> context.getString(
            R.string.website_bypass_added,
            validation.normalizedHost,
        )
        is WebsiteBypassValidation.Duplicate -> context.getString(
            R.string.website_bypass_duplicate,
            validation.host,
        )
        is WebsiteBypassValidation.Invalid -> validation.reason
        is WebsiteBypassValidation.Conflict -> context.getString(
            R.string.website_bypass_conflict,
            validation.host,
            validation.ruleName,
            validation.matcher,
        )
        is WebsiteBypassValidation.CannotVerify -> context.getString(
            R.string.website_bypass_cannot_verify,
            validation.host,
            validation.ruleName,
            validation.matcher,
        )
    }

    private companion object {
        const val PENDING_DELETE_MILLIS = 6_000L
    }
}
