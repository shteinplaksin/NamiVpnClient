package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.Destination

enum class QuickSetupEntryPoint {
    INITIAL_ONBOARDING,
    PROFILES,
    SETTINGS_OR_ABOUT,
}

object QuickSetupReturnPolicy {
    fun destination(entryPoint: QuickSetupEntryPoint): Destination = when (entryPoint) {
        QuickSetupEntryPoint.PROFILES -> Destination.Profiles
        QuickSetupEntryPoint.INITIAL_ONBOARDING,
        QuickSetupEntryPoint.SETTINGS_OR_ABOUT,
        -> Destination.Home
    }
}
