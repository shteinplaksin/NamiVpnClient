package io.github.hhwkart.nami.data.settings

import io.github.hhwkart.nami.database.preference.KeyValuePair
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

sealed interface LegacyPreferenceValue {
    data class BooleanValue(val value: Boolean) : LegacyPreferenceValue
    data class FloatValue(val value: Float) : LegacyPreferenceValue
    data class IntValue(val value: Int) : LegacyPreferenceValue
    data class LongValue(val value: Long) : LegacyPreferenceValue
    data class StringValue(val value: String) : LegacyPreferenceValue
    data class StringSetValue(val value: Set<String>) : LegacyPreferenceValue
}

data class DecodedLegacyPreference(
    val key: String,
    val value: LegacyPreferenceValue,
)

class LegacyPreferenceDecodeException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** Decodes the existing Room KeyValuePair wire representation without mutating it. */
object LegacyPreferenceCodec {

    @Suppress("DEPRECATION")
    fun decode(row: KeyValuePair): DecodedLegacyPreference {
        if (row.key.isBlank()) {
            throw LegacyPreferenceDecodeException("Legacy preference key cannot be blank")
        }

        val value = try {
            when (row.valueType) {
                KeyValuePair.TYPE_BOOLEAN -> LegacyPreferenceValue.BooleanValue(
                    requireNotNull(row.boolean) { "Boolean value is missing" },
                )

                KeyValuePair.TYPE_FLOAT -> LegacyPreferenceValue.FloatValue(
                    requireNotNull(row.float) { "Float value is missing" },
                )

                @Suppress("DEPRECATION")
                KeyValuePair.TYPE_INT -> LegacyPreferenceValue.IntValue(
                    requireNotNull(row.int) { "Int value is missing" },
                )

                KeyValuePair.TYPE_LONG -> LegacyPreferenceValue.LongValue(
                    requireNotNull(row.long) { "Long value is missing" },
                )

                KeyValuePair.TYPE_STRING -> LegacyPreferenceValue.StringValue(
                    requireNotNull(row.string) { "String value is missing" },
                )

                KeyValuePair.TYPE_STRING_SET -> LegacyPreferenceValue.StringSetValue(
                    decodeStringSet(row.value),
                )

                else -> throw LegacyPreferenceDecodeException(
                    "Unsupported legacy preference type ${row.valueType} for '${row.key}'",
                )
            }
        } catch (error: LegacyPreferenceDecodeException) {
            throw error
        } catch (error: Throwable) {
            throw LegacyPreferenceDecodeException(
                "Could not decode legacy preference '${row.key}'",
                error,
            )
        }

        return DecodedLegacyPreference(row.key, value)
    }

    /**
     * The legacy writer stored UTF-16 character counts but appended UTF-8 bytes.
     * ASCII values therefore use the declared length, while some Unicode values
     * require recovering the valid UTF-8 boundary instead. A failed recovery is
     * surfaced to DataStore migration rather than silently dropping settings.
     */
    private fun decodeStringSet(bytes: ByteArray): Set<String> {
        if (bytes.isEmpty()) return emptySet()

        val memo = HashMap<Int, List<String>?>()
        val values = parseStringSet(bytes, 0, memo)
            ?: throw LegacyPreferenceDecodeException("Malformed legacy string-set bytes")
        return values.toSet()
    }

    private fun parseStringSet(
        bytes: ByteArray,
        offset: Int,
        memo: MutableMap<Int, List<String>?>,
    ): List<String>? {
        memo[offset]?.let { return it }
        if (offset == bytes.size) return emptyList()
        if (offset + Int.SIZE_BYTES > bytes.size) return null

        val declaredLength = ByteBuffer.wrap(bytes, offset, Int.SIZE_BYTES).int
        if (declaredLength < 0) return null

        val start = offset + Int.SIZE_BYTES
        val preferredEnd = start + declaredLength
        val candidates = buildList {
            if (preferredEnd in start..bytes.size) add(preferredEnd)
            for (end in start..bytes.size) {
                if (end != preferredEnd) add(end)
            }
        }

        for (end in candidates) {
            val value = decodeUtf8(bytes.copyOfRange(start, end)) ?: continue
            val tail = parseStringSet(bytes, end, memo) ?: continue
            return listOf(value) + tail
        }

        memo[offset] = null
        return null
    }

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
