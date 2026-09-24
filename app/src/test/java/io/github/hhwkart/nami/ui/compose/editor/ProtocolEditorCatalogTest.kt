package io.github.hhwkart.nami.ui.compose.editor

import io.github.hhwkart.nami.fmt.hysteria.HysteriaBean
import io.github.hhwkart.nami.fmt.wireguard.WireGuardBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolEditorCatalogTest {

    @Test
    fun hysteriaHasNoDuplicateVisibleFieldKeys() {
        val base = ProtocolEditorCatalog.beanToForm(
            ProtocolKind.HYSTERIA,
            ProtocolEditorCatalog.createBean(ProtocolKind.HYSTERIA),
        )
        listOf(
            base + ("protocolVersion" to "1") + ("serverAuthType" to "0"),
            base + ("protocolVersion" to "1") + ("serverAuthType" to "1"),
            base + ("protocolVersion" to "2"),
        ).forEach { form ->
            val visibleKeys = ProtocolEditorCatalog.specs(ProtocolKind.HYSTERIA)
                .filter { it.isVisible(form) }
                .map { it.key }
            assertEquals(visibleKeys.distinct(), visibleKeys)
        }
    }

    @Test
    fun hysteriaUsesMultiPortValidationInsteadOfHiddenSinglePort() {
        val form = ProtocolEditorCatalog.beanToForm(
            ProtocolKind.HYSTERIA2,
            ProtocolEditorCatalog.createBean(ProtocolKind.HYSTERIA2),
        ).toMutableMap()
        form[FieldKeys.SERVER_PORT] = "0"
        form["serverPorts"] = "443,8443-8445"
        assertFalse(ProtocolEditorCatalog.validate(ProtocolKind.HYSTERIA2, form)
            .containsKey(FieldKeys.SERVER_PORT))
        assertFalse(ProtocolEditorCatalog.validate(ProtocolKind.HYSTERIA2, form)
            .containsKey("serverPorts"))

        form["serverPorts"] = "70000"
        assertTrue(ProtocolEditorCatalog.validate(ProtocolKind.HYSTERIA2, form)
            .containsKey("serverPorts"))
    }

    @Test
    fun protocolDefaultsRemainVersionSpecific() {
        val hysteria = ProtocolEditorCatalog.createBean(ProtocolKind.HYSTERIA) as HysteriaBean
        val hysteria2 = ProtocolEditorCatalog.createBean(ProtocolKind.HYSTERIA2) as HysteriaBean
        assertEquals(1, hysteria.protocolVersion)
        assertEquals(2, hysteria2.protocolVersion)
        assertEquals("10", ProtocolEditorCatalog.beanToForm(ProtocolKind.HYSTERIA, hysteria)["serverUploadSpeed"])
        assertEquals("0", ProtocolEditorCatalog.beanToForm(ProtocolKind.HYSTERIA2, hysteria2)["serverUploadSpeed"])
    }

    @Test
    fun blankWireGuardAllowedIpsAreNormalizedLosslessly() {
        val bean = (ProtocolEditorCatalog.createBean(ProtocolKind.WIREGUARD) as WireGuardBean).apply {
            allowedIPs = ""
        }
        val form = ProtocolEditorCatalog.beanToForm(ProtocolKind.WIREGUARD, bean)
        assertEquals("0.0.0.0/0,::/0", form["allowedIPs"])
        val saved = ProtocolEditorCatalog.applyForm(ProtocolKind.WIREGUARD, bean, form) as WireGuardBean
        assertEquals("0.0.0.0/0,::/0", saved.allowedIPs)
    }

    @Test
    fun everyBuiltInEditorHasUniqueFieldKeysAndSharedJsonParity() {
        ProtocolKind.entries.filter { it.selectableForNew }.forEach { kind ->
            val keys = ProtocolEditorCatalog.specs(kind).map { it.key }
            assertEquals("Duplicate field in ${kind.name}", keys.distinct(), keys)
            assertTrue("Missing custom outbound JSON in ${kind.name}", FieldKeys.CUSTOM_OUTBOUND_JSON in keys)
            assertTrue("Missing custom config JSON in ${kind.name}", FieldKeys.CUSTOM_CONFIG_JSON in keys)
        }
    }

    @Test
    fun sharedJsonFieldsRoundTripForEveryBuiltInProtocol() {
        ProtocolKind.entries.filter { it.selectableForNew }.forEach { kind ->
            val bean = ProtocolEditorCatalog.createBean(kind)
            val form = ProtocolEditorCatalog.beanToForm(kind, bean).toMutableMap().apply {
                put(FieldKeys.CUSTOM_OUTBOUND_JSON, "{\"tag\":\"out\"}")
                put(FieldKeys.CUSTOM_CONFIG_JSON, "{\"experimental\":{}}")
            }
            val saved = ProtocolEditorCatalog.applyForm(kind, bean, form)
            assertEquals("${kind.name} outbound JSON", "{\"tag\":\"out\"}", saved.customOutboundJson)
            assertEquals("${kind.name} config JSON", "{\"experimental\":{}}", saved.customConfigJson)
        }
    }

    @Test
    fun everyDeclaredFieldSurvivesDefaultBeanRoundTrip() {
        ProtocolKind.entries.filter { it.selectableForNew }.forEach { kind ->
            val bean = ProtocolEditorCatalog.createBean(kind)
            val before = ProtocolEditorCatalog.beanToForm(kind, bean)
            val after = ProtocolEditorCatalog.beanToForm(
                kind,
                ProtocolEditorCatalog.applyForm(kind, bean, before),
            )
            ProtocolEditorCatalog.specs(kind).forEach { field ->
                assertEquals(
                    "${kind.name}.${field.key}",
                    before[field.key] ?: field.defaultValue,
                    after[field.key] ?: field.defaultValue,
                )
            }
        }
    }
}
