package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.Destination

enum class QuickSetupEntryPoint {
    INITIAL_ONBOARDING,
    PROFILES,
    SETTINGS,
    ABOUT,
    // Kept for activities restored from versions that used one shared entry point.
    SETTINGS_OR_ABOUT,
}

sealed interface QuickSetupReturnTarget {
    data class ToDestination(val destination: Destination) : QuickSetupReturnTarget
    data class PopScreens(val count: Int) : QuickSetupReturnTarget
}

object QuickSetupReturnPolicy {
    fun target(
        entryPoint: QuickSetupEntryPoint,
        legacyPopCount: Int,
    ): QuickSetupReturnTarget = when (entryPoint) {
        QuickSetupEntryPoint.PROFILES -> QuickSetupReturnTarget.ToDestination(Destination.Profiles)
        QuickSetupEntryPoint.SETTINGS -> QuickSetupReturnTarget.ToDestination(Destination.Settings)
        QuickSetupEntryPoint.ABOUT -> QuickSetupReturnTarget.ToDestination(Destination.About)
        QuickSetupEntryPoint.SETTINGS_OR_ABOUT ->
            QuickSetupReturnTarget.PopScreens(legacyPopCount.coerceAtLeast(1))
        QuickSetupEntryPoint.INITIAL_ONBOARDING ->
            QuickSetupReturnTarget.ToDestination(Destination.Home)
    }
}
