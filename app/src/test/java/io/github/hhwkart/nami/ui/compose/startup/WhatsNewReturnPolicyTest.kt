package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.Destination
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewReturnPolicyTest {
    @Test
    fun automaticNewsReturnsHome() {
        assertEquals(
            Destination.Home,
            WhatsNewReturnPolicy.destination(WhatsNewEntryPoint.AUTOMATIC),
        )
    }

    @Test
    fun settingsNewsReturnsToSettings() {
        assertEquals(
            Destination.Settings,
            WhatsNewReturnPolicy.destination(WhatsNewEntryPoint.SETTINGS),
        )
    }

    @Test
    fun aboutNewsReturnsToAbout() {
        assertEquals(
            Destination.About,
            WhatsNewReturnPolicy.destination(WhatsNewEntryPoint.ABOUT),
        )
    }
}
