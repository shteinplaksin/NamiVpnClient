package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSetupReturnPolicyTest {

    @Test
    fun initialOnboardingReturnsHomeAfterCompletionOrCancel() {
        assertEquals(
            QuickSetupReturnTarget.ToDestination(Destination.Home),
            QuickSetupReturnPolicy.target(QuickSetupEntryPoint.INITIAL_ONBOARDING, 1),
        )
    }

    @Test
    fun profilesEntryReturnsProfilesAfterCompletionOrCancel() {
        assertEquals(
            QuickSetupReturnTarget.ToDestination(Destination.Profiles),
            QuickSetupReturnPolicy.target(QuickSetupEntryPoint.PROFILES, 1),
        )
    }

    @Test
    fun settingsEntryReturnsToSettingsAfterCompletionOrCancel() {
        assertEquals(
            QuickSetupReturnTarget.ToDestination(Destination.Settings),
            QuickSetupReturnPolicy.target(QuickSetupEntryPoint.SETTINGS, 1),
        )
    }

    @Test
    fun aboutEntryReturnsToAboutAfterCompletionOrCancel() {
        assertEquals(
            QuickSetupReturnTarget.ToDestination(Destination.About),
            QuickSetupReturnPolicy.target(QuickSetupEntryPoint.ABOUT, 1),
        )
    }

    @Test
    fun legacyCombinedEntryPopsBackToTheRestoredOrigin() {
        assertEquals(
            QuickSetupReturnTarget.PopScreens(1),
            QuickSetupReturnPolicy.target(QuickSetupEntryPoint.SETTINGS_OR_ABOUT, 1),
        )
        assertEquals(
            QuickSetupReturnTarget.PopScreens(2),
            QuickSetupReturnPolicy.target(QuickSetupEntryPoint.SETTINGS_OR_ABOUT, 2),
        )
    }
}
