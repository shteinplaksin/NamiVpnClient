package io.github.hhwkart.nami.ui.compose.routing

import io.github.hhwkart.nami.data.routing.GeositeAssetMatcher
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.RuleEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.domain.routing.WebsiteBypassPolicy
import io.github.hhwkart.nami.domain.routing.WebsiteBypassRuleSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class RoutingExport(
    val content: String,
    val normalizedProfileOutboundCount: Int,
)

data class RoutingImportResult(
    val added: Int,
    val skippedDuplicates: Int,
    val normalized: Int = 0,
    val rejected: Int = 0,
)

class RoutingJsonException(
    message: String,
    val rejectedCount: Int = 0,
) : IllegalArgumentException(message)

@Serializable
private data class RoutingJsonDocument(
    val schemaVersion: Int,
    val rules: List<RoutingJsonRule>,
)

@Serializable
private data class RoutingJsonRule(
    val name: String = "",
    val config: String = "",
    val enabled: Boolean = true,
    val domains: String = "",
    val ip: String = "",
    val port: String = "",
    val sourcePort: String = "",
    val network: String = "",
    val source: String = "",
    val protocol: String = "",
    val packages: List<String> = emptyList(),
    val outbound: String,
)

private val routingJson = Json {
    prettyPrint = true
    encodeDefaults = true
    ignoreUnknownKeys = true
}

object RoutingJsonTransfer {

    fun export(rules: List<RuleEntity>): RoutingExport {
        var normalized = 0
        val jsonRules = rules.map { rule ->
            val outbound = when (rule.outbound) {
                -1L -> "bypass"
                -2L -> "block"
                0L -> "proxy"
                else -> {
                    normalized++
                    "proxy"
                }
            }
            RoutingJsonRule(
                name = rule.name,
                config = rule.config,
                enabled = rule.enabled,
                domains = rule.domains,
                ip = rule.ip,
                port = rule.port,
                sourcePort = rule.sourcePort,
                network = rule.network,
                source = rule.source,
                protocol = rule.protocol,
                packages = rule.packages.toList().sorted(),
                outbound = outbound,
            )
        }
        return RoutingExport(
            content = routingJson.encodeToString(RoutingJsonDocument(1, jsonRules)),
            normalizedProfileOutboundCount = normalized,
        )
    }

    fun decode(content: String): List<RuleEntity> {
        val document = try {
            routingJson.decodeFromString<RoutingJsonDocument>(content)
        } catch (error: SerializationException) {
            throw RoutingJsonException("Invalid routing JSON: ${error.message ?: "malformed document"}")
        } catch (error: IllegalArgumentException) {
            throw RoutingJsonException("Invalid routing JSON: ${error.message ?: "malformed document"}")
        }
        if (document.schemaVersion != 1) {
            throw RoutingJsonException(
                "Unsupported routing JSON schema version.",
                rejectedCount = document.rules.size,
            )
        }
        return document.rules.mapIndexed { index, item ->
            val outbound = when (item.outbound) {
                "proxy" -> 0L
                "bypass" -> -1L
                "block" -> -2L
                else -> throw RoutingJsonException(
                    "Rule ${index + 1} has an unsupported outbound kind.",
                    rejectedCount = document.rules.size,
                )
            }
            val packages = item.packages.toSet().also { values ->
                if (values.any { it.isBlank() }) {
                    throw RoutingJsonException(
                        "Rule ${index + 1} has an empty package name.",
                        rejectedCount = document.rules.size,
                    )
                }
            }
            if (item.domains.isBlank() && item.ip.isBlank() && packages.isEmpty() &&
                item.config.isBlank() && item.port.isBlank() && item.sourcePort.isBlank() &&
                item.source.isBlank() && item.network.isBlank() && item.protocol.isBlank()
            ) {
                throw RoutingJsonException(
                    "Rule ${index + 1} has no matching criteria.",
                    rejectedCount = document.rules.size,
                )
            }
            RuleEntity(
                name = item.name,
                config = item.config,
                enabled = item.enabled,
                domains = item.domains,
                ip = item.ip,
                port = item.port,
                sourcePort = item.sourcePort,
                network = item.network,
                source = item.source,
                protocol = item.protocol,
                packages = packages,
                outbound = outbound,
            )
        }
    }

    suspend fun import(content: String): Result<RoutingImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = decode(content)
            val existing = SagerDatabase.rulesDao.allRules()
            val unique = decoded.distinctBy(RoutingPresetManager::fingerprint)
            val duplicates = decoded.size - unique.size
            val newRules = unique.filterNot { candidate ->
                existing.any { RoutingPresetManager.fingerprint(it) == RoutingPresetManager.fingerprint(candidate) }
            }
            if (DataStore.websiteBypassEnabled && DataStore.serviceMode == io.github.hhwkart.nami.Key.MODE_VPN) {
                val decision = WebsiteBypassPolicy.safeRuntimeHosts(
                    hosts = DataStore.websiteBypassDomains.orEmpty().split('\n', ',', '\r'),
                    rules = (existing + newRules).map {
                        WebsiteBypassRuleSnapshot(
                            name = it.displayName(),
                            domains = it.domains,
                            enabled = it.enabled,
                            outbound = it.outbound,
                        )
                    },
                    geositeMatcher = GeositeAssetMatcher,
                )
                decision.rejected.firstOrNull()?.let { rejected ->
                    throw RoutingJsonException(WebsiteBypassPolicy.describe(rejected))
                }
            }
            SagerDatabase.instance.runInTransaction {
                newRules.forEach { rule ->
                    rule.id = 0L
                    rule.userOrder = SagerDatabase.rulesDao.nextOrder() ?: 1L
                    SagerDatabase.rulesDao.createRule(rule)
                }
            }
            RoutingPresetManager.markCustom()
            RoutingImportResult(
                added = newRules.size,
                skippedDuplicates = duplicates + unique.size - newRules.size,
                rejected = 0,
            )
        }
    }
}
