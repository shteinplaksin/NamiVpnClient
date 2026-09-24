package io.github.hhwkart.nami.ui.compose.startup

/** Facts used to classify an installation without touching lazily-created defaults. */
data class InstallFacts(
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val hasUserCreatedProfiles: Boolean,
    val hasUserCreatedSubscriptions: Boolean,
)

enum class InstallState {
    FRESH_INSTALL,
    EXISTING_INSTALL,
}

/**
 * Keeps the first-run decision small and deterministic so it can be tested without Android.
 * Default groups and default routing rules are deliberately not part of [InstallFacts].
 */
object InstallStateDetector {

    fun detect(facts: InstallFacts): InstallState {
        val fresh = facts.firstInstallTime == facts.lastUpdateTime &&
            !facts.hasUserCreatedProfiles &&
            !facts.hasUserCreatedSubscriptions
        return if (fresh) InstallState.FRESH_INSTALL else InstallState.EXISTING_INSTALL
    }
}
