package io.github.hhwkart.nami.fmt

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawConfigLanPolicyTest {
    @Test
    fun restrictsRawSocketInboundsWhileKeepingTunAndOtherConfig() {
        val input = """
            {
              "log": {"level": "debug"},
              "inbounds": [
                {"type": "mixed", "listen": "0.0.0.0", "listen_port": 2080, "tag": "mixed"},
                {"type": "socks", "listen": "192.168.1.25", "listen_port": 1080},
                {"type": "http", "listen_port": 8080},
                {"type": "tun", "interface_name": "custom-tun"}
              ],
              "outbounds": [{"type": "direct", "tag": "direct"}]
            }
        """.trimIndent()

        val restricted = RawConfigLanPolicy.restrictInboundListenersToLocalhost(input)
        val json = JsonParser.parseString(restricted).asJsonObject
        val inbounds = json.getAsJsonArray("inbounds")

        assertEquals(LOCALHOST, inbounds[0].asJsonObject.get("listen").asString)
        assertEquals(2080, inbounds[0].asJsonObject.get("listen_port").asInt)
        assertEquals("mixed", inbounds[0].asJsonObject.get("tag").asString)
        assertEquals(LOCALHOST, inbounds[1].asJsonObject.get("listen").asString)
        assertEquals(LOCALHOST, inbounds[2].asJsonObject.get("listen").asString)
        assertFalse(inbounds[3].asJsonObject.has("listen"))
        assertEquals("debug", json.getAsJsonObject("log").get("level").asString)
        assertEquals("direct", json.getAsJsonArray("outbounds")[0].asJsonObject.get("tag").asString)
    }

    @Test
    fun failsClosedWhenRawInboundShapeCannotBeRestricted() {
        val error = try {
            RawConfigLanPolicy.restrictInboundListenersToLocalhost(
                """{"inbounds":{"type":"mixed","listen":"0.0.0.0"}}""",
            )
            null
        } catch (exception: IllegalArgumentException) {
            exception
        }

        assertTrue(error?.message.orEmpty().contains("inbounds field must be a JSON array"))
    }

    @Test
    fun returnsConfigWithoutInboundsUnchanged() {
        val input = """{
          "log": {"level": "warn"},
          "outbounds": []
        }"""

        assertEquals(input, RawConfigLanPolicy.restrictInboundListenersToLocalhost(input))
    }

    @Test
    fun returnsAlreadyLocalListenersUnchanged() {
        val input = """
            {
              "inbounds": [{"type": "mixed", "listen": "127.0.0.1", "listen_port": 2080}],
              "experimental": {
                "clash_api": {"external_controller": "localhost:9090"},
                "v2ray_api": {"listen": "[::1]:8080"},
                "debug": {"listen": "127.0.0.2:6060"}
              }
            }
        """.trimIndent()

        assertEquals(input, RawConfigLanPolicy.restrictInboundListenersToLocalhost(input))
    }

    @Test
    fun restrictsMixedCaseRawListenerKeysAndRecognizesMixedCaseTunType() {
        val input = """
            {
              "INBOUNDS": [
                {"TYPE": "mixed", "Listen": "0.0.0.0", "listen_port": 2080},
                {"TYPE": "tun", "interface_name": "custom-tun"}
              ],
              "EXPERIMENTAL": {
                "CLASH_API": {"External_Controller": "192.168.1.4:9090", "external_ui": "dashboards/custom"},
                "V2RAY_API": {"Listen": "[::]:8080"},
                "DEBUG": {"LISTEN": "0.0.0.0:6060"}
              }
            }
        """.trimIndent()

        val json = JsonParser.parseString(
            RawConfigLanPolicy.restrictInboundListenersToLocalhost(input),
        ).asJsonObject
        val inbounds = json.getAsJsonArray("INBOUNDS")
        val experimental = json.getAsJsonObject("EXPERIMENTAL")

        assertEquals(LOCALHOST, inbounds[0].asJsonObject.get("Listen").asString)
        assertEquals(2080, inbounds[0].asJsonObject.get("listen_port").asInt)
        assertFalse(inbounds[1].asJsonObject.has("listen"))
        assertFalse(inbounds[1].asJsonObject.has("Listen"))
        assertEquals(
            "127.0.0.1:9090",
            experimental.getAsJsonObject("CLASH_API").get("External_Controller").asString,
        )
        assertEquals(
            "dashboards/custom",
            experimental.getAsJsonObject("CLASH_API").get("external_ui").asString,
        )
        assertEquals(
            "127.0.0.1:8080",
            experimental.getAsJsonObject("V2RAY_API").get("Listen").asString,
        )
        assertEquals(
            "127.0.0.1:6060",
            experimental.getAsJsonObject("DEBUG").get("LISTEN").asString,
        )
    }

    @Test
    fun failsClosedOnAmbiguousCaseInsensitiveListenerKeys() {
        val ambiguousConfigs = listOf(
            """{"inbounds":[],"INBOUNDS":[]}""",
            """{"inbounds":[{"type":"mixed","listen":"0.0.0.0","Listen":"192.168.1.4"}]}""",
            """{"inbounds":[{"type":"tun","TYPE":"mixed"}]}""",
            """{"experimental":{"clash_api":{"external_controller":"127.0.0.1:9090","External_Controller":"0.0.0.0:9090"}}}""",
            """{"experimental":{"clash_api":{"external_controller":"0.0.0.0:9090","external_controller":null}}}""",
            """{"experimental":{"clash_api":{"external_controller":"0.0.0.0:9090"},"v2ray_api":null},"experimental":{"clash_api":{"external_controller":null}}}""",
            """{"experimental":{"clash_api":{"external_controller":"0.0.0.0:9090","external_\u0063ontroller":null}}}""",
        )

        ambiguousConfigs.forEach { config ->
            val error = try {
                RawConfigLanPolicy.restrictInboundListenersToLocalhost(config)
                null
            } catch (error: IllegalArgumentException) {
                error
            }
            assertTrue("Expected ambiguous listener keys to fail closed for: $config", error != null)
        }
    }

    @Test
    fun preservesRawConfigVerbatimForExportWithoutCheckingRuntimePermission() {
        val input = """{
          "inbounds": [{"type": "mixed", "listen": "0.0.0.0", "listen_port": 2080}]
        }"""
        var permissionChecked = false

        val exported = RawConfigLanPolicy.configForBuild(
            config = input,
            forExport = true,
        ) {
            permissionChecked = true
            false
        }

        assertEquals(input, exported)
        assertFalse(permissionChecked)
    }

    @Test
    fun runtimeRawConfigIsRestrictedOnlyWithoutPermission() {
        val input = """{"inbounds":[{"type":"mixed","listen":"0.0.0.0","listen_port":2080}]}"""

        val restricted = RawConfigLanPolicy.configForBuild(
            config = input,
            forExport = false,
        ) { false }
        val unchanged = RawConfigLanPolicy.configForBuild(
            config = input,
            forExport = false,
        ) { true }

        val restrictedInbound = JsonParser.parseString(restricted).asJsonObject
            .getAsJsonArray("inbounds")[0].asJsonObject
        assertEquals(LOCALHOST, restrictedInbound.get("listen").asString)
        assertEquals(input, unchanged)
    }

    @Test
    fun restrictsExperimentalApiListenersEvenWhenConfigHasNoInbounds() {
        val input = """
            {
              "experimental": {
                "clash_api": {
                  "external_controller": "0.0.0.0:9090",
                  "external_ui": "dashboards/custom"
                },
                "v2ray_api": {"listen": "192.168.1.25:8080", "stats": {"enabled": true}},
                "debug": {"listen": "[::]:6060", "gc_percent": 100}
              },
              "outbounds": [{"type": "direct"}]
            }
        """.trimIndent()

        val restricted = RawConfigLanPolicy.restrictInboundListenersToLocalhost(input)
        val json = JsonParser.parseString(restricted).asJsonObject
        val experimental = json.getAsJsonObject("experimental")

        assertEquals(
            "127.0.0.1:9090",
            experimental.getAsJsonObject("clash_api").get("external_controller").asString,
        )
        assertEquals(
            "dashboards/custom",
            experimental.getAsJsonObject("clash_api").get("external_ui").asString,
        )
        assertEquals(
            "127.0.0.1:8080",
            experimental.getAsJsonObject("v2ray_api").get("listen").asString,
        )
        assertEquals(
            "127.0.0.1:6060",
            experimental.getAsJsonObject("debug").get("listen").asString,
        )
        assertTrue(experimental.getAsJsonObject("v2ray_api").getAsJsonObject("stats").get("enabled").asBoolean)
        assertEquals("direct", json.getAsJsonArray("outbounds")[0].asJsonObject.get("type").asString)
    }

    @Test
    fun preservesExperimentalListenerPortsSchemesAndPathSuffixes() {
        val input = """
            {
              "experimental": {
                "clash_api": {"external_controller": "tcp://[::]:9091/api"},
                "v2ray_api": {"listen": "[::]:8081"}
              }
            }
        """.trimIndent()

        val json = JsonParser.parseString(
            RawConfigLanPolicy.restrictInboundListenersToLocalhost(input),
        ).asJsonObject
        val experimental = json.getAsJsonObject("experimental")

        assertEquals(
            "tcp://127.0.0.1:9091/api",
            experimental.getAsJsonObject("clash_api").get("external_controller").asString,
        )
        assertEquals(
            "127.0.0.1:8081",
            experimental.getAsJsonObject("v2ray_api").get("listen").asString,
        )
    }
}
