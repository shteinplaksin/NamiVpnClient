package io.github.hhwkart.nami.ui.compose.startup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsNewPolicyTest {

    @Test
    fun missingMarkerIsDue() {
        assertTrue(WhatsNewPolicy.isDue(null))
        assertTrue(WhatsNewPolicy.isDue(""))
    }

    @Test
    fun onlyTheContentVersionEqualityMatters() {
        assertFalse(WhatsNewPolicy.isDue(WHATS_NEW_CONTENT_VERSION))
        assertTrue(WhatsNewPolicy.isDue("pre-1.4.2-20260202-1"))
        assertTrue(WhatsNewPolicy.isDue("1.4.2"))
        assertTrue(WhatsNewPolicy.isDue("phase5-0"))
    }
}
