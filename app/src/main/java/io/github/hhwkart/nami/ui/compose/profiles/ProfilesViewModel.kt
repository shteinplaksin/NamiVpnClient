package io.github.hhwkart.nami.ui.compose.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.GroupOrder
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.GroupManager
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.group.GroupUpdater
import io.github.hhwkart.nami.ui.ConnectionTestViewModel
import io.github.hhwkart.nami.ui.compose.ConnectionBus
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.bg.proto.UrlTest
import io.github.hhwkart.nami.ktx.app
import io.github.hhwkart.nami.ktx.readableMessage
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import io.github.hhwkart.nami.core.Protocols
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class ProfileUi(
    val id: Long,
    val groupId: Long,
    val name: String,
    val type: String,
    val address: String,
    val status: Int,
    val ping: Int,
    val error: String?,
    val tx: Long,
    val rx: Long,
    val selected: Boolean,
    val running: Boolean,
    val canEdit: Boolean,
    val canDelete: Boolean,
    val entity: ProxyEntity,
)

data class ProfilesUiState(
    val groups: List<ProxyGroup> = emptyList(),
    val items: List<ProfileUi> = emptyList(),
    val query: String = "",
    val testingGroupIds: Set<Long> = emptySet(),
)

@HiltViewModel
class ProfilesViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    private var groupsCache: List<ProxyGroup> = emptyList()
    private val pendingDeletes = ConcurrentHashMap<Long, Job>()

    init {
        reload()
        // live updates from ProfileManager/GroupManager listeners
        viewModelScope.launch(Dispatchers.IO) {
            ProfileManager.addListener(object : ProfileManager.Listener {
                override suspend fun onAdd(profile: ProxyEntity) = refreshGroup(profile.groupId)
                override suspend fun onUpdated(data: io.github.hhwkart.nami.aidl.TrafficData) =
                    refreshProfileTraffic(data.id, data.tx, data.rx)

                override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) =
                    refreshGroup(profile.groupId)

                override suspend fun onRemoved(groupId: Long, profileId: Long) = reload()
            })
        }
        viewModelScope.launch {
            ConnectionBus.state.collect { connection ->
                val selected = DataStore.selectedProxy
                val running = DataStore.currentProfile
                _uiState.update { state ->
                    state.copy(items = state.items.map { item ->
                        item.copy(
                            selected = item.id == selected,
                            running = connection.started && item.id == running,
                        )
                    })
                }
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
            var groups = SagerDatabase.groupDao.allGroups()
            if (groups.isEmpty()) {
                SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                groups = SagerDatabase.groupDao.allGroups()
            }
            groupsCache = groups
            val items = mutableListOf<ProfileUi>()
            val selected = DataStore.selectedProxy
            val running = DataStore.currentProfile
            val query = _uiState.value.query.lowercase()
            for (group in groups) {
                val profiles = ordered(group)
                for (p in profiles) {
                    if (pendingDeletes.containsKey(p.id)) continue
                    if (query.isNotEmpty() &&
                        !p.displayName().lowercase().contains(query) &&
                        !p.displayType().lowercase().contains(query) &&
                        !p.displayAddress().lowercase().contains(query)
                    ) continue
                    items += p.toUi(group.id, selected, running)
                }
            }
            _uiState.update { it.copy(groups = groups, items = items) }
        }
    }

    private suspend fun ordered(group: ProxyGroup): List<ProxyEntity> {
        var profiles = SagerDatabase.proxyDao.getByGroup(group.id)
        when (group.order) {
            GroupOrder.BY_NAME -> profiles = profiles.sortedBy { it.displayName() }
            GroupOrder.BY_DELAY -> profiles =
                profiles.sortedBy { if (it.status == 1) it.ping else 114514 }
        }
        return profiles
    }

    private fun ProxyEntity.toUi(groupId: Long, selected: Long, running: Long) = ProfileUi(
        id = id,
        groupId = groupId,
        name = displayName() ?: "",
        type = displayType(),
        address = displayAddress(),
        status = status,
        ping = ping,
        error = error,
        tx = tx,
        rx = rx,
        selected = id == selected,
        running = id == selected && id == running && ConnectionBus.state.value.started,
        canEdit = nekoBean == null,
        canDelete = true,
        entity = this,
    )

    private fun refreshGroup(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val selected = DataStore.selectedProxy
            val running = DataStore.currentProfile
            val group = groupsCache.find { it.id == groupId } ?: return@launch
            val fresh = ordered(group).map { it.toUi(groupId, selected, running) }
            _uiState.update { state ->
                state.copy(items = state.items.filterNot { it.groupId == groupId } + fresh)
            }
        }
    }

    private fun refreshProfileTraffic(profileId: Long, tx: Long, rx: Long) {
        _uiState.update { state ->
            state.copy(items = state.items.map {
                if (it.id == profileId) it.copy(tx = tx, rx = rx) else it
            })
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        reload()
    }

    fun setOrder(group: ProxyGroup, order: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            group.order = order
            GroupManager.updateGroup(group)
        }
    }

    fun connectProfile(profile: ProfileUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val changed = DataStore.selectedProxy != profile.id
            DataStore.selectedProxy = profile.id
            DataStore.currentProfile = profile.id
            _uiState.update { state ->
                state.copy(items = state.items.map { item ->
                    item.copy(
                        selected = item.id == profile.id,
                        running = item.id == profile.id && ConnectionBus.state.value.started,
                    )
                })
            }
            if (changed && ConnectionBus.state.value.canStop) {
                SagerNet.reloadService()
            } else if (!changed && !ConnectionBus.state.value.started) {
                ConnectionBus.toggleConnection()
            }
        }
    }

    fun stageDelete(profile: ProfileUi) {
        if (isActiveProfile(profile.id) || pendingDeletes.containsKey(profile.id)) {
            reload()
            return
        }
        _uiState.update { state ->
            state.copy(items = state.items.filterNot { it.id == profile.id })
        }
        pendingDeletes[profile.id] = viewModelScope.launch(Dispatchers.IO) {
            delay(PENDING_DELETE_MILLIS)
            val deleted = runCatching {
                ProfileManager.deleteProfile(profile.groupId, profile.id)
            }.isSuccess
            pendingDeletes.remove(profile.id)
            if (!deleted) reload()
        }
    }

    fun undoDelete(profile: ProfileUi) {
        pendingDeletes.remove(profile.id)?.cancel()
        reload()
    }

    fun moveProfile(profile: ProfileUi, destination: ProxyGroup) {
        if (isActiveProfile(profile.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val entity = ProfileManager.getProfile(profile.id) ?: return@launch
            val oldGroupId = entity.groupId
            if (oldGroupId == destination.id) return@launch
            entity.groupId = destination.id
            entity.userOrder = SagerDatabase.proxyDao.nextOrder(destination.id) ?: 1
            ProfileManager.updateProfile(entity)
            GroupManager.postUpdate(oldGroupId)
            GroupManager.postUpdate(destination.id)
            DataStore.editingGroup = destination.id
        }
    }

    fun moveWithinGroup(profile: ProfileUi, offset: Int) {
        if (isActiveProfile(profile.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val profiles = SagerDatabase.proxyDao.getByGroup(profile.groupId)
                .sortedBy { it.userOrder }
                .toMutableList()
            val from = profiles.indexOfFirst { it.id == profile.id }
            val to = from + offset
            if (from !in profiles.indices || to !in profiles.indices) return@launch
            val first = profiles[from]
            val second = profiles[to]
            val order = first.userOrder
            first.userOrder = second.userOrder
            second.userOrder = order
            ProfileManager.updateProfile(listOf(first, second))
            GroupManager.postUpdate(profile.groupId)
        }
    }

    fun updateSubscription(group: ProxyGroup) {
        GroupUpdater.startUpdate(group, true)
    }

    fun clearTraffic(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val changed = SagerDatabase.proxyDao.getByGroup(groupId).filter {
                it.tx != 0L || it.rx != 0L
            }.onEach {
                it.tx = 0L
                it.rx = 0L
            }
            if (changed.isNotEmpty()) ProfileManager.updateProfile(changed)
        }
    }

    fun clearTestResults(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val changed = SagerDatabase.proxyDao.getByGroup(groupId).filter {
                it.status != 0
            }.onEach {
                it.status = 0
                it.ping = 0
                it.error = null
            }
            if (changed.isNotEmpty()) ProfileManager.updateProfile(changed)
        }
    }

    fun deleteUnavailable(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            SagerDatabase.proxyDao.getByGroup(groupId)
                .filter { it.status != 0 && it.status != 1 && !isActiveProfile(it.id) }
                .forEach { ProfileManager.deleteProfile(it.groupId, it.id) }
        }
    }

    fun removeDuplicates(groupId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val unique = LinkedHashSet<Protocols.Deduplication>()
            SagerDatabase.proxyDao.getByGroup(groupId).forEach { profile ->
                val key = Protocols.Deduplication(profile.requireBean(), profile.displayType())
                if (!unique.add(key) && !isActiveProfile(profile.id)) {
                    ProfileManager.deleteProfile(profile.groupId, profile.id)
                }
            }
        }
    }

    /** Batch connection test over the group owning [testTargetGroupId]. */
    fun testAll(
        testTargetGroupId: Long,
        groupTitle: String,
        test: ConnectionTestViewModel,
        icmp: Boolean = false,
    ) {
        if (test.isRunning) return
        test.start(icmp, testTargetGroupId, groupTitle)
        _uiState.update { it.copy(testingGroupIds = it.testingGroupIds + testTargetGroupId) }
    }

    fun syncTestState(test: ConnectionTestViewModel) {
        if (!test.isRunning) {
            _uiState.update { if (it.testingGroupIds.isEmpty()) it else it.copy(testingGroupIds = emptySet()) }
        }
    }

    fun testProfile(profile: ProfileUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val entity = SagerDatabase.proxyDao.getById(profile.id) ?: return@launch
            entity.status = 0
            ProfileManager.updateProfile(entity)
            _uiState.update { state ->
                state.copy(items = state.items.map {
                    if (it.id == profile.id) it.copy(status = 0) else it
                })
            }
            try {
                val urlTest = UrlTest()
                val result = withTimeoutOrNull(5000L) {
                    urlTest.doTest(entity)
                }
                if (result != null && result > 0) {
                    entity.status = 1
                    entity.ping = result
                } else {
                    entity.status = 2
                    entity.error = app.getString(R.string.connection_test_timeout)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                entity.status = 3
                entity.error = e.readableMessage
            }
            ProfileManager.updateProfile(entity)
            reload()
        }
    }

    private fun isActiveProfile(profileId: Long): Boolean =
        ConnectionBus.state.value.started && DataStore.currentProfile == profileId

    private companion object {
        const val PENDING_DELETE_MILLIS = 6_000L
    }
}
