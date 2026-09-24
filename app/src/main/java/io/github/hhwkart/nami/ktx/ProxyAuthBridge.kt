package io.github.hhwkart.nami.ktx

import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.DataStore
import libcore.HTTPClient

/**
 * Route this client through the app's own authenticated local inbound.
 * Falls back to the legacy unauthenticated attempt (which then degrades to a
 * direct dial inside the Go client) when credentials are unknown, e.g. the
 * main process has no bound service — there is no inbound to talk to anyway.
 */
fun HTTPClient.applyLocalProxy() {
    val (user, pass) = SagerNet.localProxyAuth ?: run {
        trySocks5(DataStore.mixedPort)
        return
    }
    trySocks5WithAuth(DataStore.mixedPort, user, pass)
}
