package io.github.hhwkart.nami.ui.compose.groupeditor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.GroupOrder
import io.github.hhwkart.nami.GroupType
import io.github.hhwkart.nami.database.GroupManager
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.database.SubscriptionBean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupProfileChoice(
    val id: Long,
    val title: String,
    val type: String,
)

data class GroupEditorState(
    val loading: Boolean = true,
    val dirty: Boolean = false,
    val groupId: Long = 0L,
    val name: String = "",
    val type: Int = GroupType.BASIC,
    val order: Int = GroupOrder.ORIGIN,
    val isSelector: Boolean = false,
    val frontProxy: Long = -1L,
    val landingProxy: Long = -1L,
    val subscriptionLink: String = "",
    val subscriptionForceResolve: Boolean = false,
    val subscriptionDeduplication: Boolean = false,
    val subscriptionUpdateWhenConnectedOnly: Boolean = false,
    val subscriptionUserAgent: String = "",
    val subscriptionAutoUpdate: Boolean = false,
    val subscriptionAutoUpdateDelay: String = "1440",
    val profiles: List<GroupProfileChoice> = emptyList(),
    val errors: Map<String, String> = emptyMap(),
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val entityMissing: Boolean = false,
    val showDiscardWarning: Boolean = false,
)

@HiltViewModel
class GroupEditorViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private class MissingGroupException : IllegalStateException("Group no longer exists")

    companion object {
        const val GROUP_ID_KEY = "groupId"
        const val NO_PROXY = -1L

        private const val DRAFT_GROUP_ID = "groupEditor.draft.groupId"
        private const val DRAFT_NAME = "groupEditor.draft.name"
        private const val DRAFT_TYPE = "groupEditor.draft.type"
        private const val DRAFT_ORDER = "groupEditor.draft.order"
        private const val DRAFT_SELECTOR = "groupEditor.draft.selector"
        private const val DRAFT_FRONT_PROXY = "groupEditor.draft.frontProxy"
        private const val DRAFT_LANDING_PROXY = "groupEditor.draft.landingProxy"
        private const val DRAFT_SUBSCRIPTION_LINK = "groupEditor.draft.subscriptionLink"
        private const val DRAFT_FORCE_RESOLVE = "groupEditor.draft.forceResolve"
        private const val DRAFT_DEDUPLICATION = "groupEditor.draft.deduplication"
        private const val DRAFT_UPDATE_CONNECTED_ONLY = "groupEditor.draft.updateConnectedOnly"
        private const val DRAFT_USER_AGENT = "groupEditor.draft.userAgent"
        private const val DRAFT_AUTO_UPDATE = "groupEditor.draft.autoUpdate"
        private const val DRAFT_AUTO_UPDATE_DELAY = "groupEditor.draft.autoUpdateDelay"
    }

    private val groupId = savedStateHandle[GROUP_ID_KEY] ?: 0L

    private fun restoredDraft(): GroupEditorState? {
        if (savedStateHandle.get<Long>(DRAFT_GROUP_ID) != groupId) return null
        return GroupEditorState(
            loading = true,
            dirty = true,
            groupId = groupId,
            name = savedStateHandle[DRAFT_NAME] ?: "",
            type = savedStateHandle[DRAFT_TYPE] ?: GroupType.BASIC,
            order = savedStateHandle[DRAFT_ORDER] ?: GroupOrder.ORIGIN,
            isSelector = savedStateHandle[DRAFT_SELECTOR] ?: false,
            frontProxy = savedStateHandle[DRAFT_FRONT_PROXY] ?: NO_PROXY,
            landingProxy = savedStateHandle[DRAFT_LANDING_PROXY] ?: NO_PROXY,
            subscriptionLink = savedStateHandle[DRAFT_SUBSCRIPTION_LINK] ?: "",
            subscriptionForceResolve = savedStateHandle[DRAFT_FORCE_RESOLVE] ?: false,
            subscriptionDeduplication = savedStateHandle[DRAFT_DEDUPLICATION] ?: false,
            subscriptionUpdateWhenConnectedOnly =
                savedStateHandle[DRAFT_UPDATE_CONNECTED_ONLY] ?: false,
            subscriptionUserAgent = savedStateHandle[DRAFT_USER_AGENT] ?: "",
            subscriptionAutoUpdate = savedStateHandle[DRAFT_AUTO_UPDATE] ?: false,
            subscriptionAutoUpdateDelay = savedStateHandle[DRAFT_AUTO_UPDATE_DELAY] ?: "1440",
        )
    }

    private val _uiState = MutableStateFlow(
        restoredDraft() ?: GroupEditorState(groupId = groupId),
    )
    val uiState: StateFlow<GroupEditorState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val id = _uiState.value.groupId
                val group = if (id == 0L) null else SagerDatabase.groupDao.getById(id)
                if (id != 0L && group == null) throw MissingGroupException()
                val subscription = group?.subscription?.copyValues()
                val profiles = SagerDatabase.proxyDao.getAll().map {
                    GroupProfileChoice(it.id, it.displayName(), it.displayType())
                }
                _uiState.update { state ->
                    if (state.dirty) {
                        state.copy(loading = false, profiles = profiles)
                    } else {
                        state.copy(
                            loading = false,
                            name = group?.name.orEmpty(),
                            type = group?.type ?: GroupType.BASIC,
                            order = group?.order ?: GroupOrder.ORIGIN,
                            isSelector = group?.isSelector ?: false,
                            frontProxy = group?.frontProxy ?: NO_PROXY,
                            landingProxy = group?.landingProxy ?: NO_PROXY,
                            subscriptionLink = subscription?.link.orEmpty(),
                            subscriptionForceResolve = subscription?.forceResolve ?: false,
                            subscriptionDeduplication = subscription?.deduplication ?: false,
                            subscriptionUpdateWhenConnectedOnly = subscription?.updateWhenConnectedOnly ?: false,
                            subscriptionUserAgent = subscription?.customUserAgent.orEmpty(),
                            subscriptionAutoUpdate = subscription?.autoUpdate ?: false,
                            subscriptionAutoUpdateDelay = (subscription?.autoUpdateDelay ?: 1440).toString(),
                            profiles = profiles,
                            error = null,
                        )
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = throwable.message ?: "Unable to load group",
                        entityMissing = throwable is MissingGroupException,
                    )
                }
            }
        }
    }

    fun setName(value: String) = updateDraft { it.copy(name = value, errors = it.errors - "name") }
    fun setType(value: Int) = updateDraft { it.copy(type = value) }
    fun setOrder(value: Int) = updateDraft { it.copy(order = value) }
    fun setSelector(value: Boolean) = updateDraft { it.copy(isSelector = value) }
    fun setFrontProxy(value: Long) = updateDraft { it.copy(frontProxy = value) }
    fun setLandingProxy(value: Long) = updateDraft { it.copy(landingProxy = value) }
    fun setSubscriptionLink(value: String) = updateDraft { it.copy(subscriptionLink = value) }
    fun setForceResolve(value: Boolean) = updateDraft { it.copy(subscriptionForceResolve = value) }
    fun setDeduplication(value: Boolean) = updateDraft { it.copy(subscriptionDeduplication = value) }
    fun setUpdateWhenConnectedOnly(value: Boolean) = updateDraft { it.copy(subscriptionUpdateWhenConnectedOnly = value) }
    fun setUserAgent(value: String) = updateDraft { it.copy(subscriptionUserAgent = value) }
    fun setAutoUpdate(value: Boolean) = updateDraft { it.copy(subscriptionAutoUpdate = value) }
    fun setAutoUpdateDelay(value: String) = updateDraft { it.copy(subscriptionAutoUpdateDelay = value) }

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
        clearDraft()
        _uiState.update { it.copy(showDiscardWarning = false, saved = true) }
    }

    fun save() {
        val current = _uiState.value
        if (current.entityMissing) return
        val errors = validate(current)
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(errors = errors) }
            return
        }
        _uiState.update { it.copy(saving = true, errors = emptyMap(), message = null, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val existing = if (current.groupId == 0L) null else
                    SagerDatabase.groupDao.getById(current.groupId)
                        ?: throw MissingGroupException()
                val group = (existing?.copy(subscription = existing.subscription?.copyValues()) ?: ProxyGroup()).apply {
                    name = current.name.trim().ifBlank { "My group" }
                    type = current.type
                    order = current.order
                    isSelector = current.isSelector
                    frontProxy = current.frontProxy
                    landingProxy = current.landingProxy

                    // Quota/userinfo belongs to the same subscription only when both
                    // the group type and subscription link are unchanged.
                    val keepUserInfo = existing != null &&
                        existing.type == GroupType.SUBSCRIPTION &&
                        current.type == GroupType.SUBSCRIPTION &&
                        existing.subscription?.link == current.subscriptionLink
                    if (!keepUserInfo) subscription?.subscriptionUserinfo = ""

                    if (type == GroupType.SUBSCRIPTION) {
                        val old = subscription ?: SubscriptionBean().apply { initializeDefaultValues() }
                        subscription = old.apply {
                            link = current.subscriptionLink.trim()
                            forceResolve = current.subscriptionForceResolve
                            deduplication = current.subscriptionDeduplication
                            updateWhenConnectedOnly = current.subscriptionUpdateWhenConnectedOnly
                            customUserAgent = current.subscriptionUserAgent
                            autoUpdate = current.subscriptionAutoUpdate
                            autoUpdateDelay = current.subscriptionAutoUpdateDelay.toInt()
                        }
                    }
                }
                if (current.groupId == 0L) GroupManager.createGroup(group)
                else GroupManager.updateGroup(group)
            }.onSuccess {
                clearDraft()
                _uiState.update { it.copy(saving = false, dirty = false, saved = true) }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        saving = false,
                        error = throwable.message ?: "Unable to save group",
                        entityMissing = throwable is MissingGroupException,
                    )
                }
            }
        }
    }

    private fun validate(state: GroupEditorState): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (state.subscriptionAutoUpdateDelay.toIntOrNull() == null) {
            errors["subscriptionAutoUpdateDelay"] = "Update delay must be a number"
        } else if (state.subscriptionAutoUpdateDelay.toInt() < 15) {
            errors["subscriptionAutoUpdateDelay"] = "Update delay must be at least 15 minutes"
        }
        if (state.type == GroupType.SUBSCRIPTION && state.subscriptionLink.isBlank()) {
            errors["subscriptionLink"] = "Subscription link is required"
        }
        return errors
    }

    private fun SubscriptionBean.copyValues() = SubscriptionBean().also { copy ->
        copy.type = type
        copy.link = link
        copy.token = token
        copy.forceResolve = forceResolve
        copy.deduplication = deduplication
        copy.updateWhenConnectedOnly = updateWhenConnectedOnly
        copy.customUserAgent = customUserAgent
        copy.autoUpdate = autoUpdate
        copy.autoUpdateDelay = autoUpdateDelay
        copy.lastUpdated = lastUpdated
        copy.bytesUsed = bytesUsed
        copy.bytesRemaining = bytesRemaining
        copy.username = username
        copy.expiryDate = expiryDate
        copy.protocols = protocols?.toList()
        copy.subscriptionUserinfo = subscriptionUserinfo
        copy.initializeDefaultValues()
    }

    private fun updateDraft(transform: (GroupEditorState) -> GroupEditorState) {
        val next = transform(_uiState.value).copy(dirty = true, saved = false)
        _uiState.value = next
        persistDraft(next)
    }

    private fun persistDraft(state: GroupEditorState) {
        savedStateHandle[DRAFT_GROUP_ID] = state.groupId
        savedStateHandle[DRAFT_NAME] = state.name
        savedStateHandle[DRAFT_TYPE] = state.type
        savedStateHandle[DRAFT_ORDER] = state.order
        savedStateHandle[DRAFT_SELECTOR] = state.isSelector
        savedStateHandle[DRAFT_FRONT_PROXY] = state.frontProxy
        savedStateHandle[DRAFT_LANDING_PROXY] = state.landingProxy
        savedStateHandle[DRAFT_SUBSCRIPTION_LINK] = state.subscriptionLink
        savedStateHandle[DRAFT_FORCE_RESOLVE] = state.subscriptionForceResolve
        savedStateHandle[DRAFT_DEDUPLICATION] = state.subscriptionDeduplication
        savedStateHandle[DRAFT_UPDATE_CONNECTED_ONLY] = state.subscriptionUpdateWhenConnectedOnly
        savedStateHandle[DRAFT_USER_AGENT] = state.subscriptionUserAgent
        savedStateHandle[DRAFT_AUTO_UPDATE] = state.subscriptionAutoUpdate
        savedStateHandle[DRAFT_AUTO_UPDATE_DELAY] = state.subscriptionAutoUpdateDelay
    }

    private fun clearDraft() {
        listOf(
            DRAFT_GROUP_ID,
            DRAFT_NAME,
            DRAFT_TYPE,
            DRAFT_ORDER,
            DRAFT_SELECTOR,
            DRAFT_FRONT_PROXY,
            DRAFT_LANDING_PROXY,
            DRAFT_SUBSCRIPTION_LINK,
            DRAFT_FORCE_RESOLVE,
            DRAFT_DEDUPLICATION,
            DRAFT_UPDATE_CONNECTED_ONLY,
            DRAFT_USER_AGENT,
            DRAFT_AUTO_UPDATE,
            DRAFT_AUTO_UPDATE_DELAY,
        ).forEach { key -> savedStateHandle.remove<Any>(key) }
    }
}
