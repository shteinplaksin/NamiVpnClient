package io.github.hhwkart.nami.data.serialization

import io.github.hhwkart.nami.domain.model.BackupFormatVersion
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A parsed backup document whose section payloads remain opaque to this boundary. */
data class BackupJsonDocument(
    val version: BackupFormatVersion,
    val profiles: List<String>? = null,
    val groups: List<String>? = null,
    val rules: List<String>? = null,
    val settings: List<String>? = null,
) {
    val presentSectionCount: Int
        get() = listOf(profiles, groups, rules, settings).count { it != null }

    val totalPayloadCount: Int
        get() = listOfNotNull(profiles, groups, rules, settings).sumOf(List<String>::size)
}

class BackupJsonCodecException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** Stable JSON boundary for legacy v1 and current v2 backup envelopes. */
object BackupJsonCodec {
    private val parser = Json { ignoreUnknownKeys = true }

    fun decode(input: String): BackupJsonDocument {
        val root = try {
            parser.parseToJsonElement(input)
        } catch (error: Exception) {
            throw BackupJsonCodecException("Backup JSON is malformed", error)
        }

        val objectRoot = root as? JsonObject
            ?: throw BackupJsonCodecException("Backup JSON root must be an object")
        val version = objectRoot[VERSION_KEY]?.let { element ->
            val primitive = element as? JsonPrimitive
                ?: throw BackupJsonCodecException("Backup version must be an integer")
            if (primitive.isString) {
                throw BackupJsonCodecException("Backup version must be an integer")
            }
            primitive.content.toIntOrNull()
                ?: throw BackupJsonCodecException("Backup version must be an integer")
        } ?: throw BackupJsonCodecException("Backup version is missing")

        val formatVersion = when (version) {
            1 -> BackupFormatVersion.LegacyV1
            2 -> BackupFormatVersion.CurrentV2
            else -> throw BackupJsonCodecException("Unsupported backup version: $version")
        }

        return BackupJsonDocument(
            version = formatVersion,
            profiles = readSection(objectRoot, PROFILES_KEY),
            groups = readSection(objectRoot, GROUPS_KEY),
            rules = readSection(objectRoot, RULES_KEY),
            settings = readSection(objectRoot, SETTINGS_KEY),
        )
    }

    /** Encodes only the current v2 envelope; absent sections remain absent. */
    fun encode(document: BackupJsonDocument): String {
        if (document.version != BackupFormatVersion.CurrentV2) {
            throw BackupJsonCodecException(
                "Only the current v2 backup format can be encoded",
            )
        }
        val encoded = buildJsonObject {
            put(VERSION_KEY, BackupFormatVersion.CurrentV2.value)
            document.profiles?.let { put(PROFILES_KEY, JsonArray(it.map(::JsonPrimitive))) }
            document.groups?.let { put(GROUPS_KEY, JsonArray(it.map(::JsonPrimitive))) }
            document.rules?.let { put(RULES_KEY, JsonArray(it.map(::JsonPrimitive))) }
            document.settings?.let { put(SETTINGS_KEY, JsonArray(it.map(::JsonPrimitive))) }
        }
        return prettyJson.encodeToString(JsonElement.serializer(), encoded)
    }

    private fun readSection(root: JsonObject, key: String): List<String>? {
        val element = root[key] ?: return null
        val array = element as? JsonArray
            ?: throw BackupJsonCodecException("Backup section '$key' must be an array")
        return array.mapIndexed { index, item ->
            val primitive = item as? JsonPrimitive
                ?: throw BackupJsonCodecException("Backup section '$key' item $index must be a string")
            if (!primitive.isString) {
                throw BackupJsonCodecException("Backup section '$key' item $index must be a string")
            }
            primitive.content
        }
    }

    private val prettyJson = Json { prettyPrint = true }

    private const val VERSION_KEY = "version"
    private const val PROFILES_KEY = "profiles"
    private const val GROUPS_KEY = "groups"
    private const val RULES_KEY = "rules"
    private const val SETTINGS_KEY = "settings"
}
