package io.github.hhwkart.nami.data.serialization

import io.github.hhwkart.nami.domain.model.BackupFormatVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupJsonCodecTest {
    @Test
    fun `decodes legacy v1 fixture and preserves absent sections`() {
        val document = BackupJsonCodec.decode("""
            {"version":1,"profiles":["cHJvZmlsZQ=="],"settings":["7b2a"]}
        """.trimIndent())

        assertEquals(BackupFormatVersion.LegacyV1, document.version)
        assertEquals(listOf("cHJvZmlsZQ=="), document.profiles)
        assertNull(document.groups)
        assertNull(document.rules)
        assertEquals(listOf("7b2a"), document.settings)
        assertEquals(2, document.presentSectionCount)
    }

    @Test
    fun `encodes deterministic current v2 output and round trips opaque payloads`() {
        val original = BackupJsonDocument(
            version = BackupFormatVersion.CurrentV2,
            profiles = listOf("opaque+/=", "ёж-данные"),
            groups = emptyList(),
            rules = null,
            settings = listOf("AAAA\\nBBBB"),
        )

        val encoded = BackupJsonCodec.encode(original)
        assertEquals(
            """
            {
                "version": 2,
                "profiles": [
                    "opaque+/=",
                    "ёж-данные"
                ],
                "groups": [],
                "settings": [
                    "AAAA\\nBBBB"
                ]
            }
            """.trimIndent(),
            encoded,
        )
        assertEquals(original.copy(version = BackupFormatVersion.CurrentV2), BackupJsonCodec.decode(encoded))
    }

    @Test
    fun rejectsEncodingAsLegacyV1() {
        assertThrows(BackupJsonCodecException::class.java) {
            BackupJsonCodec.encode(
                BackupJsonDocument(version = BackupFormatVersion.LegacyV1, profiles = emptyList()),
            )
        }
    }

    @Test
    fun `ignores unknown keys`() {
        val document = BackupJsonCodec.decode("""
            {"version":2,"future":{"nested":true},"profiles":[],"unknown":[1,2,3]}
        """.trimIndent())

        assertEquals(emptyList<String>(), document.profiles)
        assertEquals(1, document.presentSectionCount)
    }

    @Test
    fun `accepts missing sections`() {
        val document = BackupJsonCodec.decode("{\"version\":2}")

        assertNull(document.profiles)
        assertNull(document.groups)
        assertNull(document.rules)
        assertNull(document.settings)
        assertEquals(0, document.totalPayloadCount)
    }

    @Test
    fun `rejects missing and unsupported versions`() {
        assertThrows(BackupJsonCodecException::class.java) { BackupJsonCodec.decode("{}") }
        assertThrows(BackupJsonCodecException::class.java) { BackupJsonCodec.decode("{\"version\":3}") }
        assertThrows(BackupJsonCodecException::class.java) { BackupJsonCodec.decode("{\"version\":\"2\"}") }
    }

    @Test
    fun `rejects malformed root and non string items`() {
        assertThrows(BackupJsonCodecException::class.java) { BackupJsonCodec.decode("not json") }
        assertThrows(BackupJsonCodecException::class.java) { BackupJsonCodec.decode("[]") }
        assertThrows(BackupJsonCodecException::class.java) {
            BackupJsonCodec.decode("{\"version\":2,\"profiles\":[42]}")
        }
        assertThrows(BackupJsonCodecException::class.java) {
            BackupJsonCodec.decode("{\"version\":2,\"profiles\":\"not-array\"}")
        }
    }

    @Test
    fun `preserves empty opaque strings and unicode`() {
        val payloads = listOf("", "日本語", "😀", "+/=", "line\nnext")
        val decoded = BackupJsonCodec.decode(BackupJsonCodec.encode(BackupJsonDocument(
            version = BackupFormatVersion.CurrentV2,
            profiles = payloads,
        )))

        assertEquals(payloads, decoded.profiles)
        assertFalse(decoded.groups != null)
    }
}
