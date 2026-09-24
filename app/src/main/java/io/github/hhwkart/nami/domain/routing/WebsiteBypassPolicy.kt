package io.github.hhwkart.nami.domain.routing

import java.net.IDN
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** Result of looking up one geosite code for one normalized hostname. */
enum class GeoSiteMatch {
    MATCHED,
    NO_MATCH,
    UNAVAILABLE,
}

/** Adapter boundary for the sing-box geosite asset. */
fun interface GeoSiteMatcher {
    fun match(code: String, host: String): GeoSiteMatch
}

/** Room-free snapshot of a persisted routing rule used by the domain policy. */
data class WebsiteBypassRuleSnapshot(
    val name: String,
    val domains: String,
    val enabled: Boolean = true,
    val outbound: Long = 0L,
)

sealed interface WebsiteBypassValidation {
    data class Accepted(val normalizedHost: String) : WebsiteBypassValidation

    data class Duplicate(val host: String) : WebsiteBypassValidation

    data class Invalid(val reason: String) : WebsiteBypassValidation

    data class Conflict(
        val host: String,
        val ruleName: String,
        val matcher: String,
    ) : WebsiteBypassValidation

    data class CannotVerify(
        val host: String,
        val ruleName: String,
        val matcher: String,
    ) : WebsiteBypassValidation
}

data class WebsiteBypassRuntimeDecision(
    val acceptedHosts: List<String>,
    val rejected: List<WebsiteBypassValidation>,
)

/**
 * Pure validation and conflict policy for the global VPN website bypass list.
 *
 * Runtime order is intentional: active non-blocking domain rules are checked
 * before a Website Bypass rule is appended to the generated route. Blocking
 * rules remain higher priority and therefore do not need to reject a bypass
 * entry.
 */
object WebsiteBypassPolicy {

    private const val BLOCK_OUTBOUND = -2L
    private val labelPattern = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
    private val ipv4Pattern = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")

    fun normalize(raw: String): WebsiteBypassValidation {
        val value = raw.trim()
        if (value.isEmpty()) return WebsiteBypassValidation.Invalid("Enter a domain")
        if (value.startsWith(".") || value.startsWith("*.")) {
            return WebsiteBypassValidation.Invalid("Leading dots and wildcards are not supported")
        }
        if (value.contains("://") || value.contains('/') || value.contains('?') || value.contains('#')) {
            return WebsiteBypassValidation.Invalid("Enter a hostname, not a URL")
        }
        if (value.contains(':') || ipv4Pattern.matches(value)) {
            return WebsiteBypassValidation.Invalid("IP addresses are not supported")
        }

        val withoutTrailingDot = value.removeSuffix(".")
        val ascii = try {
            IDN.toASCII(withoutTrailingDot, IDN.USE_STD3_ASCII_RULES)
        } catch (_: IllegalArgumentException) {
            return WebsiteBypassValidation.Invalid("Invalid hostname")
        }
        val normalized = ascii.lowercase(Locale.ROOT)
        val labels = normalized.split('.')
        if (labels.size < 2 || normalized.length > 253 || labels.any { !labelPattern.matches(it) }) {
            return WebsiteBypassValidation.Invalid("Enter a fully qualified domain name")
        }
        return WebsiteBypassValidation.Accepted(normalized)
    }

    fun validate(
        raw: String,
        existingHosts: Collection<String>,
        rules: Collection<WebsiteBypassRuleSnapshot>,
        geositeMatcher: GeoSiteMatcher,
    ): WebsiteBypassValidation {
        val normalized = normalize(raw)
        if (normalized !is WebsiteBypassValidation.Accepted) return normalized
        if (existingHosts.any { existing ->
                (normalize(existing) as? WebsiteBypassValidation.Accepted)?.normalizedHost == normalized.normalizedHost
            }
        ) {
            return WebsiteBypassValidation.Duplicate(normalized.normalizedHost)
        }

        rules.asSequence()
            .filter { it.enabled && it.outbound != BLOCK_OUTBOUND && it.domains.isNotBlank() }
            .forEach { rule ->
                evaluateRule(rule, normalized.normalizedHost, geositeMatcher)?.let { return it }
            }
        return normalized
    }

    fun safeRuntimeHosts(
        hosts: Collection<String>,
        rules: Collection<WebsiteBypassRuleSnapshot>,
        geositeMatcher: GeoSiteMatcher,
    ): WebsiteBypassRuntimeDecision {
        val accepted = ArrayList<String>()
        val rejected = ArrayList<WebsiteBypassValidation>()
        hosts.forEach { raw ->
            when (val result = validate(raw, accepted, rules, geositeMatcher)) {
                is WebsiteBypassValidation.Accepted -> accepted += result.normalizedHost
                is WebsiteBypassValidation.Duplicate -> Unit
                else -> rejected += result
            }
        }
        return WebsiteBypassRuntimeDecision(accepted.distinct(), rejected)
    }

    fun describe(validation: WebsiteBypassValidation): String = when (validation) {
        is WebsiteBypassValidation.Accepted -> "Added ${validation.normalizedHost}"
        is WebsiteBypassValidation.Duplicate -> "${validation.host} is already in Website Bypass"
        is WebsiteBypassValidation.Invalid -> validation.reason
        is WebsiteBypassValidation.Conflict ->
            "${validation.host} conflicts with \"${validation.ruleName}\" (${validation.matcher})"

        is WebsiteBypassValidation.CannotVerify ->
            "Cannot verify ${validation.host} against \"${validation.ruleName}\" (${validation.matcher})"
    }

    private fun evaluateRule(
        rule: WebsiteBypassRuleSnapshot,
        host: String,
        geositeMatcher: GeoSiteMatcher,
    ): WebsiteBypassValidation? {
        rule.domains.split(',', '\n', '\r')
            .asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .forEach { rawToken ->
                val token = rawToken.lowercase(Locale.ROOT)
                when {
                    token.startsWith("geosite:") -> {
                        val code = rawToken.substringAfter(':').trim().lowercase(Locale.ROOT)
                        val codes = when (code) {
                            "ru" -> listOf("category-ru", "tld-ru")
                            "ir" -> listOf("category-ir")
                            else -> listOf(code)
                        }
                        codes.forEach { resolvedCode ->
                            when (geositeMatcher.match(resolvedCode, host)) {
                                GeoSiteMatch.MATCHED ->
                                    return WebsiteBypassValidation.Conflict(host, rule.name, rawToken)

                                GeoSiteMatch.UNAVAILABLE ->
                                    return WebsiteBypassValidation.CannotVerify(host, rule.name, rawToken)

                                GeoSiteMatch.NO_MATCH -> Unit
                            }
                        }
                    }

                    token.startsWith("full:") -> {
                        val matcher = rawToken.substringAfter(':').trim().removeSuffix(".").lowercase(Locale.ROOT)
                        if (host == matcher) {
                            return WebsiteBypassValidation.Conflict(host, rule.name, rawToken)
                        }
                    }

                    token.startsWith("domain:") -> {
                        val matcher = rawToken.substringAfter(':').trim().removeSuffix(".").lowercase(Locale.ROOT)
                        if (matchesSuffix(host, matcher)) {
                            return WebsiteBypassValidation.Conflict(host, rule.name, rawToken)
                        }
                    }

                    token.startsWith("keyword:") -> {
                        val matcher = rawToken.substringAfter(':').trim().lowercase(Locale.ROOT)
                        if (host.contains(matcher)) {
                            return WebsiteBypassValidation.Conflict(host, rule.name, rawToken)
                        }
                    }

                    token.startsWith("regexp:") -> {
                        val expression = rawToken.substringAfter(':').trim()
                        try {
                            if (Pattern.compile(expression).matcher(host).find()) {
                                return WebsiteBypassValidation.Conflict(host, rule.name, rawToken)
                            }
                        } catch (_: PatternSyntaxException) {
                            return WebsiteBypassValidation.CannotVerify(host, rule.name, rawToken)
                        }
                    }

                    else -> {
                        if (matchesSuffix(host, rawToken.lowercase(Locale.ROOT))) {
                            return WebsiteBypassValidation.Conflict(host, rule.name, rawToken)
                        }
                    }
                }
            }
        return null
    }

    private fun matchesSuffix(host: String, suffix: String): Boolean {
        val normalized = suffix.removeSuffix(".")
        return normalized.isNotBlank() && (host == normalized || host.endsWith(".$normalized"))
    }
}
