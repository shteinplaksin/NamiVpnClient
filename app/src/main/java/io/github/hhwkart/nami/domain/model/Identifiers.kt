package io.github.hhwkart.nami.domain.model

/** Stable identifiers used by the domain layer. Zero means that a record has not been persisted yet. */
@JvmInline
value class ProfileId(val value: Long) {
    init {
        require(value >= 0L) { "Profile id cannot be negative: $value" }
    }

    val isAssigned: Boolean
        get() = value > 0L

    companion object {
        val Unassigned = ProfileId(0L)
    }
}

@JvmInline
value class GroupId(val value: Long) {
    init {
        require(value >= 0L) { "Group id cannot be negative: $value" }
    }

    val isAssigned: Boolean
        get() = value > 0L

    companion object {
        val Unassigned = GroupId(0L)
    }
}

@JvmInline
value class RuleId(val value: Long) {
    init {
        require(value >= 0L) { "Rule id cannot be negative: $value" }
    }

    val isAssigned: Boolean
        get() = value > 0L

    companion object {
        val Unassigned = RuleId(0L)
    }
}

/** Numeric protocol code retained for compatibility with the existing profile database. */
@JvmInline
value class ProtocolType(val code: Int) {
    init {
        require(code >= 0) { "Protocol type cannot be negative: $code" }
    }
}

/** Numeric group code retained so unknown future group types can survive a round trip. */
@JvmInline
value class GroupType(val code: Int) {
    init {
        require(code >= 0) { "Group type cannot be negative: $code" }
    }

    companion object {
        val Basic = GroupType(0)
        val Subscription = GroupType(1)
    }
}

/** Numeric sort mode retained for compatibility with the current settings values. */
@JvmInline
value class GroupSortOrder(val code: Int) {
    init {
        require(code >= 0) { "Group sort order cannot be negative: $code" }
    }

    companion object {
        val Origin = GroupSortOrder(0)
        val ByName = GroupSortOrder(1)
        val ByDelay = GroupSortOrder(2)
    }
}

@JvmInline
value class RoutingPresetId(val value: String) {
    init {
        require(value.isNotBlank()) { "Routing preset id cannot be blank" }
    }

    companion object {
        val Global = RoutingPresetId("global")
        val BypassLan = RoutingPresetId("bypass_lan")
        val BypassChina = RoutingPresetId("bypass_china")
        val GfwList = RoutingPresetId("gfw_list")
        val Custom = RoutingPresetId("custom")
    }
}

@JvmInline
value class SettingKey(val value: String) {
    init {
        require(value.isNotBlank()) { "Setting key cannot be blank" }
    }
}

@JvmInline
value class BackupFormatVersion(val value: Int) {
    init {
        require(value > 0) { "Backup format version must be positive: $value" }
    }

    companion object {
        val LegacyV1 = BackupFormatVersion(1)
        val CurrentV2 = BackupFormatVersion(2)
    }
}
