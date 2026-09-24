package io.github.hhwkart.nami.domain

import io.github.hhwkart.nami.domain.model.BackupSection
import io.github.hhwkart.nami.domain.model.BackupSelection
import io.github.hhwkart.nami.domain.model.GroupId
import io.github.hhwkart.nami.domain.model.LatencyRequest
import io.github.hhwkart.nami.domain.model.Profile
import io.github.hhwkart.nami.domain.model.ProfileId
import io.github.hhwkart.nami.domain.model.ProfilePayload
import io.github.hhwkart.nami.domain.model.ProtocolType
import io.github.hhwkart.nami.domain.model.RuleId
import io.github.hhwkart.nami.domain.model.RoutingOutbound
import io.github.hhwkart.nami.domain.model.RoutingRule
import io.github.hhwkart.nami.domain.model.SettingKey
import io.github.hhwkart.nami.domain.model.SettingValue
import io.github.hhwkart.nami.domain.model.SettingsSnapshot
import io.github.hhwkart.nami.domain.model.StorageMigrationState
import io.github.hhwkart.nami.domain.model.TrafficStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainContractsTest {

    @Test
    fun identifiersRejectNegativeValuesAndExposePersistenceState() {
        assertEquals(0L, ProfileId.Unassigned.value)
        assertFalse(ProfileId.Unassigned.isAssigned)
        assertTrue(ProfileId(12L).isAssigned)
        assertTrue(GroupId(12L).isAssigned)
        assertTrue(RuleId(12L).isAssigned)

        assertFails<IllegalArgumentException> { ProfileId(-1L) }
        assertFails<IllegalArgumentException> { GroupId(-1L) }
        assertFails<IllegalArgumentException> { RuleId(-1L) }
    }

    @Test
    fun profilePayloadIsOpaqueButByteArraySafe() {
        val source = byteArrayOf(1, 2, 3)
        val payload = ProfilePayload(ProtocolType(4), source)
        source[0] = 99

        assertEquals(byteArrayOf(1, 2, 3).toList(), payload.bytes.toList())
        assertEquals(payload, ProfilePayload(ProtocolType(4), byteArrayOf(1, 2, 3)))

        val profile = Profile(
            id = ProfileId(7L),
            groupId = GroupId(3L),
            displayName = "Test profile",
            protocol = ProtocolType(4),
            configuration = payload,
        )
        assertEquals("Test profile", profile.displayName)
    }

    @Test
    fun routingRuleReportsWhetherItHasMatchCriteria() {
        assertFalse(RoutingRule().hasMatchCriteria)
        assertTrue(RoutingRule(domains = "example.com").hasMatchCriteria)
        assertTrue(RoutingRule(packages = setOf("com.example.app")).hasMatchCriteria)
        assertEquals(
            RoutingOutbound.Profile(ProfileId(42L)),
            RoutingRule(outbound = RoutingOutbound.Profile(ProfileId(42L))).outbound,
        )
    }

    @Test
    fun settingsSnapshotPreservesTypedValuesAndMigrationState() {
        val key = SettingKey("customKey")
        val initial = SettingsSnapshot(
            migrationState = StorageMigrationState.InProgress,
        )
        val updated = initial.withValue(key, SettingValue.StringValue("value"))

        assertEquals(StorageMigrationState.InProgress, updated.migrationState)
        assertEquals(SettingValue.StringValue("value"), updated[key])
        assertEquals(null, updated.withoutValue(key)[key])
    }

    @Test
    fun backupSelectionIsExplicit() {
        val selection = BackupSelection(setOf(BackupSection.Profiles, BackupSection.Settings))

        assertTrue(selection.includesProfiles)
        assertTrue(selection.includesSettings)
        assertFalse(selection.includesGroups)
        assertFalse(selection.includesRules)
        assertEquals(4, BackupSelection.All.sections.size)
    }

    @Test
    fun zeroTrafficIsAValidDomainValue() {
        val stats = TrafficStats()

        assertEquals(0L, stats.totalBytes)
        assertEquals(0L, stats.uploadedBytes)
        assertEquals(0L, stats.downloadedBytes)
    }

    @Test
    fun latencyRequestRequiresPositiveTimeoutAndConcurrency() {
        assertFails<IllegalArgumentException> { LatencyRequest("", timeoutMillis = 0L) }
        assertFails<IllegalArgumentException> { LatencyRequest("", concurrency = 0) }
    }

    private inline fun <reified T : Throwable> assertFails(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            assertTrue("Expected ${T::class.java.simpleName}, got ${error::class.java}", error is T)
            return
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }
}
