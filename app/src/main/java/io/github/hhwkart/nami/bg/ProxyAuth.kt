package io.github.hhwkart.nami.bg

import java.util.UUID

/**
 * Credentials for the local mixed (SOCKS5+HTTP) inbound.
 *
 * Generated randomly at every fresh service start and kept only in the memory
 * of the :bg process (never persisted, never in default-mode SharedPreferences,
 * never in world-readable files). sing-box verifies them for every inbound
 * connection, so other apps on the device cannot use the tunnel without them.
 */
object ProxyAuth {

    @Volatile
    var username: String = ""
        private set

    @Volatile
    var password: String = ""
        private set

    val valid: Boolean get() = username.isNotBlank() && password.isNotBlank()

    /**
     * Generate fresh credentials for this service start.
     * Fail closed: returns false (inbound must not start) on any failure.
     */
    fun generate(): Boolean {
        return try {
            username = "neko-" + UUID.randomUUID().toString().substring(0, 8)
            password = UUID.randomUUID().toString().replace("-", "")
            valid
        } catch (_: Exception) {
            username = ""
            password = ""
            false
        }
    }

    fun reset() {
        username = ""
        password = ""
    }
}
