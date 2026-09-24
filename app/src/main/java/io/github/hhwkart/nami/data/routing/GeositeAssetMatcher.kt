package io.github.hhwkart.nami.data.routing

import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.domain.routing.GeoSiteMatch
import io.github.hhwkart.nami.domain.routing.GeoSiteMatcher
import java.io.File
import java.util.Locale
import java.util.regex.Pattern

/**
 * Read-only matcher for the sing-geosite binary extracted by libcore.
 *
 * This intentionally lives on the Kotlin side: it consumes the existing asset
 * and does not modify or rebuild libcore. The format is the small version-0
 * sing-geosite index used by the bundled sing-box assets.
 */
object GeositeAssetMatcher : GeoSiteMatcher {

    private data class Entry(val offset: Int, val length: Int)
    private data class Item(val type: Int, val value: String)

    private var loadedFile: File? = null
    private var loadedModified = Long.MIN_VALUE
    private var content: ByteArray? = null
    private var dataOffset = 0
    private var entries: Map<String, Entry> = emptyMap()
    private val itemCache = HashMap<String, List<Item>>()

    override fun match(code: String, host: String): GeoSiteMatch = synchronized(this) {
        val file = File(SagerNet.application.externalAssets, "geosite.db")
        if (!file.isFile) return GeoSiteMatch.UNAVAILABLE
        if (!loadIfNeeded(file)) return GeoSiteMatch.UNAVAILABLE
        val normalizedCode = code.lowercase(Locale.ROOT)
        val entry = entries[normalizedCode] ?: return GeoSiteMatch.UNAVAILABLE
        val items = runCatching { itemCache.getOrPut(normalizedCode) { readItems(entry) } }
            .getOrElse { return GeoSiteMatch.UNAVAILABLE }
        return runCatching {
            if (items.any { it.matches(host) }) GeoSiteMatch.MATCHED else GeoSiteMatch.NO_MATCH
        }.getOrElse { GeoSiteMatch.UNAVAILABLE }
    }

    private fun Item.matches(host: String): Boolean = when (type) {
        0 -> host == value.lowercase(Locale.ROOT)
        1 -> host == value.lowercase(Locale.ROOT) || host.endsWith(".${value.lowercase(Locale.ROOT)}")
        2 -> host.contains(value.lowercase(Locale.ROOT))
        3 -> Pattern.compile(value).matcher(host).find()
        else -> error("Unsupported geosite item type: $type")
    }

    private fun loadIfNeeded(file: File): Boolean {
        if (loadedFile?.path == file.path && loadedModified == file.lastModified() && content != null) {
            return true
        }
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return false
        return runCatching {
            val cursor = Cursor(bytes)
            check(cursor.readByte() == 0) { "Unsupported geosite version" }
            val count = cursor.readUVarInt().toInt()
            val nextEntries = LinkedHashMap<String, Entry>(count)
            repeat(count) {
                val code = cursor.readString().lowercase(Locale.ROOT)
                val offset = cursor.readUVarInt().toInt()
                val length = cursor.readUVarInt().toInt()
                check(offset >= 0 && length >= 0) { "Invalid geosite entry bounds" }
                nextEntries[code] = Entry(offset, length)
            }
            content = bytes
            dataOffset = cursor.position
            entries = nextEntries
            itemCache.clear()
            loadedFile = file
            loadedModified = file.lastModified()
        }.isSuccess
    }

    private fun readItems(entry: Entry): List<Item> {
        val bytes = requireNotNull(content)
        check(dataOffset + entry.offset in 0..bytes.size) { "Invalid geosite item offset" }
        check(entry.length in 0..bytes.size) { "Invalid geosite item count" }
        val cursor = Cursor(bytes, dataOffset + entry.offset)
        return buildList(entry.length) {
            repeat(entry.length) {
                add(Item(cursor.readByte(), cursor.readString()))
            }
        }
    }

    private class Cursor(
        private val bytes: ByteArray,
        var position: Int = 0,
    ) {
        fun readByte(): Int {
            check(position < bytes.size) { "Unexpected end of geosite asset" }
            return bytes[position++].toInt() and 0xFF
        }

        fun readUVarInt(): Long {
            var value = 0L
            var shift = 0
            while (true) {
                check(shift <= 63) { "Invalid geosite varint" }
                val next = readByte()
                value = value or ((next and 0x7F).toLong() shl shift)
                if ((next and 0x80) == 0) return value
                shift += 7
            }
        }

        fun readString(): String {
            val length = readUVarInt().toInt()
            check(length >= 0 && position + length <= bytes.size) { "Invalid geosite string" }
            val result = bytes.copyOfRange(position, position + length).toString(Charsets.UTF_8)
            position += length
            return result
        }
    }
}
