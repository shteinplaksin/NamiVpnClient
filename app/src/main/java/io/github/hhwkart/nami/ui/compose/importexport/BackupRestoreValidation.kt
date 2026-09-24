package io.github.hhwkart.nami.ui.compose.importexport

import io.github.hhwkart.nami.data.serialization.BackupJsonDocument

internal object BackupRestoreValidation {
    fun validate(document: BackupJsonDocument) {
        require(document.groups == null || document.profiles != null) {
            "Backup contains groups without profiles and cannot be restored"
        }
    }

    fun validateProfileGroupReferences(
        profileGroupIds: Collection<Long>,
        availableGroupIds: Set<Long>,
    ) {
        val missingGroupIds = profileGroupIds.filter { it <= 0L || it !in availableGroupIds }.toSet()
        require(missingGroupIds.isEmpty()) {
            "Backup profiles reference groups that are not available: ${missingGroupIds.sorted().joinToString()}"
        }
    }

    fun validateUniquePrimaryIds(sectionName: String, ids: Collection<Long>) {
        require(ids.all { it > 0L }) {
            "Backup contains invalid $sectionName IDs; IDs must be positive"
        }
        val duplicateIds = ids.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        require(duplicateIds.isEmpty()) {
            "Backup contains duplicate $sectionName IDs: ${duplicateIds.joinToString()}"
        }
    }

    fun validateUniqueSettingKeys(keys: Collection<String>) {
        val duplicateKeys = keys.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
            .sorted()
        require(duplicateKeys.isEmpty()) {
            "Backup contains duplicate settings keys: ${duplicateKeys.joinToString()}"
        }
    }

    fun profilePointerAfterRestore(
        profilesReplaced: Boolean,
        settingsRestored: Boolean,
        importedProfileId: Long?,
        availableProfileIds: Set<Long>,
    ): Long? {
        if (!settingsRestored) return if (profilesReplaced) 0L else null
        val profileId = importedProfileId ?: 0L
        return profileId.takeIf { it > 0L && it in availableProfileIds } ?: 0L
    }
}
