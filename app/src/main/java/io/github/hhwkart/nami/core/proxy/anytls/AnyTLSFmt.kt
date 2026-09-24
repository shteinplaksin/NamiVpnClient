package io.github.hhwkart.nami.core.proxy.anytls

import io.github.hhwkart.nami.ktx.blankAsNull
import io.github.hhwkart.nami.ktx.linkBuilder
import io.github.hhwkart.nami.ktx.toLink
import io.github.hhwkart.nami.ktx.urlSafe
import io.github.hhwkart.nami.core.SingBoxOptions
import io.github.hhwkart.nami.core.utils.listByLineOrComma
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

fun buildSingBoxOutboundAnyTLSBean(bean: AnyTLSBean): SingBoxOptions.Outbound_AnyTLSOptions {
    return SingBoxOptions.Outbound_AnyTLSOptions().apply {
        type = "anytls"
        server = bean.serverAddress
        server_port = bean.serverPort
        password = bean.password

        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni.blankAsNull()
            if (bean.allowInsecure) insecure = true
            alpn = bean.alpn.blankAsNull()?.listByLineOrComma()
            bean.certificates.blankAsNull()?.let {
                certificate = it
            }
            bean.utlsFingerprint.blankAsNull()?.let {
                utls = SingBoxOptions.OutboundUTLSOptions().apply {
                    enabled = true
                    fingerprint = it
                }
            }
            bean.echConfig.blankAsNull()?.let {
                // In new version, some complex options will be deprecated, so we just do this.
                ech = SingBoxOptions.OutboundECHOptions().apply {
                    enabled = true
                    config = listOf(it)
                }
            }
        }
    }
}

fun AnyTLSBean.toUri(): String {
    val builder = linkBuilder()
        .host(serverAddress)
        .port(serverPort)
        .username(password)
    if (!name.isNullOrBlank()) {
        builder.encodedFragment(name.urlSafe())
    }
    if (allowInsecure) {
        builder.addQueryParameter("insecure", "1")
    }
    if (!sni.isNullOrBlank()) {
        builder.addQueryParameter("sni", sni)
    }
    if (!utlsFingerprint.isNullOrBlank()) {
        builder.addQueryParameter("fp", utlsFingerprint)
    }
    return builder.toLink("anytls")
}

fun parseAnytls(url: String): AnyTLSBean {
    // https://github.com/anytls/anytls-go/blob/main/docs/uri_scheme.md
    val link = url.replace("anytls://", "https://").toHttpUrlOrNull() ?: error(
        "invalid anytls link $url"
    )
    return AnyTLSBean().apply {
        serverAddress = link.host
        serverPort = link.port
        name = link.fragment
        password = link.username
        sni = link.queryParameter("sni") ?: ""
        link.queryParameter("insecure")?.also {
            allowInsecure = it == "1" || it == "true"
        }
        link.queryParameter("fp")?.let {
            utlsFingerprint = it
        }
    }
}
