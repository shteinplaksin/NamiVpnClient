package io.github.hhwkart.nami.domain.model

@JvmInline
value class ServiceMode(val value: String) {
    init {
        require(value.isNotBlank()) { "Service mode cannot be blank" }
    }

    companion object {
        val Vpn = ServiceMode("vpn")
        val Proxy = ServiceMode("proxy")
    }
}

sealed interface SettingValue {
    data class BooleanValue(val value: Boolean) : SettingValue
    data class IntValue(val value: Int) : SettingValue
    data class LongValue(val value: Long) : SettingValue
    data class FloatValue(val value: Float) : SettingValue
    data class StringValue(val value: String) : SettingValue
    data class StringSetValue(val value: Set<String>) : SettingValue
}

/** Canonical names for settings that are already part of the persistent application contract. */
object SettingKeys {
    val profileCurrent = SettingKey("profileCurrent")
    val profileGroup = SettingKey("profileGroup")
    val serviceMode = SettingKey("serviceMode")
    val composeDynamicColors = SettingKey("composeDynamicColors")
    val amoledDark = SettingKey("amoledDark")
    val themeMode = SettingKey("themeMode")
    val interfaceStyle = SettingKey("interfaceStyle")
    val liquidGlassQuality = SettingKey("liquidGlassQuality")
    val routingPreset = SettingKey("routingPreset")
    val routingPresetOwnership = SettingKey("routingPresetOwnership")
    val websiteBypassEnabled = SettingKey("websiteBypassEnabled")
    val websiteBypassDomains = SettingKey("websiteBypassDomains")
    val showGroupInNotification = SettingKey("showGroupInNotification")
    val connectionTestUrl = SettingKey("connectionTestURL")
}

sealed interface StorageMigrationState {
    data object NotStarted : StorageMigrationState
    data object InProgress : StorageMigrationState
    data object Ready : StorageMigrationState
    data object LegacyFallback : StorageMigrationState

    data class Failed(val message: String) : StorageMigrationState {
        init {
            require(message.isNotBlank()) { "Migration failure message cannot be blank" }
        }
    }
}

/**
 * A lossless, typed settings snapshot. Known settings can be projected into stronger models later;
 * unknown keys remain in this map so a migration does not discard forward-compatible values.
 */
data class SettingsSnapshot(
    val values: Map<SettingKey, SettingValue> = emptyMap(),
    val migrationState: StorageMigrationState = StorageMigrationState.NotStarted,
) {
    operator fun get(key: SettingKey): SettingValue? = values[key]

    fun withValue(key: SettingKey, value: SettingValue): SettingsSnapshot =
        copy(values = values + (key to value))

    fun withoutValue(key: SettingKey): SettingsSnapshot =
        copy(values = values - key)
}
