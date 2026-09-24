package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickSetupReturnPolicyTest {

    @Test
    fun initialOnboardingReturnsHomeAfterCompletionOrCancel() {
        assertEquals(
            Destination.Home,
            QuickSetupReturnPolicy.destination(QuickSetupEntryPoint.INITIAL_ONBOARDING),
        )
    }

    @Test
    fun profilesEntryReturnsProfilesAfterCompletionOrCancel() {
        assertEquals(
            Destination.Profiles,
            QuickSetupReturnPolicy.destination(QuickSetupEntryPoint.PROFILES),
        )
    }

    @Test
    fun settingsOrAboutEntryReturnsHomeAfterCompletionOrCancel() {
        assertEquals(
            Destination.Home,
            QuickSetupReturnPolicy.destination(QuickSetupEntryPoint.SETTINGS_OR_ABOUT),
        )
    }
}
