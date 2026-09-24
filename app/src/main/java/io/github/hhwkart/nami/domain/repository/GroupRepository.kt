package io.github.hhwkart.nami.domain.repository

import io.github.hhwkart.nami.domain.model.GroupId
import io.github.hhwkart.nami.domain.model.ProfileGroup
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun observeGroups(): Flow<List<ProfileGroup>>

    suspend fun getGroup(groupId: GroupId): ProfileGroup?

    suspend fun saveGroup(group: ProfileGroup): ProfileGroup

    /** Implementations preserve the existing product policy for profiles in a deleted group. */
    suspend fun deleteGroup(groupId: GroupId): Boolean
}
