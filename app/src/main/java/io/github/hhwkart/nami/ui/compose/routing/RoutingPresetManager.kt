package io.github.hhwkart.nami.ui.compose.routing

import io.github.hhwkart.nami.data.routing.GeositeAssetMatcher
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.RuleEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.domain.routing.WebsiteBypassPolicy
import io.github.hhwkart.nami.domain.routing.WebsiteBypassRuleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class RoutingPreset(
    val id: String,
    val title: String,
    val description: String,
) {
    GLOBAL("global", "Global", "Route traffic through the selected proxy."),
    BYPASS_LAN("bypass_lan", "Bypass LAN", "Keep local network traffic outside the proxy."),
    BYPASS_CHINA("bypass_china", "Bypass China", "Route supported China domains and networks directly."),
    GFW_LIST("gfw_list", "GFW List", "Proxy domains listed by the bundled GFW dataset."),
}

data class PresetOwnedRuleRecord(
    val ruleId: Long,
    val presetId: String,
    val canonicalId: String,
    val canonicalFingerprint: String,
)

data class GfwAvailability(
    val available: Boolean,
    val reason: String? = null,
)

/**
 * Applies only rules which this manager previously created and registered.
 * Names and matching rule contents are never used as an ownership signal.
 */
object RoutingPresetManager {

    private const val BYPASS_CHINA_DOMAIN = "preset_bypass_china_domain"
    private const val BYPASS_CHINA_IP = "preset_bypass_china_ip"
    private const val GFW_DOMAIN = "preset_gfw_domain"

    /** The bundled geosite.db.xz was verified during Phase 5 preflight to expose the gfw code. */
    @Volatile
    var gfwAvailability: GfwAvailability = GfwAvailability(
        available = true,
        reason = null,
    )

    suspend fun apply(preset: RoutingPreset): Result<Unit> = withContext(Dispatchers.IO) {
        if (preset == RoutingPreset.GFW_LIST && !gfwAvailability.available) {
            return@withContext Result.failure(
                IllegalStateException(gfwAvailability.reason ?: "GFW List is unavailable."),
            )
        }

        runCatching {
            val existingOwnership = readOwnership()
            val existingRules = SagerDatabase.rulesDao.allRules()
            val nextOwnership = ArrayList<PresetOwnedRuleRecord>()
            val rulesToDelete = ArrayList<RuleEntity>()

            existingOwnership.forEach { record ->
                val current = SagerDatabase.rulesDao.getById(record.ruleId)
                when {
                    current == null -> Unit
                    fingerprint(current) == record.canonicalFingerprint -> rulesToDelete += current
                    else -> Unit // User edited it; preserve it as a user-owned rule.
                }
            }

            val created = when (preset) {
                RoutingPreset.BYPASS_CHINA -> listOf(
                    CanonicalRule(
                        BYPASS_CHINA_DOMAIN,
                        RuleEntity(
                            name = "Bypass China domains",
                            domains = "geosite:cn\ngoogleapis.cn",
                            outbound = -1L,
                            enabled = true,
                        ),
                    ),
                    CanonicalRule(
                        BYPASS_CHINA_IP,
                        RuleEntity(
                            name = "Bypass China IP ranges",
                            ip = "geoip:cn",
                            outbound = -1L,
                            enabled = true,
                        ),
                    ),
                )
                RoutingPreset.GFW_LIST -> listOf(
                    CanonicalRule(
                        GFW_DOMAIN,
                        RuleEntity(
                            name = "GFW List",
                            domains = "geosite:gfw",
                            outbound = 0L,
                            enabled = true,
                        ),
                    ),
                )
                RoutingPreset.GLOBAL, RoutingPreset.BYPASS_LAN -> emptyList()
            }

            if (DataStore.websiteBypassEnabled && DataStore.serviceMode == io.github.hhwkart.nami.Key.MODE_VPN) {
                val deletedIds = rulesToDelete.mapTo(HashSet()) { it.id }
                val decision = WebsiteBypassPolicy.safeRuntimeHosts(
                    hosts = DataStore.websiteBypassDomains.orEmpty().split('\n', ',', '\r'),
                    rules = existingRules
                        .filterNot { it.id in deletedIds }
                        .map(::websiteRuleSnapshot) + created.map { websiteRuleSnapshot(it.rule) },
                    geositeMatcher = GeositeAssetMatcher,
                )
                decision.rejected.firstOrNull()?.let { rejected ->
                    error(WebsiteBypassPolicy.describe(rejected))
                }
            }

            SagerDatabase.instance.runInTransaction {
                if (rulesToDelete.isNotEmpty()) {
                    SagerDatabase.rulesDao.deleteRules(rulesToDelete)
                }
                created.forEach { canonical ->
                    val duplicate = SagerDatabase.rulesDao.allRules().firstOrNull {
                        fingerprint(it) == fingerprint(canonical.rule)
                    }
                    if (duplicate == null) {
                        canonical.rule.userOrder = SagerDatabase.rulesDao.nextOrder() ?: 1L
                        canonical.rule.id = SagerDatabase.rulesDao.createRule(canonical.rule)
                        nextOwnership += PresetOwnedRuleRecord(
                            ruleId = canonical.rule.id,
                            presetId = preset.id,
                            canonicalId = canonical.id,
                            canonicalFingerprint = fingerprint(canonical.rule),
                        )
                    }
                }
            }

            DataStore.bypassLan = preset == RoutingPreset.BYPASS_LAN
            DataStore.bypassLanInCore = preset == RoutingPreset.BYPASS_LAN
            DataStore.routingPresetOwnership = encodeOwnership(nextOwnership)
            DataStore.routingPreset = preset.id
        }
    }

    fun markCustom() {
        DataStore.routingPreset = "custom"
    }

    fun clearOwnership() {
        DataStore.routingPresetOwnership = ""
        DataStore.routingPreset = "custom"
    }

    fun current(): RoutingPreset? = RoutingPreset.entries.firstOrNull { it.id == DataStore.routingPreset }

    fun fingerprint(rule: RuleEntity): String = buildString {
        append(rule.name).append('\u0000')
        append(rule.config).append('\u0000')
        append(rule.enabled).append('\u0000')
        append(rule.domains).append('\u0000')
        append(rule.ip).append('\u0000')
        append(rule.port).append('\u0000')
        append(rule.sourcePort).append('\u0000')
        append(rule.network).append('\u0000')
        append(rule.source).append('\u0000')
        append(rule.protocol).append('\u0000')
        append(rule.outbound).append('\u0000')
        append(rule.packages.toList().sorted().joinToString("\u0001"))
    }

    private fun readOwnership(): List<PresetOwnedRuleRecord> = runCatching {
        val array = JSONArray(DataStore.routingPresetOwnership.takeIf { it.isNotBlank() } ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    PresetOwnedRuleRecord(
                        ruleId = item.optLong("ruleId", 0L),
                        presetId = item.optString("presetId"),
                        canonicalId = item.optString("canonicalId"),
                        canonicalFingerprint = item.optString("canonicalFingerprint"),
                    ),
                )
            }
        }.filter { it.ruleId > 0L && it.presetId.isNotBlank() && it.canonicalId.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun encodeOwnership(records: List<PresetOwnedRuleRecord>): String = JSONArray().apply {
        records.forEach { record ->
            put(JSONObject().apply {
                put("ruleId", record.ruleId)
                put("presetId", record.presetId)
                put("canonicalId", record.canonicalId)
                put("canonicalFingerprint", record.canonicalFingerprint)
            })
        }
    }.toString()

    private data class CanonicalRule(val id: String, val rule: RuleEntity)

    private fun websiteRuleSnapshot(rule: RuleEntity) = WebsiteBypassRuleSnapshot(
        name = rule.displayName(),
        domains = rule.domains,
        enabled = rule.enabled,
        outbound = rule.outbound,
    )
}
