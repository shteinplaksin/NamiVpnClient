package io.github.hhwkart.nami.domain.repository

import io.github.hhwkart.nami.domain.model.GroupId
import io.github.hhwkart.nami.domain.model.Profile
import io.github.hhwkart.nami.domain.model.ProfileId
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfiles(groupId: GroupId? = null): Flow<List<Profile>>

    suspend fun getProfile(profileId: ProfileId): Profile?

    suspend fun saveProfile(profile: Profile): Profile

    suspend fun deleteProfile(profileId: ProfileId): Boolean

    suspend fun reorderProfiles(groupId: GroupId, orderedProfileIds: List<ProfileId>)
}
