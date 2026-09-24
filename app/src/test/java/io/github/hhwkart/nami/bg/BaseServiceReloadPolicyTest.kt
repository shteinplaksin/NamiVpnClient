package io.github.hhwkart.nami.bg

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseServiceReloadPolicyTest {
    @Test
    fun forcedPermissionChangeRebuildSkipsSelectorFastPath() {
        var selectorCheckExecuted = false
        assertFalse(
            BaseServiceReloadPolicy.canUseSelectorFastPath(
                forceConfigRebuild = true,
            ) {
                selectorCheckExecuted = true
                true
            },
        )
        assertFalse(selectorCheckExecuted)
    }

    @Test
    fun ordinaryReloadKeepsSelectorFastPath() {
        var selectorCheckExecuted = false
        assertTrue(
            BaseServiceReloadPolicy.canUseSelectorFastPath(
                forceConfigRebuild = false,
            ) {
                selectorCheckExecuted = true
                true
            },
        )
        assertTrue(selectorCheckExecuted)
    }
}
