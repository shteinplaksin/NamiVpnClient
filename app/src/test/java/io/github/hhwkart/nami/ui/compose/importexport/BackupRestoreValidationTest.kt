package io.github.hhwkart.nami.ui.compose.importexport

import io.github.hhwkart.nami.data.serialization.BackupJsonDocument
import io.github.hhwkart.nami.domain.model.BackupFormatVersion
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.database.preference.KeyValuePair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupRestoreValidationTest {
    @Test
    fun rejectsGroupsWithoutProfiles() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidation.validate(
                BackupJsonDocument(
                    version = BackupFormatVersion.CurrentV2,
                    groups = listOf("group"),
                ),
            )
        }
    }

    @Test
    fun acceptsProfilesWithoutGroupsForLegacyCompatibility() {
        BackupRestoreValidation.validate(
            BackupJsonDocument(
                version = BackupFormatVersion.LegacyV1,
                profiles = listOf("profile"),
            ),
        )
    }

    @Test
    fun acceptsEmptyProfilesWithGroups() {
        BackupRestoreValidation.validate(
            BackupJsonDocument(
                version = BackupFormatVersion.CurrentV2,
                profiles = emptyList(),
                groups = emptyList(),
            ),
        )
    }

    @Test
    fun rejectsProfileReferencesMissingFromAvailableGroups() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidation.validateProfileGroupReferences(
                profileGroupIds = listOf(3L, 9L),
                availableGroupIds = setOf(3L),
            )
        }
        assertEquals("Backup profiles reference groups that are not available: 9", error.message)
    }

    @Test
    fun acceptsProfileOnlyBackupWhenEveryGroupExistsLocally() {
        BackupRestoreValidation.validateProfileGroupReferences(
            profileGroupIds = listOf(4L, 4L),
            availableGroupIds = setOf(4L, 7L),
        )
    }

    @Test
    fun rejectsUnassignedGroupIdZero() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidation.validateProfileGroupReferences(
                profileGroupIds = listOf(0L),
                availableGroupIds = setOf(0L),
            )
        }
    }

    @Test
    fun rejectsDuplicatePrimaryIdsForEveryRestoredEntityType() {
        listOf("profile", "group", "rule").forEach { section ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                BackupRestoreValidation.validateUniquePrimaryIds(section, listOf(7L, 7L))
            }
            assertEquals("Backup contains duplicate $section IDs: 7", error.message)
        }
    }

    @Test
    fun rejectsNonPositivePrimaryIdsBeforeRoomCanGenerateReplacementIds() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidation.validateUniquePrimaryIds("profile", listOf(0L))
        }
        assertEquals("Backup contains invalid profile IDs; IDs must be positive", error.message)
    }

    @Test
    fun rejectsDuplicateSettingsKeys() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            BackupRestoreValidation.validateUniqueSettingKeys(listOf(Key.PROFILE_ID, Key.PROFILE_ID))
        }
        assertEquals("Backup contains duplicate settings keys: ${Key.PROFILE_ID}", error.message)
    }

    @Test
    fun profileReplacementWithoutSettingsClearsOldProfilePointers() {
        assertEquals(
            0L,
            BackupRestoreValidation.profilePointerAfterRestore(
                profilesReplaced = true,
                settingsRestored = false,
                importedProfileId = 88L,
                availableProfileIds = setOf(88L),
            ),
        )
    }

    @Test
    fun settingsProfilePointersAreKeptOnlyWhenTheyExistInTheResultingProfiles() {
        assertEquals(
            12L,
            BackupRestoreValidation.profilePointerAfterRestore(
                profilesReplaced = true,
                settingsRestored = true,
                importedProfileId = 12L,
                availableProfileIds = setOf(12L),
            ),
        )
        assertEquals(
            0L,
            BackupRestoreValidation.profilePointerAfterRestore(
                profilesReplaced = true,
                settingsRestored = true,
                importedProfileId = 12L,
                availableProfileIds = setOf(13L),
            ),
        )
        val currentProfile = KeyValuePair(Key.PROFILE_CURRENT).put(15L).long
        assertEquals(
            0L,
            BackupRestoreValidation.profilePointerAfterRestore(
                profilesReplaced = true,
                settingsRestored = true,
                importedProfileId = currentProfile,
                availableProfileIds = setOf(14L),
            ),
        )
    }

    @Suppress("DEPRECATION")
    @Test
    fun profilePointerSettingsAcceptLegacyIntegerAndCurrentLongWireTypes() {
        val legacyValue = KeyValuePair(Key.PROFILE_ID).put(12)
        val currentValue = KeyValuePair(Key.PROFILE_CURRENT).put(13L)

        assertEquals(12L, legacyValue.long)
        assertEquals(13L, currentValue.long)
    }
}
