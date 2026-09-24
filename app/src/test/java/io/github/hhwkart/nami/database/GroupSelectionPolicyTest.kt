package io.github.hhwkart.nami.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupSelectionPolicyTest {
    @Test
    fun clearingAnotherGroupKeepsTheSelectedProfile() {
        assertFalse(shouldResetSelectedProxy(17L, setOf(23L, 24L)))
    }

    @Test
    fun clearingTheSelectedProfilesGroupResetsItsSelection() {
        assertTrue(shouldResetSelectedProxy(17L, setOf(17L, 23L)))
    }

    @Test
    fun aSelectionChangedToAnotherProfileBeingClearedIsAlsoReset() {
        assertTrue(shouldResetSelectedProxy(23L, setOf(17L, 23L)))
    }

    @Test
    fun aSelectionChangedToAProfileOutsideTheClearedGroupIsKept() {
        assertFalse(shouldResetSelectedProxy(29L, setOf(17L, 23L)))
    }

    @Test
    fun failedClearCanRestoreOnlyAnUnchangedEmptySelection() {
        assertTrue(shouldRestoreSelectedProxyAfterFailedClear(17L, 0L, true))
        assertFalse(shouldRestoreSelectedProxyAfterFailedClear(17L, 23L, true))
        assertFalse(shouldRestoreSelectedProxyAfterFailedClear(17L, 0L, false))
    }
}
