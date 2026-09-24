package io.github.hhwkart.nami.bg.proto

import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProxyEntity

class UrlTest {

    val link = DataStore.connectionTestURL
    private val timeout = 5000

    suspend fun doTest(profile: ProxyEntity): Int {
        return TestInstance(profile, link, timeout).doTest()
    }

}