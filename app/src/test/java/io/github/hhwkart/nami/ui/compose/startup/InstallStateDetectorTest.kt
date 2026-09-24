package io.github.hhwkart.nami.ui.compose.startup

import org.junit.Assert.assertEquals
import org.junit.Test

class InstallStateDetectorTest {

    @Test
    fun equalInstallTimesAndNoUserDataAreFresh() {
        assertEquals(
            InstallState.FRESH_INSTALL,
            InstallStateDetector.detect(InstallFacts(10L, 10L, false, false)),
        )
    }

    @Test
    fun lazilyCreatedDefaultsDoNotChangeFreshClassification() {
        assertEquals(
            InstallState.FRESH_INSTALL,
            InstallStateDetector.detect(InstallFacts(10L, 10L, false, false)),
        )
    }

    @Test
    fun profileMakesEqualTimestampInstallExisting() {
        assertEquals(
            InstallState.EXISTING_INSTALL,
            InstallStateDetector.detect(InstallFacts(10L, 10L, true, false)),
        )
    }

    @Test
    fun subscriptionMakesEqualTimestampInstallExisting() {
        assertEquals(
            InstallState.EXISTING_INSTALL,
            InstallStateDetector.detect(InstallFacts(10L, 10L, false, true)),
        )
    }

    @Test
    fun updatedPackageIsExistingEvenWithoutUserRows() {
        assertEquals(
            InstallState.EXISTING_INSTALL,
            InstallStateDetector.detect(InstallFacts(10L, 11L, false, false)),
        )
    }

    @Test
    fun inconsistentPackageMetadataIsConservative() {
        assertEquals(
            InstallState.EXISTING_INSTALL,
            InstallStateDetector.detect(InstallFacts(11L, 10L, false, false)),
        )
    }
}
