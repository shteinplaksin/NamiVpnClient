package io.github.hhwkart.nami.ui.compose

import kotlinx.serialization.Serializable

/** Type-safe destinations. No string routes anywhere. */
@Serializable
sealed interface Destination {

    @Serializable
    data object Home : Destination

    @Serializable
    data object Profiles : Destination

    @Serializable
    data object Groups : Destination

    @Serializable
    data object Routing : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object Onboarding : Destination

    @Serializable
    data object QuickSetup : Destination

    @Serializable
    data object WhatsNew : Destination

    @Serializable
    data object PerAppProxy : Destination

    @Serializable
    data object Tools : Destination

    @Serializable
    data object Log : Destination

    @Serializable
    data object About : Destination

    @Serializable
    data object Dashboard : Destination

    @Serializable
    data object AddImport : Destination

    @Serializable
    data object QrScanner : Destination

    @Serializable
    data object BackupRestore : Destination

    @Serializable
    data object StunTest : Destination

    @Serializable
    data object RouteAssets : Destination

    @Serializable
    data class ProtocolEditor(
        val profileId: Long,
        val protocolType: String,
        val groupId: Long = 0L,
    ) : Destination

    @Serializable
    data class GroupEditor(val groupId: Long) : Destination

    @Serializable
    data class RouteEditor(val ruleId: Long) : Destination
}
