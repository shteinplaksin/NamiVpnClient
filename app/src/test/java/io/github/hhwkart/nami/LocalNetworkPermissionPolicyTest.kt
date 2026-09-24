package io.github.hhwkart.nami

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkPermissionPolicyTest {
    @Test
    fun legacyAndroidHonorsLanSettingWithoutRuntimePermission() {
        assertTrue(
            LocalNetworkPermissionPolicy.shouldExposeToLan(
                allowAccess = true,
                sdkInt = 36,
                hasPermission = false,
            ),
        )
    }

    @Test
    fun android17RequiresPermissionEvenWhenLanSettingWasAlreadySaved() {
        assertFalse(
            LocalNetworkPermissionPolicy.shouldExposeToLan(
                allowAccess = true,
                sdkInt = 37,
                hasPermission = false,
            ),
        )
    }

    @Test
    fun android17ExposesLanAfterPermissionIsGranted() {
        assertTrue(
            LocalNetworkPermissionPolicy.shouldExposeToLan(
                allowAccess = true,
                sdkInt = 37,
                hasPermission = true,
            ),
        )
    }

    @Test
    fun generatedExportPreservesSavedLanBindingWithoutRuntimePermission() {
        assertTrue(
            LocalNetworkPermissionPolicy.shouldExposeToLan(
                allowAccess = true,
                sdkInt = 37,
                hasPermission = false,
                forExport = true,
            ),
        )
    }

    @Test
    fun generatedTestConfigStaysLoopbackEvenWhenLanIsSavedAndGranted() {
        assertFalse(
            LocalNetworkPermissionPolicy.shouldExposeToLan(
                allowAccess = true,
                sdkInt = 37,
                hasPermission = true,
                forTest = true,
            ),
        )
    }

    @Test
    fun disabledLanSettingNeverExposesInbound() {
        assertFalse(
            LocalNetworkPermissionPolicy.shouldExposeToLan(
                allowAccess = false,
                sdkInt = 37,
                hasPermission = true,
            ),
        )
    }

    @Test
    fun permissionGrantReloadsSavedLanAccessForAnActiveService() {
        assertTrue(
            LocalNetworkPermissionPolicy.shouldForceReloadServiceForPermissionChange(
                permissionWasGranted = false,
                permissionIsGranted = true,
                serviceStarted = true,
            ),
        )
    }

    @Test
    fun permissionChangeReloadsActiveServiceWhileLanSettingIsOff() {
        assertTrue(
            LocalNetworkPermissionPolicy.shouldForceReloadServiceForPermissionChange(
                permissionWasGranted = false,
                permissionIsGranted = true,
                serviceStarted = true,
            ),
        )
        assertTrue(
            LocalNetworkPermissionPolicy.shouldForceReloadServiceForPermissionChange(
                permissionWasGranted = true,
                permissionIsGranted = false,
                serviceStarted = true,
            ),
        )
    }

    @Test
    fun permissionChangeDoesNotReloadWhenServiceIsStopped() {
        assertFalse(
            LocalNetworkPermissionPolicy.shouldForceReloadServiceForPermissionChange(
                permissionWasGranted = false,
                permissionIsGranted = true,
                serviceStarted = false,
            ),
        )
    }

    @Test
    fun activityPermissionMonitorForcesOnlyOneReloadPerObservedTransition() {
        val transition = LocalNetworkPermissionTransition(initiallyGranted = false)

        assertTrue(
            transition.shouldForceReload(
                permissionIsGranted = true,
                serviceStarted = true,
                sdkInt = 37,
            ),
        )
        assertFalse(
            transition.shouldForceReload(
                permissionIsGranted = true,
                serviceStarted = true,
                sdkInt = 37,
            ),
        )
        assertTrue(
            transition.shouldForceReload(
                permissionIsGranted = false,
                serviceStarted = true,
                sdkInt = 37,
            ),
        )
    }

    @Test
    fun unknownRuntimeConfigIsRebuiltOnceOnAndroid17RegardlessOfPermissionState() {
        listOf(false, true).forEach { permissionGranted ->
            val transition = LocalNetworkPermissionTransition()

            assertTrue(
                transition.shouldForceReload(
                    permissionIsGranted = permissionGranted,
                    serviceStarted = true,
                    sdkInt = 37,
                ),
            )
            assertFalse(
                transition.shouldForceReload(
                    permissionIsGranted = permissionGranted,
                    serviceStarted = true,
                    sdkInt = 37,
                ),
            )
        }
    }

    @Test
    fun unknownRuntimeConfigIsRebuiltOnceWhenLanPreferenceIsOff() {
        listOf(false, true).forEach { permissionGranted ->
            val transition = LocalNetworkPermissionTransition()

            assertTrue(
                transition.shouldForceReload(
                    permissionIsGranted = permissionGranted,
                    serviceStarted = true,
                    sdkInt = 37,
                ),
            )
            assertFalse(
                transition.shouldForceReload(
                    permissionIsGranted = permissionGranted,
                    serviceStarted = true,
                    sdkInt = 37,
                ),
            )
        }
    }

    @Test
    fun unknownRuntimeConfigDoesNotForceRebuildOnAndroid16OrWithoutLanService() {
        assertFalse(
            LocalNetworkPermissionTransition().shouldForceReload(
                permissionIsGranted = false,
                serviceStarted = true,
                sdkInt = 36,
            ),
        )
        assertFalse(
            LocalNetworkPermissionTransition().shouldForceReload(
                permissionIsGranted = false,
                serviceStarted = false,
                sdkInt = 37,
            ),
        )
    }

    @Test
    fun unknownRuntimeConfigWaitsForSurvivingServiceConnectionBeforeReconcile() {
        val transition = LocalNetworkPermissionTransition()

        assertFalse(
            transition.shouldForceReload(
                permissionIsGranted = false,
                serviceStarted = false,
                sdkInt = 37,
            ),
        )
        assertTrue(
            transition.shouldForceReload(
                permissionIsGranted = false,
                serviceStarted = true,
                sdkInt = 37,
            ),
        )
        assertFalse(
            transition.shouldForceReload(
                permissionIsGranted = false,
                serviceStarted = true,
                sdkInt = 37,
            ),
        )
    }
}
