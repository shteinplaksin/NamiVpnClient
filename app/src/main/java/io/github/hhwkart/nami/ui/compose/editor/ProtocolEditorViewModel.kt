package io.github.hhwkart.nami.ui.compose.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.bg.proto.UrlTest
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.fmt.AbstractBean
import io.github.hhwkart.nami.fmt.internal.ChainBean
import io.github.hhwkart.nami.ktx.readableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ProtocolEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val routeProfileId = savedStateHandle.get<Long>("profileId") ?: 0L
    private val routeProtocol = ProtocolKind.fromRouteKey(
        savedStateHandle.get<String>(STATE_PROTOCOL)
            ?: savedStateHandle.get<String>("protocolType")
    )

    private val _uiState = MutableStateFlow(
        ProtocolEditorUiState(
            profileId = routeProfileId,
            protocol = routeProtocol,
            isNew = routeProfileId == 0L,
        )
    )
    val uiState: StateFlow<ProtocolEditorUiState> = _uiState.asStateFlow()

    private var baselineBean: AbstractBean? = null
    private var baselineForm: Map<String, String> = emptyMap()
    private var selectionGeneration = 0

    init {
        viewModelScope.launch {
            runCatching { load() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            loading = false,
                            message = error.readableMessage,
                        )
                    }
                }
        }
    }

    private suspend fun load() = withContext(Dispatchers.IO) {
        val entity = routeProfileId.takeIf { it != 0L }?.let(ProfileManager::getProfile)
        val protocol = entity?.let(ProtocolKind::fromEntity) ?: routeProtocol
        val groupId = entity?.groupId
            ?: savedStateHandle.get<Long>("groupId")?.takeIf { it > 0L }
            ?: DataStore.selectedGroupForImport()
        val bean = entity?.requireBean()?.clone() ?: ProtocolEditorCatalog.createBean(protocol)
        baselineBean = bean
        val initialForm = ProtocolEditorCatalog.beanToForm(protocol, bean)
        baselineForm = initialForm
        val restoredForm = savedStateHandle.get<HashMap<String, String>>(STATE_FORM)
        val form = restoredForm?.toMap()?.takeIf { it.isNotEmpty() } ?: initialForm
        val errors = ProtocolEditorCatalog.validate(protocol, form)
        val candidates = if (protocol == ProtocolKind.CHAIN) {
            loadChainCandidates(entity, form)
        } else {
            emptyList()
        }
        _uiState.value = ProtocolEditorUiState(
            loading = false,
            profileId = routeProfileId,
            groupId = groupId,
            protocol = protocol,
            isNew = entity == null,
            form = form,
            errors = errors,
            isValid = errors.isEmpty(),
            chainCandidates = candidates,
            dirty = savedStateHandle.get<Boolean>(STATE_DIRTY) ?: false,
        )
    }

    fun updateField(key: String, value: String) {
        _uiState.update { state ->
            val form = state.form + (key to value)
            val errors = ProtocolEditorCatalog.validate(state.protocol, form)
            val dirty = form != baselineForm
            persist(form, state.protocol, dirty)
            state.copy(
                form = form,
                errors = errors,
                isValid = errors.isEmpty(),
                message = null,
                dirty = dirty,
            )
        }
    }

    fun selectProtocol(protocol: ProtocolKind) {
        val state = _uiState.value
        if (!state.isNew || state.protocol == protocol) return
        val generation = ++selectionGeneration
        _uiState.update { it.copy(loading = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val bean = ProtocolEditorCatalog.createBean(protocol)
            if (generation != selectionGeneration) return@launch
            baselineBean = bean
            val form = ProtocolEditorCatalog.beanToForm(protocol, bean)
            val errors = ProtocolEditorCatalog.validate(protocol, form)
            val candidates = if (protocol == ProtocolKind.CHAIN) {
                loadChainCandidates(null, form)
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    protocol = protocol,
                    form = form,
                    errors = errors,
                    isValid = errors.isEmpty(),
                    chainCandidates = candidates,
                    message = null,
                    loading = false,
                    dirty = true,
                )
            }
            persist(form, protocol, true)
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.isValid || state.isSaving) return
        _uiState.update { it.copy(isSaving = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bean = buildBean(state)
                if (state.isNew) {
                    ProfileManager.createProfile(state.groupId, bean)
                } else {
                    val entity = ProfileManager.getProfile(state.profileId)
                        ?: error("Profile no longer exists")
                    if (entity.id == DataStore.selectedProxy) SagerNet.stopService()
                    entity.putBean(bean)
                    ProfileManager.updateProfile(entity)
                }
            }.onSuccess {
                savedStateHandle.remove<HashMap<String, String>>(STATE_FORM)
                savedStateHandle.remove<String>(STATE_PROTOCOL)
                savedStateHandle.remove<Boolean>(STATE_DIRTY)
                _uiState.update { it.copy(isSaving = false, saved = true, dirty = false) }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isSaving = false, message = error.readableMessage)
                }
            }
        }
    }

    fun test() {
        val state = _uiState.value
        if (!state.isValid || state.isTesting || !state.protocol.hasEndpoint) return
        _uiState.update { it.copy(isTesting = true, message = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val bean = buildBean(state)
                val temporary = ProxyEntity(groupId = state.groupId).putBean(bean)
                UrlTest().doTest(temporary)
            }.onSuccess { delayMs ->
                _uiState.update {
                    it.copy(isTesting = false, message = "Connection test: ${delayMs} ms")
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(isTesting = false, message = error.readableMessage)
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun formatJson(key: String) {
        val raw = _uiState.value.form[key].orEmpty()
        if (raw.isBlank()) return
        runCatching { org.json.JSONObject(raw).toString(2) }
            .onSuccess { updateField(key, it) }
            .onFailure { error ->
                _uiState.update { it.copy(message = error.readableMessage) }
            }
    }

    fun toggleChain(id: Long) {
        val state = _uiState.value
        if (state.protocol != ProtocolKind.CHAIN) return
        val ids = selectedChainIds(state).toMutableList()
        if (!ids.remove(id)) ids.add(id)
        updateChain(ids)
    }

    fun moveChain(from: Int, to: Int) {
        val state = _uiState.value
        if (state.protocol != ProtocolKind.CHAIN) return
        val selected = state.chainCandidates.filter { it.selected }
        if (from !in selected.indices || to !in selected.indices) return
        val ids = selected.map { it.id }.toMutableList()
        val moved = ids.removeAt(from)
        ids.add(to, moved)
        updateChain(ids)
    }

    private fun updateChain(ids: List<Long>) {
        _uiState.update { state ->
            val byId = state.chainCandidates.associateBy { it.id }
            val ordered = ids.mapNotNull(byId::get).map { it.copy(selected = true) }
            val remaining = state.chainCandidates
                .filter { it.id !in ids }
                .map { it.copy(selected = false) }
            val form = state.form + (FieldKeys.CHAIN_PROXIES to ids.joinToString(","))
            val errors = ProtocolEditorCatalog.validate(state.protocol, form)
            state.copy(
                form = form,
                errors = errors,
                isValid = errors.isEmpty(),
                chainCandidates = ordered + remaining,
                dirty = true,
            )
        }
        val updated = _uiState.value
        persist(updated.form, updated.protocol, true)
    }

    private fun selectedChainIds(state: ProtocolEditorUiState): List<Long> =
        state.form[FieldKeys.CHAIN_PROXIES]
            .orEmpty()
            .split(',')
            .mapNotNull(String::toLongOrNull)

    private fun buildBean(state: ProtocolEditorUiState): AbstractBean {
        val base = baselineBean?.clone() ?: ProtocolEditorCatalog.createBean(state.protocol)
        return ProtocolEditorCatalog.applyForm(state.protocol, base, state.form)
    }

    private fun loadChainCandidates(
        entity: ProxyEntity?,
        form: Map<String, String>,
    ): List<ChainCandidate> {
        val selected = form[FieldKeys.CHAIN_PROXIES]
            .orEmpty()
            .split(',')
            .mapNotNull(String::toLongOrNull)
        val allowed = SagerDatabase.proxyDao.getAll().filter { candidate ->
            candidate.id != entity?.id && !containsChain(candidate, entity?.id ?: 0L, hashSetOf())
        }
        val byId = allowed.associateBy { it.id }
        val selectedCandidates = selected.mapNotNull(byId::get).map { candidate ->
            ChainCandidate(candidate.id, candidate.displayName(), candidate.displayType(), true)
        }
        val remaining = allowed.filter { it.id !in selected }.map { candidate ->
            ChainCandidate(candidate.id, candidate.displayName(), candidate.displayType(), false)
        }
        return selectedCandidates + remaining
    }

    private fun containsChain(
        candidate: ProxyEntity,
        targetId: Long,
        visited: MutableSet<Long>,
    ): Boolean {
        if (targetId == 0L || candidate.type != ProxyEntity.TYPE_CHAIN) return false
        if (!visited.add(candidate.id)) return false
        val ids = (candidate.requireBean() as ChainBean).proxies.orEmpty()
        if (targetId in ids) return true
        return ProfileManager.getProfiles(ids).any { containsChain(it, targetId, visited) }
    }

    private fun persist(form: Map<String, String>, protocol: ProtocolKind, dirty: Boolean) {
        savedStateHandle[STATE_FORM] = HashMap(form)
        savedStateHandle[STATE_PROTOCOL] = protocol.routeKey
        savedStateHandle[STATE_DIRTY] = dirty
    }

    private companion object {
        const val STATE_FORM = "protocolEditor.form"
        const val STATE_PROTOCOL = "protocolEditor.protocol"
        const val STATE_DIRTY = "protocolEditor.dirty"
    }
}
