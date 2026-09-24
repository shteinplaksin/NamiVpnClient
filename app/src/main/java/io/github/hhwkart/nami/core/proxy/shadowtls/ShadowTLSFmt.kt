package io.github.hhwkart.nami.core.proxy.shadowtls

import io.github.hhwkart.nami.fmt.v2ray.buildSingBoxOutboundTLS
import io.github.hhwkart.nami.core.SingBoxOptions

fun buildSingBoxOutboundShadowTLSBean(bean: ShadowTLSBean): SingBoxOptions.Outbound_ShadowTLSOptions {
    return SingBoxOptions.Outbound_ShadowTLSOptions().apply {
        type = "shadowtls"
        server = bean.serverAddress
        server_port = bean.serverPort
        version = bean.version
        password = bean.password
        tls = buildSingBoxOutboundTLS(bean)
    }
}
