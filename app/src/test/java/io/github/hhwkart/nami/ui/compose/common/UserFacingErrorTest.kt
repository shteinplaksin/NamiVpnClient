package io.github.hhwkart.nami.ui.compose.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserFacingErrorTest {

    @Test
    fun mapsNetworkFailuresToRetryableNetworkErrors() {
        val error = UserFacingError.fromThrowable(
            IllegalStateException("connection timeout"),
            "https://example.com/subscription",
        )

        assertEquals(UserFacingErrorKind.NETWORK, error.kind)
        assertTrue(error.displayText.contains("https://example.com/subscription"))
        assertTrue(error.displayText.contains("try again"))
    }

    @Test
    fun validationErrorsKeepTheirSpecificHint() {
        val error = UserFacingError(
            kind = UserFacingErrorKind.VALIDATION,
            message = "Enter a valid URL.",
            hint = "Use HTTPS.",
        )

        assertEquals("Enter a valid URL.\nUse HTTPS.", error.displayText)
    }
}
