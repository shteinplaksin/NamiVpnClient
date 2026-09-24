package io.github.hhwkart.nami.domain.usecase

import io.github.hhwkart.nami.domain.core.CoreGateway
import io.github.hhwkart.nami.domain.core.QuiescenceReason
import io.github.hhwkart.nami.domain.core.QuiescenceResult
import io.github.hhwkart.nami.domain.core.ServiceQuiescence
import io.github.hhwkart.nami.domain.model.BackupArtifact
import io.github.hhwkart.nami.domain.model.BackupInput
import io.github.hhwkart.nami.domain.model.BackupRestoreRequest
import io.github.hhwkart.nami.domain.model.BackupRestoreResult
import io.github.hhwkart.nami.domain.model.BackupSelection
import io.github.hhwkart.nami.domain.model.BackupValidation
import io.github.hhwkart.nami.domain.model.GroupId
import io.github.hhwkart.nami.domain.model.LatencyRequest
import io.github.hhwkart.nami.domain.model.LatencyResult
import io.github.hhwkart.nami.domain.model.Profile
import io.github.hhwkart.nami.domain.model.ProfileId
import io.github.hhwkart.nami.domain.model.RoutingPresetId
import io.github.hhwkart.nami.domain.model.RoutingPresetSelection
import io.github.hhwkart.nami.domain.model.SettingsSnapshot
import io.github.hhwkart.nami.domain.model.SubscriptionRecord
import io.github.hhwkart.nami.domain.model.SubscriptionUpdateReason
import io.github.hhwkart.nami.domain.repository.BackupRepository
import io.github.hhwkart.nami.domain.repository.ProfileRepository
import io.github.hhwkart.nami.domain.repository.RoutingRepository
import io.github.hhwkart.nami.domain.repository.SettingsRepository
import io.github.hhwkart.nami.domain.repository.SubscriptionRepository
import kotlinx.coroutines.flow.Flow

class ObserveProfiles(private val profiles: ProfileRepository) {
    operator fun invoke(groupId: GroupId? = null): Flow<List<Profile>> =
        profiles.observeProfiles(groupId)
}

class SaveProfile(private val profiles: ProfileRepository) {
    suspend operator fun invoke(profile: Profile): Profile = profiles.saveProfile(profile)
}

class DeleteProfile(private val profiles: ProfileRepository) {
    suspend operator fun invoke(profileId: ProfileId): Boolean = profiles.deleteProfile(profileId)
}

class SelectProfile(
    private val profiles: ProfileRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(profileId: ProfileId): Result<Unit> {
        if (profiles.getProfile(profileId) == null) {
            return Result.failure(ProfileNotFoundException(profileId))
        }
        settings.set(
            io.github.hhwkart.nami.domain.model.SettingKeys.profileCurrent,
            io.github.hhwkart.nami.domain.model.SettingValue.LongValue(profileId.value),
        )
        return Result.success(Unit)
    }
}

class ApplyRoutingPreset(private val routing: RoutingRepository) {
    suspend operator fun invoke(presetId: RoutingPresetId): RoutingPresetSelection =
        routing.applyPreset(presetId)
}

class UpdateSubscription(private val subscriptions: SubscriptionRepository) {
    suspend operator fun invoke(
        groupId: GroupId,
        reason: SubscriptionUpdateReason = SubscriptionUpdateReason.Manual,
    ): SubscriptionRecord = subscriptions.updateSubscription(groupId, reason)
}

class ConnectVpn(private val core: CoreGateway) {
    suspend operator fun invoke(profileId: ProfileId) = core.connect(profileId)
}

class DisconnectVpn(private val core: CoreGateway) {
    suspend operator fun invoke() = core.disconnect()
}

class TestLatency(private val core: CoreGateway) {
    suspend operator fun invoke(profileId: ProfileId, request: LatencyRequest): LatencyResult =
        core.testLatency(profileId, request)
}

class ObserveSettings(private val settings: SettingsRepository) {
    operator fun invoke(): Flow<SettingsSnapshot> = settings.observeSettings()
}

class ExportBackup(private val backup: BackupRepository) {
    suspend operator fun invoke(selection: BackupSelection): BackupArtifact =
        backup.export(selection)
}

class ValidateBackup(private val backup: BackupRepository) {
    suspend operator fun invoke(input: BackupInput, selection: BackupSelection): BackupValidation =
        backup.validate(input, selection)
}

class RestoreBackup(private val backup: BackupRepository) {
    suspend operator fun invoke(request: BackupRestoreRequest): BackupRestoreResult =
        backup.restore(request)
}

class AcquireServiceQuiescence(private val quiescence: ServiceQuiescence) {
    suspend operator fun invoke(
        reason: QuiescenceReason,
        timeoutMillis: Long,
    ): QuiescenceResult = quiescence.acquire(reason, timeoutMillis)
}

class ProfileNotFoundException(profileId: ProfileId) :
    IllegalArgumentException("Profile ${profileId.value} was not found")
