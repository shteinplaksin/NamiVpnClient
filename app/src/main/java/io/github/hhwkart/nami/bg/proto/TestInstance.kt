package io.github.hhwkart.nami.bg.proto

import io.github.hhwkart.nami.BuildConfig
import io.github.hhwkart.nami.bg.GuardedProcessPool
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.fmt.buildConfig
import io.github.hhwkart.nami.ktx.Logs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import libcore.Libcore
import io.github.hhwkart.nami.core.net.LocalResolverImpl

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(): Int = withContext(Dispatchers.Default) {
        processes = GuardedProcessPool { Logs.w(it) }
        use {
            init()
            launch()
            if (processes.processCount > 0) {
                // Wait for external plugins to finish starting.
                delay(500)
            }
            Libcore.urlTest(box, link, timeout)
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}
