package io.github.hhwkart.nami.bg

internal object BaseServiceReloadPolicy {
    fun canUseSelectorFastPath(
        forceConfigRebuild: Boolean,
        selectorCanReload: () -> Boolean,
    ): Boolean = !forceConfigRebuild && selectorCanReload()
}
