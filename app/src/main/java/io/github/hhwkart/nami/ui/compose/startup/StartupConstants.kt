package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.Destination

/** Bumped only when the What’s New content changes, never for every APK build. */
const val WHATS_NEW_CONTENT_VERSION = "phase5-1"

object WhatsNewPolicy {
    fun isDue(storedContentVersion: String?): Boolean =
        storedContentVersion != WHATS_NEW_CONTENT_VERSION
}

enum class WhatsNewEntryPoint {
    AUTOMATIC,
    SETTINGS,
    ABOUT,
}

object WhatsNewReturnPolicy {
    fun destination(entryPoint: WhatsNewEntryPoint) = when (entryPoint) {
        WhatsNewEntryPoint.AUTOMATIC -> Destination.Home
        WhatsNewEntryPoint.SETTINGS -> Destination.Settings
        WhatsNewEntryPoint.ABOUT -> Destination.About
    }
}
