package io.github.hhwkart.nami.domain.model

sealed interface RoutingOutbound {
    data object Proxy : RoutingOutbound
    data object Bypass : RoutingOutbound
    data object Block : RoutingOutbound

    data class Profile(val profileId: ProfileId) : RoutingOutbound {
        init {
            require(profileId.isAssigned) { "A profile outbound must reference a persisted profile" }
        }
    }
}

data class RoutingRule(
    val id: RuleId = RuleId.Unassigned,
    val name: String = "",
    val config: String = "",
    val userOrder: Long = 0L,
    val enabled: Boolean = false,
    val domains: String = "",
    val ip: String = "",
    val port: String = "",
    val sourcePort: String = "",
    val network: String = "",
    val source: String = "",
    val protocol: String = "",
    val outbound: RoutingOutbound = RoutingOutbound.Proxy,
    val packages: Set<String> = emptySet(),
) {
    init {
        require(userOrder >= 0L) { "Rule order cannot be negative" }
        require(packages.none { it.isBlank() }) { "Rule package names cannot be blank" }
    }

    val hasMatchCriteria: Boolean
        get() = domains.isNotBlank() ||
            ip.isNotBlank() ||
            port.isNotBlank() ||
            sourcePort.isNotBlank() ||
            network.isNotBlank() ||
            source.isNotBlank() ||
            protocol.isNotBlank() ||
            packages.isNotEmpty() ||
            config.isNotBlank()
}

data class RoutingPresetOwnership(
    val ruleId: RuleId,
    val presetId: RoutingPresetId,
    val canonicalId: String,
    val canonicalFingerprint: String,
) {
    init {
        require(ruleId.isAssigned) { "Preset ownership must reference a persisted rule" }
        require(canonicalId.isNotBlank()) { "Preset ownership canonical id cannot be blank" }
        require(canonicalFingerprint.isNotBlank()) {
            "Preset ownership fingerprint cannot be blank"
        }
    }
}

data class RoutingPresetSelection(
    val presetId: RoutingPresetId = RoutingPresetId.Global,
    val ownership: List<RoutingPresetOwnership> = emptyList(),
)
