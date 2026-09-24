package io.github.hhwkart.nami.ui.compose.startup

/** Bumped only when the What’s New content changes, never for every APK build. */
const val WHATS_NEW_CONTENT_VERSION = "phase5-1"

object WhatsNewPolicy {
    fun isDue(storedContentVersion: String?): Boolean =
        storedContentVersion != WHATS_NEW_CONTENT_VERSION
}
