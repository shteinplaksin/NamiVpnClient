package io.github.hhwkart.nami.ui.compose.routing

import io.github.hhwkart.nami.database.RuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingJsonTransferTest {

    @Test
    fun profileOutboundIsNormalizedToProxy() {
        val export = RoutingJsonTransfer.export(
            listOf(
                RuleEntity(domains = "example.com", outbound = 42L),
            ),
        )

        assertEquals(1, export.normalizedProfileOutboundCount)
        assertTrue(export.content.contains("\"outbound\": \"proxy\""))
        assertTrue(!export.content.contains("42"))
    }

    @Test
    fun stableOutboundKindsRoundTrip() {
        val export = RoutingJsonTransfer.export(
            listOf(
                RuleEntity(domains = "proxy.example", outbound = 0L),
                RuleEntity(domains = "direct.example", outbound = -1L),
                RuleEntity(domains = "blocked.example", outbound = -2L),
            ),
        )

        val decoded = RoutingJsonTransfer.decode(export.content)
        assertEquals(listOf(0L, -1L, -2L), decoded.map { it.outbound })
    }

    @Test
    fun fullPortableRuleRoundTripPreservesRoutingFieldsWithoutLocalIdentity() {
        val original = RuleEntity(
            id = 91L,
            name = "compound rule",
            config = "mode=all",
            userOrder = 17L,
            enabled = false,
            domains = "example.com\nexample.org",
            ip = "192.0.2.1/32",
            port = "443",
            sourcePort = "1000-2000",
            network = "tcp,udp",
            source = "10.0.0.0/8",
            protocol = "tls",
            outbound = -1L,
            packages = setOf("com.example.z", "com.example.a"),
        )

        val export = RoutingJsonTransfer.export(listOf(original))
        val decoded = RoutingJsonTransfer.decode(export.content).single()

        assertEquals(0L, decoded.id)
        assertEquals(0L, decoded.userOrder)
        assertEquals(original.name, decoded.name)
        assertEquals(original.config, decoded.config)
        assertEquals(original.enabled, decoded.enabled)
        assertEquals(original.domains, decoded.domains)
        assertEquals(original.ip, decoded.ip)
        assertEquals(original.port, decoded.port)
        assertEquals(original.sourcePort, decoded.sourcePort)
        assertEquals(original.network, decoded.network)
        assertEquals(original.source, decoded.source)
        assertEquals(original.protocol, decoded.protocol)
        assertEquals(original.packages, decoded.packages)
        assertEquals(original.outbound, decoded.outbound)
        assertTrue(!export.content.contains("\"id\""))
        assertTrue(!export.content.contains("\"userOrder\""))
    }

    @Test
    fun numericOutboundIsRejectedOnImport() {
        val json = """
            {
              "schemaVersion": 1,
              "rules": [{"domains":"example.com","outbound":123}]
            }
        """.trimIndent()

        val error = runCatching { RoutingJsonTransfer.decode(json) }.exceptionOrNull()
        assertTrue(error is RoutingJsonException)
    }

    @Test
    fun invalidRuleIsRejectedBeforeDatabaseMutation() {
        val json = """
            {
              "schemaVersion": 1,
              "rules": [{"domains":"","outbound":"proxy"}]
            }
        """.trimIndent()

        val error = runCatching { RoutingJsonTransfer.decode(json) }.exceptionOrNull()
        assertTrue(error is RoutingJsonException)
    }

    @Test
    fun ownershipFingerprintIsOrderStableForPackages() {
        val first = RuleEntity(domains = "example.com", packages = setOf("b", "a"))
        val second = RuleEntity(domains = "example.com", packages = setOf("a", "b"))

        assertEquals(
            RoutingPresetManager.fingerprint(first),
            RoutingPresetManager.fingerprint(second),
        )
    }
}
