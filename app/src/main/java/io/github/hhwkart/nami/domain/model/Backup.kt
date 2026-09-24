package io.github.hhwkart.nami.domain.model

enum class BackupSection {
    Profiles,
    Groups,
    Rules,
    Settings,
}

data class BackupSelection(
    val sections: Set<BackupSection> = emptySet(),
) {
    val includesProfiles: Boolean
        get() = BackupSection.Profiles in sections

    val includesGroups: Boolean
        get() = BackupSection.Groups in sections

    val includesRules: Boolean
        get() = BackupSection.Rules in sections

    val includesSettings: Boolean
        get() = BackupSection.Settings in sections

    companion object {
        val All = BackupSelection(BackupSection.entries.toSet())
        val None = BackupSelection()
    }
}

data class BackupInput(
    val content: String,
    val fileName: String? = null,
)

data class BackupArtifact(
    val content: String,
    val fileName: String,
    val version: BackupFormatVersion,
    val selection: BackupSelection,
)

data class BackupPreview(
    val version: BackupFormatVersion,
    val availableSections: BackupSelection,
    val profileCount: Int? = null,
    val groupCount: Int? = null,
    val ruleCount: Int? = null,
    val settingCount: Int? = null,
)

data class BackupValidation(
    val preview: BackupPreview?,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
) {
    val isValid: Boolean
        get() = errors.isEmpty() && preview != null
}

data class BackupRestoreRequest(
    val input: BackupInput,
    val selection: BackupSelection,
)

data class RestoredCounts(
    val profiles: Int = 0,
    val groups: Int = 0,
    val rules: Int = 0,
    val settings: Int = 0,
)

data class BackupRestoreResult(
    val version: BackupFormatVersion,
    val restored: RestoredCounts,
)
