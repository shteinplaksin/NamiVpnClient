package io.github.hhwkart.nami.fmt

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import io.github.hhwkart.nami.core.utils.JavaUtil.gson
import java.io.StringReader

/** Constrains raw sing-box inbound listeners when Android LAN permission is unavailable. */
internal object RawConfigLanPolicy {
    fun configForBuild(
        config: String,
        forExport: Boolean,
        hasLocalNetworkPermission: () -> Boolean,
    ): String = when {
        forExport -> config
        hasLocalNetworkPermission() -> config
        else -> restrictInboundListenersToLocalhost(config)
    }

    fun restrictInboundListenersToLocalhost(config: String): String {
        rejectDuplicateProtectedKeys(config)
        val root = try {
            gson.fromJson(config, JsonObject::class.java)
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box inbounds to localhost without local network permission. " +
                    "Check the custom configuration JSON or grant local network permission.",
                error,
            )
        } ?: return config

        var changed = restrictInboundListeners(root)
        changed = restrictExperimentalApiListeners(root) || changed

        return if (changed) gson.toJson(root) else config
    }

    private enum class JsonScope {
        ROOT,
        INBOUNDS,
        INBOUND,
        EXPERIMENTAL,
        CLASH_API,
        V2RAY_API,
        DEBUG,
        OTHER,
    }

    private fun rejectDuplicateProtectedKeys(config: String) {
        try {
            JsonReader(StringReader(config)).apply { isLenient = true }.use { reader ->
                if (reader.peek() != JsonToken.END_DOCUMENT) scanJsonValue(reader, JsonScope.ROOT)
                if (reader.peek() != JsonToken.END_DOCUMENT) {
                    throw IllegalArgumentException("Unexpected trailing content in raw sing-box config.")
                }
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Cannot safely inspect raw sing-box config listener keys without local network permission.",
                error,
            )
        }
    }

    private fun scanJsonValue(reader: JsonReader, scope: JsonScope) {
        when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> scanJsonObject(reader, scope)
            JsonToken.BEGIN_ARRAY -> scanJsonArray(reader, scope)
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NULL -> reader.nextNull()
            else -> throw IllegalArgumentException("Unexpected token in raw sing-box config.")
        }
    }

    private fun scanJsonObject(reader: JsonReader, scope: JsonScope) {
        val seenProtectedKeys = mutableSetOf<String>()
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            val protectedKey = scope.protectedKeys().firstOrNull { key ->
                key.equals(name, ignoreCase = true)
            }
            if (protectedKey != null && !seenProtectedKeys.add(protectedKey)) {
                throw IllegalArgumentException(
                    "Cannot safely restrict raw sing-box listeners without local network permission: " +
                        "duplicate case-insensitive '$protectedKey' key in ${scope.description}.",
                )
            }
            scanJsonValue(reader, scope.childScope(protectedKey))
        }
        reader.endObject()
    }

    private fun scanJsonArray(reader: JsonReader, scope: JsonScope) {
        val itemScope = if (scope == JsonScope.INBOUNDS) JsonScope.INBOUND else JsonScope.OTHER
        reader.beginArray()
        while (reader.hasNext()) scanJsonValue(reader, itemScope)
        reader.endArray()
    }

    private fun JsonScope.protectedKeys(): Set<String> = when (this) {
        JsonScope.ROOT -> setOf("inbounds", "experimental")
        JsonScope.INBOUND -> setOf("type", "listen")
        JsonScope.EXPERIMENTAL -> setOf("clash_api", "v2ray_api", "debug")
        JsonScope.CLASH_API -> setOf("external_controller")
        JsonScope.V2RAY_API, JsonScope.DEBUG -> setOf("listen")

        JsonScope.INBOUNDS,
        JsonScope.OTHER,
        -> emptySet()
    }

    private fun JsonScope.childScope(protectedKey: String?): JsonScope = when (this) {
        JsonScope.ROOT -> when (protectedKey) {
            "inbounds" -> JsonScope.INBOUNDS
            "experimental" -> JsonScope.EXPERIMENTAL
            else -> JsonScope.OTHER
        }

        JsonScope.EXPERIMENTAL -> when (protectedKey) {
            "clash_api" -> JsonScope.CLASH_API
            "v2ray_api" -> JsonScope.V2RAY_API
            "debug" -> JsonScope.DEBUG
            else -> JsonScope.OTHER
        }

        else -> JsonScope.OTHER
    }

    private val JsonScope.description: String
        get() = when (this) {
            JsonScope.ROOT -> "top-level config"
            JsonScope.INBOUND -> "inbound"
            JsonScope.EXPERIMENTAL -> "experimental"
            JsonScope.CLASH_API -> "experimental.clash_api"
            JsonScope.V2RAY_API -> "experimental.v2ray_api"
            JsonScope.DEBUG -> "experimental.debug"
            JsonScope.INBOUNDS -> "inbounds"
            JsonScope.OTHER -> "config object"
        }

    private fun restrictInboundListeners(root: JsonObject): Boolean {
        val inboundsElement = root.uniqueField("inbounds", "top-level config")?.value ?: return false
        if (inboundsElement.isJsonNull) return false
        val inbounds = inboundsElement.takeIf { it.isJsonArray }?.asJsonArray
            ?: throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box listeners without local network permission: " +
                    "the inbounds field must be a JSON array."
            )

        var changed = false
        inbounds.forEach { inboundElement ->
            val inbound = inboundElement.takeIf { it.isJsonObject }?.asJsonObject
                ?: throw IllegalArgumentException(
                    "Cannot safely restrict raw sing-box listeners without local network permission: " +
                        "every inbound must be a JSON object."
                )
            val type = inbound.uniqueField("type", "inbound")
                ?.value?.takeUnless { it.isJsonNull }?.asString
            if (type != "tun") {
                // A missing listen address also defaults to a network
                // listener in raw sing-box configs, so set it explicitly.
                changed = inbound.setStringIfChanged("listen", LOCALHOST, "inbound") || changed
            }
        }
        return changed
    }

    private fun restrictExperimentalApiListeners(root: JsonObject): Boolean {
        val experimentalElement = root.uniqueField("experimental", "top-level config")?.value ?: return false
        if (experimentalElement.isJsonNull) return false
        val experimental = experimentalElement.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box listeners without local network permission: " +
                    "experimental must be a JSON object."
            )

        var changed = experimental.objectFieldOrNull("clash_api")
            ?.restrictAddressField("external_controller", "experimental.clash_api") ?: false
        changed = (experimental.objectFieldOrNull("v2ray_api")
            ?.restrictAddressField("listen", "experimental.v2ray_api") ?: false) || changed
        changed = (experimental.objectFieldOrNull("debug")
            ?.restrictAddressField("listen", "experimental.debug") ?: false) || changed
        return changed
    }

    private fun JsonObject.objectFieldOrNull(name: String): JsonObject? {
        val element = uniqueField(name, "experimental")?.value ?: return null
        if (element.isJsonNull) return null
        return element.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box listeners without local network permission: " +
                    "experimental.$name must be a JSON object."
            )
    }

    private fun JsonObject.restrictAddressField(name: String, owner: String): Boolean {
        val element = uniqueField(name, owner)?.value ?: return false
        if (element.isJsonNull) return false
        val address = element.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
            ?: throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box listeners without local network permission: " +
                    "experimental listener field $name must be a string."
            )
        if (address.isBlank()) return false
        return setStringIfChanged(name, forceLoopbackHost(address), owner)
    }

    private fun JsonObject.setStringIfChanged(name: String, value: String, owner: String): Boolean {
        val field = uniqueField(name, owner)
        val replacement = com.google.gson.JsonPrimitive(value)
        if (field?.value == replacement) return false
        add(field?.key ?: name, replacement)
        return true
    }

    private fun JsonObject.uniqueField(name: String, owner: String): Map.Entry<String, JsonElement>? {
        val matches = entrySet().filter { (key, _) -> key.equals(name, ignoreCase = true) }
        if (matches.size > 1) {
            throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box listeners without local network permission: " +
                    "ambiguous case-insensitive '$name' keys in $owner."
            )
        }
        return matches.singleOrNull()
    }

    private fun forceLoopbackHost(address: String): String {
        val schemeSeparator = address.indexOf("://")
        val scheme = if (schemeSeparator >= 0) address.substring(0, schemeSeparator) else ""
        // Unix sockets and file-descriptor listeners are not LAN listeners;
        // keep their local path/descriptor unchanged.
        if (scheme.equals("unix", ignoreCase = true) || scheme.equals("fd", ignoreCase = true) ||
            address.startsWith("unix:", ignoreCase = true) || address.startsWith("fd:", ignoreCase = true)
        ) {
            return address
        }

        val prefix = if (schemeSeparator >= 0) address.substring(0, schemeSeparator + 3) else ""
        val remainder = if (schemeSeparator >= 0) address.substring(schemeSeparator + 3) else address
        val suffixIndex = remainder.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (suffixIndex >= 0) remainder.substring(0, suffixIndex) else remainder
        val suffix = if (suffixIndex >= 0) remainder.substring(suffixIndex) else ""
        if (authority.isBlank()) {
            throw IllegalArgumentException(
                "Cannot safely restrict raw sing-box listener address '$address' to localhost."
            )
        }
        if (isLoopbackAuthority(authority)) return address

        // Keep the port exactly as supplied (including bracketed or unbracketed
        // IPv6 authorities) and preserve any scheme and path suffix.
        val portSeparator = authority.lastIndexOf(':')
        val port = if (portSeparator >= 0 && portSeparator < authority.lastIndex) {
            ":${authority.substring(portSeparator + 1)}"
        } else {
            ""
        }
        return "$prefix$LOCALHOST$port$suffix"
    }

    private fun isLoopbackAuthority(authority: String): Boolean {
        val host = if (authority.startsWith("[")) {
            val closingBracket = authority.indexOf(']')
            if (closingBracket < 0) return false
            authority.substring(1, closingBracket)
        } else {
            val separator = authority.lastIndexOf(':')
            if (separator >= 0 && separator < authority.lastIndex) {
                authority.substring(0, separator)
            } else {
                authority
            }
        }
        if (host.equals("localhost", ignoreCase = true) || host == "::1") return true

        val ipv4 = host.split('.')
        return ipv4.size == 4 && ipv4[0] == "127" && ipv4.all { part ->
            part.toIntOrNull()?.let { it in 0..255 } == true
        }
    }
}
