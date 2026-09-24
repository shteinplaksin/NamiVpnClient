package io.github.hhwkart.nami.ui.compose.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.GroupType
import io.github.hhwkart.nami.database.GroupManager
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.database.SubscriptionBean
import io.github.hhwkart.nami.group.GroupUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GroupUi(
    val id: Long,
    val name: String,
    val isSubscription: Boolean,
    val profileCount: Long,
    val autoUpdate: Boolean,
    val updating: Boolean,
    val updateProgress: Int,
    val updateMax: Int,
    val bytesUsed: Long,
    val bytesTotal: Long,
    val lastUpdated: Int,
    val expireAt: Long, // unix seconds, 0 = unknown
    val group: ProxyGroup,
)

data class GroupsUiState(
    val groups: List<GroupUi> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class GroupsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    init {
        reload()
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                // update spinners are driven by GroupUpdater's process-global
                // state; cheap poll keeps us consistent with the bg process
                delay(500)
                refreshUpdateState()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            GroupManager.addListener(object : GroupManager.Listener {
                override suspend fun groupAdd(group: ProxyGroup) = reload()
                override suspend fun groupRemoved(groupId: Long) = reload()
                override suspend fun groupUpdated(group: ProxyGroup) = reload()
                override suspend fun groupUpdated(groupId: Long) = reload()
            })
        }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val groups = SagerDatabase.groupDao.allGroups()
                groups.map { g ->
                val sub = g.subscription
                val userinfo = sub?.subscriptionUserinfo
                var expireAt = sub?.expiryDate?.toLong() ?: 0L
                if (expireAt == 0L && !userinfo.isNullOrBlank()) {
                    "expire=([0-9]+)".toRegex().find(userinfo)?.groupValues?.getOrNull(1)
                        ?.let { expireAt = it.toLong() }
                }
                var used = sub?.bytesUsed ?: 0L
                var total = sub?.bytesRemaining?.let { rem ->
                    if (rem >= 0 && (used > 0 || rem > 0)) used + rem else 0L
                } ?: 0L
                if (total == 0L && !userinfo.isNullOrBlank()) {
                    val up = "upload=([0-9]+)".toRegex().find(userinfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val down = "download=([0-9]+)".toRegex().find(userinfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    val t = "total=([0-9]+)".toRegex().find(userinfo)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                    used = used.coerceAtLeast(up + down)
                    total = t
                }
                GroupUi(
                    id = g.id,
                    name = g.displayName(),
                    isSubscription = g.type == GroupType.SUBSCRIPTION,
                    profileCount = SagerDatabase.proxyDao.countByGroup(g.id),
                    autoUpdate = sub?.autoUpdate ?: false,
                    updating = g.id in GroupUpdater.updating,
                    updateProgress = 0,
                    updateMax = 0,
                    bytesUsed = used,
                    bytesTotal = total,
                    lastUpdated = sub?.lastUpdated ?: 0,
                    expireAt = expireAt,
                    group = g,
                )
                }
            }.onSuccess { items ->
                _uiState.update { it.copy(groups = items, error = null) }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Unable to load groups.") }
            }
        }
    }

    private fun refreshUpdateState() {
        _uiState.update { state ->
            state.copy(groups = state.groups.map { g ->
                val updating = g.id in GroupUpdater.updating
                val progress = GroupUpdater.progress[g.id]
                if (updating == g.updating && !updating) g
                else g.copy(
                    updating = updating,
                    updateProgress = progress?.progress ?: 0,
                    updateMax = progress?.max ?: 0,
                )
            })
        }
    }

    fun setAutoUpdate(group: GroupUi, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            group.group.subscription?.autoUpdate = enabled
            GroupManager.updateGroup(group.group)
            reload()
        }
    }

    fun updateGroup(group: GroupUi) {
        if (group.id in GroupUpdater.updating) return
        GroupUpdater.startUpdate(group.group, true)
    }

    fun updateAll() {
        viewModelScope.launch(Dispatchers.IO) {
            SagerDatabase.groupDao.allGroups()
                .filter { it.type == GroupType.SUBSCRIPTION }
                .forEach { GroupUpdater.startUpdate(it, true) }
        }
    }

    fun addSubscription(name: String, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val group = ProxyGroup(type = GroupType.SUBSCRIPTION)
            group.name = name.ifBlank { "Subscription #${System.currentTimeMillis()}" }
            group.subscription = SubscriptionBean().apply {
                link = url
                initializeDefaultValues()
            }
            val created = GroupManager.createGroup(group)
            GroupUpdater.startUpdate(created, true)
        }
    }

    fun addBasicGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val group = ProxyGroup(type = GroupType.BASIC).apply {
                this.name = name.ifBlank { "Group #${System.currentTimeMillis()}" }
            }
            GroupManager.createGroup(group)
        }
    }

    fun moveGroup(group: GroupUi, offset: Int) {
        if (group.group.ungrouped || group.id in GroupUpdater.updating) return
        viewModelScope.launch(Dispatchers.IO) {
            val groups = SagerDatabase.groupDao.allGroups().sortedBy { it.userOrder }.toMutableList()
            val from = groups.indexOfFirst { it.id == group.id }
            val to = from + offset
            if (from !in groups.indices || to !in groups.indices) return@launch
            val first = groups[from]
            val second = groups[to]
            val order = first.userOrder
            first.userOrder = second.userOrder
            second.userOrder = order
            SagerDatabase.groupDao.updateGroup(first)
            SagerDatabase.groupDao.updateGroup(second)
            GroupManager.postUpdate(first.id)
            GroupManager.postUpdate(second.id)
        }
    }

    suspend fun profileLinks(groupId: Long): String = withContext(Dispatchers.IO) {
        SagerDatabase.proxyDao.getByGroup(groupId)
            .joinToString("\n") { it.toStdLink(compact = true) }
    }

    fun clearGroup(group: GroupUi) {
        if (group.id in GroupUpdater.updating) return
        viewModelScope.launch(Dispatchers.IO) {
            GroupManager.clearGroup(group.id)
            reload()
        }
    }

    fun deleteGroup(group: GroupUi) {
        if (group.group.ungrouped || group.id in GroupUpdater.updating) return
        viewModelScope.launch(Dispatchers.IO) {
            GroupManager.deleteGroup(group.group.id)
            reload()
        }
    }
}
