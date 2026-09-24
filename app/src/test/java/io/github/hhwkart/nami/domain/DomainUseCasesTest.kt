package io.github.hhwkart.nami.domain

import io.github.hhwkart.nami.domain.model.GroupId
import io.github.hhwkart.nami.domain.model.Profile
import io.github.hhwkart.nami.domain.model.ProfileId
import io.github.hhwkart.nami.domain.model.ProfilePayload
import io.github.hhwkart.nami.domain.model.ProtocolType
import io.github.hhwkart.nami.domain.model.SettingKey
import io.github.hhwkart.nami.domain.model.SettingValue
import io.github.hhwkart.nami.domain.model.SettingsSnapshot
import io.github.hhwkart.nami.domain.repository.ProfileRepository
import io.github.hhwkart.nami.domain.repository.SettingsRepository
import io.github.hhwkart.nami.domain.usecase.ProfileNotFoundException
import io.github.hhwkart.nami.domain.usecase.SelectProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainUseCasesTest {

    @Test
    fun selectProfileWritesTheCanonicalCurrentProfileSetting() = runBlocking {
        val profile = Profile(
            id = ProfileId(7L),
            groupId = GroupId(2L),
            displayName = "Selected",
            protocol = ProtocolType(4),
            configuration = ProfilePayload(ProtocolType(4), byteArrayOf(1)),
        )
        val profiles = FakeProfiles(profile)
        val settings = FakeSettings()

        val result = SelectProfile(profiles, settings)(profile.id)

        assertTrue(result.isSuccess)
        assertEquals(
            SettingValue.LongValue(profile.id.value),
            settings.snapshot[SettingKey("profileCurrent")],
        )
    }

    @Test
    fun selectProfileDoesNotWriteAnUnknownProfile() = runBlocking {
        val settings = FakeSettings()

        val result = SelectProfile(FakeProfiles(), settings)(ProfileId(7L))

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ProfileNotFoundException)
        assertEquals(emptyMap<SettingKey, SettingValue>(), settings.snapshot.values)
    }

    private class FakeProfiles(vararg initial: Profile) : ProfileRepository {
        private val values = initial.associateBy { it.id }.toMutableMap()

        override fun observeProfiles(groupId: GroupId?): Flow<List<Profile>> = emptyFlow()

        override suspend fun getProfile(profileId: ProfileId): Profile? = values[profileId]

        override suspend fun saveProfile(profile: Profile): Profile {
            values[profile.id] = profile
            return profile
        }

        override suspend fun deleteProfile(profileId: ProfileId): Boolean =
            values.remove(profileId) != null

        override suspend fun reorderProfiles(groupId: GroupId, orderedProfileIds: List<ProfileId>) = Unit
    }

    private class FakeSettings : SettingsRepository {
        var snapshot = SettingsSnapshot()

        override fun observeSettings(): Flow<SettingsSnapshot> = emptyFlow()

        override suspend fun readSettings(): SettingsSnapshot = snapshot

        override suspend fun updateSettings(transform: (SettingsSnapshot) -> SettingsSnapshot): SettingsSnapshot {
            snapshot = transform(snapshot)
            return snapshot
        }
    }
}
