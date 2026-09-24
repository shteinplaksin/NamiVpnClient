package io.github.hhwkart.nami.domain.routing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebsiteBypassPolicyTest {

    @Test
    fun normalizeTrimsLowercasesRemovesTrailingDotAndConvertsIdn() {
        val result = WebsiteBypassPolicy.normalize("  BÜCHER.Example.  ")

        assertEquals(
            WebsiteBypassValidation.Accepted("xn--bcher-kva.example"),
            result,
        )
    }

    @Test
    fun validateRejectsDuplicateCaseInsensitivelyAfterNormalization() {
        val result = WebsiteBypassPolicy.validate(
            raw = " Example.COM. ",
            existingHosts = listOf("example.com"),
            rules = emptyList(),
            geositeMatcher = noGeoSiteMatch,
        )

        assertEquals(WebsiteBypassValidation.Duplicate("example.com"), result)
    }

    @Test
    fun normalizeRejectsUrlPathPortWildcardAndLeadingDot() {
        val invalidValues = listOf(
            "https://example.com",
            "example.com/path",
            "example.com?query=1",
            "example.com#fragment",
            "example.com:443",
            "*.example.com",
            ".example.com",
        )

        invalidValues.forEach { raw ->
            assertTrue("Expected '$raw' to be invalid", WebsiteBypassPolicy.normalize(raw) is WebsiteBypassValidation.Invalid)
        }
    }

    @Test
    fun normalizeRejectsIpAddresses() {
        assertInvalid("192.0.2.1")
        assertInvalid("2001:db8::1")
    }

    @Test
    fun normalizeRejectsSingleLabelAndBareTld() {
        assertInvalid("localhost")
        assertInvalid("com")
    }

    @Test
    fun normalizeRejectsMalformedAndOverlongLabels() {
        assertInvalid("-example.com")
        assertInvalid("example-.com")
        assertInvalid("exa_mple.com")
        assertInvalid("a".repeat(64) + ".com")
    }

    @Test
    fun suffixRuleMatchesTheDomainAndItsSubdomainsButNotLookalikes() {
        val rule = WebsiteBypassRuleSnapshot(name = "suffix", domains = "example.com")

        assertConflict("example.com", rule, "example.com")
        assertConflict("www.example.com", rule, "example.com")
        assertAccepted("notexample.com", rule)
    }

    @Test
    fun fullRuleMatchesOnlyTheExactHost() {
        val rule = WebsiteBypassRuleSnapshot(name = "full", domains = "full:example.com")

        assertConflict("example.com", rule, "full:example.com")
        assertAccepted("www.example.com", rule)
    }

    @Test
    fun domainRuleMatchesTheDomainAndSubdomains() {
        val rule = WebsiteBypassRuleSnapshot(name = "domain", domains = "domain:example.com.")

        assertConflict("example.com", rule, "domain:example.com.")
        assertConflict("api.example.com", rule, "domain:example.com.")
        assertAccepted("example.org", rule)
    }

    @Test
    fun keywordRuleMatchesAContainedSubstring() {
        val rule = WebsiteBypassRuleSnapshot(name = "keyword", domains = "keyword:video")

        assertConflict("video.example.com", rule, "keyword:video")
        assertAccepted("example.com", rule)
    }

    @Test
    fun regexpRuleMatchesAndInvalidExpressionsCannotBeVerified() {
        val matchingRule = WebsiteBypassRuleSnapshot(name = "regexp", domains = "regexp:^api\\.example\\.com$")
        assertConflict("api.example.com", matchingRule, "regexp:^api\\.example\\.com$")
        assertAccepted("www.example.com", matchingRule)

        val invalidRule = WebsiteBypassRuleSnapshot(name = "regexp", domains = "regexp:[")
        val result = validate("example.com", invalidRule)
        assertEquals(
            WebsiteBypassValidation.CannotVerify("example.com", "regexp", "regexp:["),
            result,
        )
    }

    @Test
    fun geositeRuAliasChecksCategoryAndTldCodes() {
        val matcher = RecordingGeoSiteMatcher { code, _ ->
            if (code == "tld-ru") GeoSiteMatch.MATCHED else GeoSiteMatch.NO_MATCH
        }
        val rule = WebsiteBypassRuleSnapshot(name = "geosite", domains = "geosite:RU")

        val result = validate("example.ru", rule, matcher)

        assertEquals(
            WebsiteBypassValidation.Conflict("example.ru", "geosite", "geosite:RU"),
            result,
        )
        assertEquals(listOf("category-ru", "tld-ru"), matcher.codes)
    }

    @Test
    fun unavailableGeositeReturnsCannotVerify() {
        val matcher = RecordingGeoSiteMatcher { _, _ -> GeoSiteMatch.UNAVAILABLE }
        val rule = WebsiteBypassRuleSnapshot(name = "geosite", domains = "geosite:category-custom")

        assertEquals(
            WebsiteBypassValidation.CannotVerify(
                "example.com",
                "geosite",
                "geosite:category-custom",
            ),
            validate("example.com", rule, matcher),
        )
    }

    @Test
    fun blockingRulesDoNotRejectABypassHost() {
        val blockingRule = WebsiteBypassRuleSnapshot(
            name = "block",
            domains = "example.com,full:api.example.com",
            outbound = -2L,
        )

        assertAccepted("api.example.com", blockingRule)
    }

    @Test
    fun disabledRulesDoNotRejectABypassHost() {
        val disabledRule = WebsiteBypassRuleSnapshot(
            name = "disabled",
            domains = "example.com",
            enabled = false,
        )

        assertAccepted("example.com", disabledRule)
    }

    @Test
    fun firstActiveNonBlockingRuleWinsOverLaterRules() {
        val rules = listOf(
            WebsiteBypassRuleSnapshot(name = "first", domains = "keyword:example"),
            WebsiteBypassRuleSnapshot(name = "second", domains = "full:example.com"),
        )

        val result = WebsiteBypassPolicy.validate(
            raw = "example.com",
            existingHosts = emptyList(),
            rules = rules,
            geositeMatcher = noGeoSiteMatch,
        )

        assertEquals(
            WebsiteBypassValidation.Conflict("example.com", "first", "keyword:example"),
            result,
        )
    }

    @Test
    fun safeRuntimeHostsNormalizesAndDropsDuplicatesWhileKeepingRejections() {
        val result = WebsiteBypassPolicy.safeRuntimeHosts(
            hosts = listOf(" Example.COM ", "example.com.", "localhost"),
            rules = emptyList(),
            geositeMatcher = noGeoSiteMatch,
        )

        assertEquals(listOf("example.com"), result.acceptedHosts)
        assertEquals(listOf(WebsiteBypassValidation.Invalid("Enter a fully qualified domain name")), result.rejected)
    }

    private fun validate(
        host: String,
        rule: WebsiteBypassRuleSnapshot,
        matcher: GeoSiteMatcher = noGeoSiteMatch,
    ): WebsiteBypassValidation = WebsiteBypassPolicy.validate(
        raw = host,
        existingHosts = emptyList(),
        rules = listOf(rule),
        geositeMatcher = matcher,
    )

    private fun assertAccepted(host: String, rule: WebsiteBypassRuleSnapshot) {
        assertEquals(WebsiteBypassValidation.Accepted(host), validate(host, rule))
    }

    private fun assertConflict(host: String, rule: WebsiteBypassRuleSnapshot, matcher: String) {
        assertEquals(
            WebsiteBypassValidation.Conflict(host, rule.name, matcher),
            validate(host, rule),
        )
    }

    private fun assertInvalid(raw: String) {
        assertTrue("Expected '$raw' to be invalid", WebsiteBypassPolicy.normalize(raw) is WebsiteBypassValidation.Invalid)
    }

    private class RecordingGeoSiteMatcher(
        private val delegate: (String, String) -> GeoSiteMatch,
    ) : GeoSiteMatcher {
        val codes = mutableListOf<String>()

        override fun match(code: String, host: String): GeoSiteMatch {
            codes += code
            return delegate(code, host)
        }
    }

    private companion object {
        val noGeoSiteMatch = GeoSiteMatcher { _, _ -> GeoSiteMatch.NO_MATCH }
    }
}
