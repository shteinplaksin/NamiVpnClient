package io.github.hhwkart.nami.ui.compose.editor

import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.fmt.AbstractBean
import io.github.hhwkart.nami.fmt.http.HttpBean
import io.github.hhwkart.nami.fmt.hysteria.HysteriaBean
import io.github.hhwkart.nami.fmt.internal.ChainBean
import io.github.hhwkart.nami.fmt.mieru.MieruBean
import io.github.hhwkart.nami.fmt.naive.NaiveBean
import io.github.hhwkart.nami.fmt.shadowsocks.ShadowsocksBean
import io.github.hhwkart.nami.fmt.socks.SOCKSBean
import io.github.hhwkart.nami.fmt.ssh.SSHBean
import io.github.hhwkart.nami.fmt.trojan.TrojanBean
import io.github.hhwkart.nami.fmt.trojan_go.TrojanGoBean
import io.github.hhwkart.nami.fmt.tuic.TuicBean
import io.github.hhwkart.nami.fmt.v2ray.StandardV2RayBean
import io.github.hhwkart.nami.fmt.v2ray.VMessBean
import io.github.hhwkart.nami.fmt.wireguard.WireGuardBean
import io.github.hhwkart.nami.ktx.applyDefaultValues
import io.github.hhwkart.nami.core.proxy.anytls.AnyTLSBean
import io.github.hhwkart.nami.core.proxy.config.ConfigBean
import io.github.hhwkart.nami.core.proxy.neko.NekoBean
import io.github.hhwkart.nami.core.proxy.shadowtls.ShadowTLSBean
import org.json.JSONObject

/** The single parity catalog used by the Compose protocol editor. */
object ProtocolEditorCatalog {

    private const val NONE = ""

    private fun options(vararg values: String) = values.map { EditorOption(it, it) }

    private fun text(
        key: String,
        label: String,
        section: EditorSection,
        defaultValue: String = NONE,
        required: Boolean = false,
        visibility: List<FieldVisibility> = emptyList(),
        kind: EditorFieldKind = EditorFieldKind.TEXT,
        supportingText: String? = null,
    ) = EditorFieldSpec(key, label, kind, section, defaultValue, required = required, visibility = visibility, supportingText = supportingText)

    private fun number(
        key: String,
        label: String,
        section: EditorSection,
        defaultValue: String = "0",
        min: Int? = null,
        max: Int? = null,
        visibility: List<FieldVisibility> = emptyList(),
    ) = EditorFieldSpec(key, label, EditorFieldKind.NUMBER, section, defaultValue, min = min, max = max, visibility = visibility)

    private fun password(
        key: String,
        label: String,
        section: EditorSection,
        defaultValue: String = NONE,
        visibility: List<FieldVisibility> = emptyList(),
        visibleWhen: ((Map<String, String>) -> Boolean)? = null,
    ) = EditorFieldSpec(key, label, EditorFieldKind.PASSWORD, section, defaultValue, visibility = visibility, visibleWhen = visibleWhen)

    private fun menu(
        key: String,
        label: String,
        section: EditorSection,
        defaultValue: String,
        values: List<String>,
        visibility: List<FieldVisibility> = emptyList(),
        labels: List<String> = values,
    ) = EditorFieldSpec(
        key = key,
        label = label,
        kind = EditorFieldKind.DROPDOWN,
        section = section,
        defaultValue = defaultValue,
        options = values.mapIndexed { index, value ->
            EditorOption(labels.getOrElse(index) { value }, value)
        },
        visibility = visibility,
    )

    private fun toggle(
        key: String,
        label: String,
        section: EditorSection,
        defaultValue: String = "false",
        visibility: List<FieldVisibility> = emptyList(),
    ) = EditorFieldSpec(key, label, EditorFieldKind.SWITCH, section, defaultValue, visibility = visibility)

    private fun common(nameKey: String = FieldKeys.NAME): List<EditorFieldSpec> = listOf(
        text(nameKey, "Name", EditorSection.COMMON),
        text(FieldKeys.SERVER_ADDRESS, "Server address", EditorSection.COMMON, "127.0.0.1", required = true),
        number(FieldKeys.SERVER_PORT, "Server port", EditorSection.COMMON, "1080", min = 1, max = 65535),
    )

    private val sharedAdvanced = listOf(
        text(
            FieldKeys.CUSTOM_OUTBOUND_JSON,
            "Custom outbound JSON",
            EditorSection.ADVANCED,
            kind = EditorFieldKind.JSON,
        ),
        text(
            FieldKeys.CUSTOM_CONFIG_JSON,
            "Custom config JSON",
            EditorSection.ADVANCED,
            kind = EditorFieldKind.JSON,
        ),
    )

    private fun v2ray(kind: ProtocolKind): List<EditorFieldSpec> {
        val httpOnly = listOf(FieldVisibility("_protocol", setOf("http")))
        val nonHttp = listOf(FieldVisibility("_protocol", setOf("http"), negate = true))
        val vmess = listOf(FieldVisibility("_protocol", setOf("vmess")))
        val vmessOrVless = listOf(FieldVisibility("_protocol", setOf("vmess", "vless")))
        val tls = listOf(FieldVisibility("security", setOf("tls")))
        val hostTransport = listOf(FieldVisibility("type", setOf("http", "ws", "httpupgrade")))
        val pathTransport = listOf(FieldVisibility("type", setOf("http", "ws", "grpc", "httpupgrade")))
        val ws = listOf(FieldVisibility("type", setOf("ws")))
        return common() + listOf(
            text("username", "Username", EditorSection.PROTOCOL, visibility = if (kind == ProtocolKind.HTTP) emptyList() else listOf(FieldVisibility("_protocol", setOf("http")))),
            password("password", "Password", EditorSection.PROTOCOL, visibility = if (kind == ProtocolKind.HTTP) emptyList() else httpOnly),
            password("uuid", if (kind == ProtocolKind.TROJAN) "Password" else "UUID", EditorSection.PROTOCOL, visibility = if (kind == ProtocolKind.HTTP) nonHttp else emptyList()),
            number("alterId", "Alter ID", EditorSection.PROTOCOL, "0", min = 0, max = 65535, visibility = vmess),
            menu("encryption", if (kind == ProtocolKind.VLESS) "XTLS flow" else "Encryption", EditorSection.PROTOCOL, if (kind == ProtocolKind.VLESS) "" else "auto", if (kind == ProtocolKind.VLESS) listOf("", "xtls-rprx-vision") else listOf("chacha20-poly1305", "aes-128-gcm", "auto", "none", "zero"), visibility = vmessOrVless),
            menu("packetEncoding", "Packet encoding", EditorSection.PROTOCOL, "0", listOf("0", "1", "2"), visibility = vmessOrVless, labels = listOf("none", "packet", "xudp")),
            menu("type", "Network", EditorSection.TRANSPORT, "tcp", listOf("tcp", "ws", "http", "quic", "grpc", "httpupgrade"), visibility = nonHttp),
            text("host", "Host", EditorSection.TRANSPORT, visibility = hostTransport),
            text("path", "Path / service name", EditorSection.TRANSPORT, visibility = pathTransport),
            menu("security", "Security", EditorSection.TLS, if (kind == ProtocolKind.TROJAN) "tls" else "none", listOf("none", "tls")),
            number("wsMaxEarlyData", "WebSocket max early data", EditorSection.TRANSPORT, "0", min = 0, visibility = ws),
            text("earlyDataHeaderName", "Early data header name", EditorSection.TRANSPORT, visibility = ws),
            text("sni", "SNI", EditorSection.TLS, visibility = tls),
            text("alpn", "ALPN", EditorSection.TLS, visibility = tls),
            text("certificates", "Certificates", EditorSection.TLS, visibility = tls),
            toggle("allowInsecure", "Allow insecure", EditorSection.TLS, visibility = tls),
            menu("utlsFingerprint", "uTLS fingerprint", EditorSection.TLS, "", listOf("", "chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized"), visibility = tls),
            text("realityPubKey", "Reality public key", EditorSection.TLS, visibility = tls),
            text("realityShortId", "Reality short ID", EditorSection.TLS, visibility = tls),
            toggle("enableECH", "Enable ECH", EditorSection.TLS, visibility = tls),
            text("echConfig", "ECH config", EditorSection.TLS, visibility = tls),
            toggle("enableMux", "Enable mux", EditorSection.ADVANCED),
            menu("muxType", "Mux type", EditorSection.ADVANCED, "0", listOf("0", "1", "2"), labels = listOf("h2mux", "smux", "yamux")),
            number("muxConcurrency", "Mux concurrency", EditorSection.ADVANCED, "1", min = 1),
            toggle("muxPadding", "Mux padding", EditorSection.ADVANCED),
        ) + sharedAdvanced
    }

    fun specs(kind: ProtocolKind): List<EditorFieldSpec> = when (kind) {
        ProtocolKind.SOCKS -> common("profileName") + listOf(
            menu("serverProtocol", "SOCKS version", EditorSection.PROTOCOL, "2", listOf("0", "1", "2"), labels = listOf("SOCKS4", "SOCKS4A", "SOCKS5")),
            text("serverUsername", "Username", EditorSection.PROTOCOL),
            password("serverPassword", "Password", EditorSection.PROTOCOL, visibility = listOf(FieldVisibility("serverProtocol", setOf("2")))),
            toggle("sUoT", "UDP over TCP", EditorSection.ADVANCED),
        ) + sharedAdvanced
        ProtocolKind.HTTP,
        ProtocolKind.VMESS,
        ProtocolKind.VLESS,
        ProtocolKind.TROJAN -> v2ray(kind)
        ProtocolKind.SHADOWSOCKS -> common() + listOf(
            menu("method", "Encryption method", EditorSection.PROTOCOL, "aes-256-gcm", listOf("2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "none", "aes-128-gcm", "aes-192-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305", "aes-128-ctr", "aes-192-ctr", "aes-256-ctr", "aes-128-cfb", "aes-192-cfb", "aes-256-cfb", "rc4-md5", "chacha20-ietf", "xchacha20")),
            password("password", "Password", EditorSection.PROTOCOL),
            menu("pluginName", "Plugin", EditorSection.ADVANCED, "", listOf("", "obfs-local", "v2ray-plugin")),
            text("pluginConfig", "Plugin configuration", EditorSection.ADVANCED),
            toggle("sUoT", "UDP over TCP", EditorSection.ADVANCED),
        ) + sharedAdvanced
        ProtocolKind.TROJAN_GO -> common("profileName") + listOf(
            password("serverPassword", "Password", EditorSection.PROTOCOL),
            text("serverSNI", "SNI", EditorSection.TLS),
            toggle("serverAllowInsecure", "Allow insecure", EditorSection.TLS),
            menu("serverNetwork", "Network", EditorSection.TRANSPORT, "none", listOf("none", "ws")),
            menu("serverEncryption", "Encryption", EditorSection.PROTOCOL, "none", listOf("none", "ss")),
            text("serverHost", "WebSocket host", EditorSection.TRANSPORT, visibility = listOf(FieldVisibility("serverNetwork", setOf("ws")))),
            text("serverPath", "WebSocket path", EditorSection.TRANSPORT, visibility = listOf(FieldVisibility("serverNetwork", setOf("ws")))),
            menu("serverMethod", "Shadowsocks method", EditorSection.ADVANCED, "AES-128-GCM", listOf("AES-128-GCM", "AES-256-GCM", "CHACHA20-IETF-POLY1305"), visibility = listOf(FieldVisibility("serverEncryption", setOf("ss")))),
            password("serverPassword1", "Shadowsocks password", EditorSection.ADVANCED, visibility = listOf(FieldVisibility("serverEncryption", setOf("ss")))),
        ) + sharedAdvanced
        ProtocolKind.MIERU -> common("profileName") + listOf(
            menu("serverProtocol", "Protocol", EditorSection.PROTOCOL, "TCP", listOf("TCP", "UDP")),
            text("serverUsername", "Username", EditorSection.PROTOCOL),
            password("serverPassword", "Password", EditorSection.PROTOCOL),
            number("serverMTU", "MTU", EditorSection.ADVANCED, "1400", visibility = listOf(FieldVisibility("serverProtocol", setOf("UDP")))),
        ) + sharedAdvanced
        ProtocolKind.NAIVE -> common("profileName") + listOf(
            text("serverUsername", "Username", EditorSection.PROTOCOL),
            password("serverPassword", "Password", EditorSection.PROTOCOL),
            menu("serverProtocol", "Protocol", EditorSection.TRANSPORT, "https", listOf("https", "quic")),
            text("serverHeaders", "Extra headers", EditorSection.TRANSPORT),
            text("serverSNI", "SNI", EditorSection.TLS),
            text("serverCertificates", "Certificates", EditorSection.TLS),
            number("serverInsecureConcurrency", "Insecure concurrency", EditorSection.ADVANCED, "0", min = 0),
            toggle("sUoT", "UDP over TCP", EditorSection.ADVANCED),
        ) + sharedAdvanced
        ProtocolKind.HYSTERIA,
        ProtocolKind.HYSTERIA2 -> listOf(
            text("profileName", "Name", EditorSection.COMMON),
            text(FieldKeys.SERVER_ADDRESS, "Server address", EditorSection.COMMON, "127.0.0.1", required = true),
            menu("protocolVersion", "Protocol version", EditorSection.PROTOCOL, if (kind == ProtocolKind.HYSTERIA2) "2" else "1", listOf("1", "2")),
            text("serverPorts", "Server ports", EditorSection.COMMON, if (kind == ProtocolKind.HYSTERIA2) "443" else "443"),
            password("serverObfs", "Obfuscation", EditorSection.PROTOCOL),
            menu("serverAuthType", "Authentication type", EditorSection.PROTOCOL, "0", listOf("0", "1", "2"), visibility = listOf(FieldVisibility("protocolVersion", setOf("1"))), labels = listOf("None", "String", "Base64")),
            password(
                "serverPassword",
                "Authentication payload / password",
                EditorSection.PROTOCOL,
                visibleWhen = { form ->
                    form["protocolVersion"] == "2" ||
                        (form["protocolVersion"] == "1" && form["serverAuthType"] != "0")
                },
            ),
            menu("serverProtocol", "Protocol", EditorSection.TRANSPORT, "0", listOf("0", "1", "2"), visibility = listOf(FieldVisibility("protocolVersion", setOf("1"))), labels = listOf("UDP", "FakeTCP (root required)", "WeChat Video")),
            text("serverSNI", "SNI", EditorSection.TLS),
            text("serverALPN", "ALPN", EditorSection.TLS, visibility = listOf(FieldVisibility("protocolVersion", setOf("1")))),
            text("serverCertificates", "Certificates", EditorSection.TLS),
            toggle("serverAllowInsecure", "Allow insecure", EditorSection.TLS),
            number("serverUploadSpeed", "Upload Mbps", EditorSection.ADVANCED, if (kind == ProtocolKind.HYSTERIA) "10" else "0", min = 0),
            number("serverDownloadSpeed", "Download Mbps", EditorSection.ADVANCED, if (kind == ProtocolKind.HYSTERIA) "50" else "0", min = 0),
            number("serverStreamReceiveWindow", "Stream receive window", EditorSection.ADVANCED, "0", min = 0, visibility = listOf(FieldVisibility("protocolVersion", setOf("1")))),
            number("serverConnectionReceiveWindow", "Connection receive window", EditorSection.ADVANCED, "0", min = 0, visibility = listOf(FieldVisibility("protocolVersion", setOf("1")))),
            toggle("serverDisableMtuDiscovery", "Disable MTU discovery", EditorSection.ADVANCED, visibility = listOf(FieldVisibility("protocolVersion", setOf("1")))),
            number("hopInterval", "Hop interval", EditorSection.ADVANCED, "10", min = 0),
        ) + sharedAdvanced
        ProtocolKind.SSH -> common("profileName") + listOf(
            text("serverUsername", "Username", EditorSection.PROTOCOL, "root"),
            menu("serverAuthType", "Authentication type", EditorSection.PROTOCOL, "1", listOf("0", "1", "2"), labels = listOf("None", "Password", "Public key")),
            password("serverPassword", "Password", EditorSection.PROTOCOL, visibility = listOf(FieldVisibility("serverAuthType", setOf("1")))),
            text("serverPrivateKey", "Private key", EditorSection.PROTOCOL, visibility = listOf(FieldVisibility("serverAuthType", setOf("2")))),
            password("serverPassword1", "Private key passphrase", EditorSection.PROTOCOL, visibility = listOf(FieldVisibility("serverAuthType", setOf("2")))),
            text("serverCertificates", "Public key", EditorSection.ADVANCED),
        ) + sharedAdvanced
        ProtocolKind.WIREGUARD -> common() + listOf(
            text("localAddress", "Local address", EditorSection.PROTOCOL),
            password("privateKey", "Private key", EditorSection.PROTOCOL),
            text("peerPublicKey", "Peer public key", EditorSection.PROTOCOL),
            password("peerPreSharedKey", "Peer preshared key", EditorSection.PROTOCOL),
            text("allowedIPs", "Allowed IPs", EditorSection.PROTOCOL, "0.0.0.0/0,::/0", required = true),
            number("mtu", "MTU", EditorSection.ADVANCED, "1420", min = 1),
            text("reserved", "Reserved", EditorSection.ADVANCED),
            number("persistentKeepalive", "Persistent keepalive", EditorSection.ADVANCED, "0", min = 0),
        ) + sharedAdvanced
        ProtocolKind.TUIC -> common("profileName") + listOf(
            text("serverUsername", "UUID", EditorSection.PROTOCOL),
            password("serverPassword", "Token", EditorSection.PROTOCOL),
            text("serverALPN", "ALPN", EditorSection.TLS),
            text("serverCertificates", "Certificates", EditorSection.TLS),
            menu("serverUDPRelayMode", "UDP relay mode", EditorSection.PROTOCOL, "native", listOf("native", "quic")),
            menu("serverCongestionController", "Congestion controller", EditorSection.PROTOCOL, "cubic", listOf("cubic", "new_reno", "bbr")),
            toggle("serverDisableSNI", "Disable SNI", EditorSection.TLS),
            text("serverSNI", "SNI", EditorSection.TLS, visibility = listOf(FieldVisibility("serverDisableSNI", setOf("true"), negate = true))),
            toggle("serverReduceRTT", "Reduce RTT", EditorSection.ADVANCED),
            toggle("serverAllowInsecure", "Allow insecure", EditorSection.TLS),
        ) + sharedAdvanced
        ProtocolKind.SHADOW_TLS -> common() + listOf(
            menu("version", "Protocol version", EditorSection.PROTOCOL, "3", listOf("2", "3")),
            password("password", "Password", EditorSection.PROTOCOL),
            text("sni", "SNI", EditorSection.TLS),
            text("alpn", "ALPN", EditorSection.TLS),
            text("certificates", "Certificates", EditorSection.TLS),
            toggle("allowInsecure", "Allow insecure", EditorSection.TLS),
            menu("utlsFingerprint", "uTLS fingerprint", EditorSection.TLS, "", listOf("", "chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")),
        ) + sharedAdvanced
        ProtocolKind.ANY_TLS -> common() + listOf(
            password("password", "Password", EditorSection.PROTOCOL),
            text("sni", "SNI", EditorSection.TLS),
            toggle("allowInsecure", "Allow insecure", EditorSection.TLS),
            text("alpn", "ALPN", EditorSection.TLS),
            text("certificates", "Certificates", EditorSection.TLS),
            menu("utlsFingerprint", "uTLS fingerprint", EditorSection.TLS, "", listOf("", "chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")),
        ) + sharedAdvanced
        ProtocolKind.CHAIN -> listOf(
            text(FieldKeys.NAME, "Name", EditorSection.COMMON),
        ) + sharedAdvanced
        ProtocolKind.CONFIG -> listOf(
            text(FieldKeys.NAME, "Name", EditorSection.COMMON),
            toggle("isOutboundOnly", "Outbound only", EditorSection.PROTOCOL),
            text("serverConfig", "Custom JSON", EditorSection.PROTOCOL, kind = EditorFieldKind.JSON),
        ) + sharedAdvanced
        ProtocolKind.NEKO -> listOf(
            text(FieldKeys.NAME, "Name", EditorSection.COMMON),
            text("pluginId", "Plugin ID", EditorSection.PROTOCOL),
            text("protocolId", "Protocol ID", EditorSection.PROTOCOL),
            text("sharedStorage", "Plugin shared storage", EditorSection.ADVANCED, kind = EditorFieldKind.JSON),
        )
    }

    fun fields(kind: ProtocolKind): List<EditorFieldSpec> = specs(kind)

    fun createBean(kind: ProtocolKind): AbstractBean = when (kind) {
        ProtocolKind.SOCKS -> SOCKSBean()
        ProtocolKind.HTTP -> HttpBean()
        ProtocolKind.SHADOWSOCKS -> ShadowsocksBean()
        ProtocolKind.VMESS -> VMessBean()
        ProtocolKind.VLESS -> VMessBean().apply { alterId = -1 }
        ProtocolKind.TROJAN -> TrojanBean()
        ProtocolKind.TROJAN_GO -> TrojanGoBean()
        ProtocolKind.MIERU -> MieruBean()
        ProtocolKind.NAIVE -> NaiveBean()
        ProtocolKind.HYSTERIA -> HysteriaBean().apply { protocolVersion = 1 }
        ProtocolKind.HYSTERIA2 -> HysteriaBean().apply { protocolVersion = 2 }
        ProtocolKind.SSH -> SSHBean()
        ProtocolKind.WIREGUARD -> WireGuardBean()
        ProtocolKind.TUIC -> TuicBean()
        ProtocolKind.SHADOW_TLS -> ShadowTLSBean()
        ProtocolKind.ANY_TLS -> AnyTLSBean()
        ProtocolKind.CHAIN -> ChainBean()
        ProtocolKind.CONFIG -> ConfigBean()
        ProtocolKind.NEKO -> NekoBean()
    }.applyDefaultValues()

    private fun value(form: Map<String, String>, key: String, fallback: String = "") = form[key] ?: fallback

    fun beanToForm(kind: ProtocolKind, bean: AbstractBean): Map<String, String> {
        bean.initializeDefaultValues()
        val form = linkedMapOf(
            "_protocol" to kind.routeKey,
            FieldKeys.NAME to (bean.name ?: ""),
            FieldKeys.SERVER_ADDRESS to (bean.serverAddress ?: ""),
            FieldKeys.SERVER_PORT to (bean.serverPort ?: 0).toString(),
            FieldKeys.CUSTOM_OUTBOUND_JSON to (bean.customOutboundJson ?: ""),
            FieldKeys.CUSTOM_CONFIG_JSON to (bean.customConfigJson ?: ""),
        )
        when (kind) {
            ProtocolKind.SOCKS -> (bean as SOCKSBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "serverProtocol" to it.protocol.toString(), "serverUsername" to it.username.orEmpty(), "serverPassword" to it.password.orEmpty(), "sUoT" to it.sUoT.toString())) }
            ProtocolKind.HTTP -> form.putAll(standardForm(bean as HttpBean, kind))
            ProtocolKind.SHADOWSOCKS -> (bean as ShadowsocksBean).let { form.putAll(mapOf("method" to it.method.orEmpty(), "password" to it.password.orEmpty(), "sUoT" to it.sUoT.toString()).plus(pluginFields(it.plugin))) }
            ProtocolKind.VMESS, ProtocolKind.VLESS, ProtocolKind.TROJAN -> form.putAll(standardForm(bean as StandardV2RayBean, kind))
            ProtocolKind.TROJAN_GO -> (bean as TrojanGoBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "serverPassword" to it.password.orEmpty(), "serverSNI" to it.sni.orEmpty(), "serverAllowInsecure" to it.allowInsecure.toString(), "serverNetwork" to it.type.takeIf { value -> value in setOf("none", "ws") }.orEmpty().ifBlank { "none" }, "serverHost" to it.host.orEmpty(), "serverPath" to it.path.orEmpty()).plus(trojanGoEncryption(it.encryption))) }
            ProtocolKind.MIERU -> (bean as MieruBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "serverProtocol" to it.protocol.orEmpty(), "serverUsername" to it.username.orEmpty(), "serverPassword" to it.password.orEmpty(), "serverMTU" to it.mtu.toString())) }
            ProtocolKind.NAIVE -> (bean as NaiveBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "serverProtocol" to it.proto.orEmpty(), "serverUsername" to it.username.orEmpty(), "serverPassword" to it.password.orEmpty(), "serverHeaders" to it.extraHeaders.orEmpty(), "serverSNI" to it.sni.orEmpty(), "serverCertificates" to it.certificates.orEmpty(), "serverInsecureConcurrency" to it.insecureConcurrency.toString(), "sUoT" to it.sUoT.toString())) }
            ProtocolKind.HYSTERIA, ProtocolKind.HYSTERIA2 -> (bean as HysteriaBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "protocolVersion" to it.protocolVersion.toString(), "serverPorts" to it.serverPorts.orEmpty(), "serverObfs" to it.obfuscation.orEmpty(), "serverAuthType" to it.authPayloadType.toString(), "serverPassword" to it.authPayload.orEmpty(), "serverProtocol" to it.protocol.toString(), "serverSNI" to it.sni.orEmpty(), "serverALPN" to it.alpn.orEmpty(), "serverCertificates" to it.caText.orEmpty(), "serverAllowInsecure" to it.allowInsecure.toString(), "serverUploadSpeed" to it.uploadMbps.toString(), "serverDownloadSpeed" to it.downloadMbps.toString(), "serverStreamReceiveWindow" to it.streamReceiveWindow.toString(), "serverConnectionReceiveWindow" to it.connectionReceiveWindow.toString(), "serverDisableMtuDiscovery" to it.disableMtuDiscovery.toString(), "hopInterval" to it.hopInterval.toString())) }
            ProtocolKind.SSH -> (bean as SSHBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "serverUsername" to it.username.orEmpty(), "serverAuthType" to it.authType.toString(), "serverPassword" to it.password.orEmpty(), "serverPrivateKey" to it.privateKey.orEmpty(), "serverPassword1" to it.privateKeyPassphrase.orEmpty(), "serverCertificates" to it.publicKey.orEmpty())) }
            ProtocolKind.WIREGUARD -> (bean as WireGuardBean).let { form.putAll(mapOf("localAddress" to it.localAddress.orEmpty(), "privateKey" to it.privateKey.orEmpty(), "peerPublicKey" to it.peerPublicKey.orEmpty(), "peerPreSharedKey" to it.peerPreSharedKey.orEmpty(), "allowedIPs" to it.allowedIPs.orEmpty().ifBlank { "0.0.0.0/0,::/0" }, "mtu" to it.mtu.toString(), "reserved" to it.reserved.orEmpty(), "persistentKeepalive" to it.persistentKeepalive.toString())) }
            ProtocolKind.TUIC -> (bean as TuicBean).let { form.putAll(mapOf("profileName" to it.name.orEmpty(), "serverUsername" to it.uuid.orEmpty(), "serverPassword" to it.token.orEmpty(), "serverALPN" to it.alpn.orEmpty(), "serverCertificates" to it.caText.orEmpty(), "serverUDPRelayMode" to it.udpRelayMode.orEmpty(), "serverCongestionController" to it.congestionController.orEmpty(), "serverDisableSNI" to it.disableSNI.toString(), "serverSNI" to it.sni.orEmpty(), "serverReduceRTT" to it.reduceRTT.toString(), "serverAllowInsecure" to it.allowInsecure.toString())) }
            ProtocolKind.SHADOW_TLS -> (bean as ShadowTLSBean).let { form.putAll(mapOf("version" to it.version.toString(), "password" to it.password.orEmpty(), "sni" to it.sni.orEmpty(), "alpn" to it.alpn.orEmpty(), "certificates" to it.certificates.orEmpty(), "allowInsecure" to it.allowInsecure.toString(), "utlsFingerprint" to it.utlsFingerprint.orEmpty())) }
            ProtocolKind.ANY_TLS -> (bean as AnyTLSBean).let { form.putAll(mapOf("password" to it.password.orEmpty(), "sni" to it.sni.orEmpty(), "allowInsecure" to it.allowInsecure.toString(), "alpn" to it.alpn.orEmpty(), "certificates" to it.certificates.orEmpty(), "utlsFingerprint" to it.utlsFingerprint.orEmpty())) }
            ProtocolKind.CHAIN -> (bean as ChainBean).let { form[FieldKeys.CHAIN_PROXIES] = it.proxies.orEmpty().joinToString(",") }
            ProtocolKind.CONFIG -> (bean as ConfigBean).let { form.putAll(mapOf("isOutboundOnly" to (it.type == 1).toString(), "serverConfig" to it.config.orEmpty())) }
            ProtocolKind.NEKO -> (bean as NekoBean).let { form.putAll(mapOf("pluginId" to it.plgId.orEmpty(), "protocolId" to it.protocolId.orEmpty(), "sharedStorage" to it.sharedStorage.toString())) }
        }
        return form
    }

    private fun standardForm(bean: StandardV2RayBean, kind: ProtocolKind): Map<String, String> = mapOf(
        "uuid" to (if (kind == ProtocolKind.TROJAN) (bean as TrojanBean).password else bean.uuid).orEmpty(),
        "username" to if (bean is HttpBean) bean.username.orEmpty() else "",
        "password" to if (bean is HttpBean) bean.password.orEmpty() else "",
        "alterId" to if (bean is VMessBean) bean.alterId.toString() else "0",
        "encryption" to bean.encryption.orEmpty(), "type" to bean.type.orEmpty(), "host" to bean.host.orEmpty(), "path" to bean.path.orEmpty(),
        "packetEncoding" to bean.packetEncoding.toString(), "wsMaxEarlyData" to bean.wsMaxEarlyData.toString(), "earlyDataHeaderName" to bean.earlyDataHeaderName.orEmpty(),
        "security" to bean.security.orEmpty(), "sni" to bean.sni.orEmpty(), "alpn" to bean.alpn.orEmpty(), "certificates" to bean.certificates.orEmpty(), "allowInsecure" to bean.allowInsecure.toString(),
        "utlsFingerprint" to bean.utlsFingerprint.orEmpty(), "realityPubKey" to bean.realityPubKey.orEmpty(), "realityShortId" to bean.realityShortId.orEmpty(), "enableECH" to bean.enableECH.toString(), "echConfig" to bean.echConfig.orEmpty(),
        "enableMux" to bean.enableMux.toString(), "muxPadding" to bean.muxPadding.toString(), "muxType" to bean.muxType.toString(), "muxConcurrency" to bean.muxConcurrency.toString(),
    )

    private fun pluginFields(plugin: String?): Map<String, String> = mapOf("pluginName" to plugin.orEmpty().substringBefore(";"), "pluginConfig" to plugin.orEmpty().substringAfter(";", ""))

    private fun trojanGoEncryption(encryption: String?): Map<String, String> {
        val raw = encryption.orEmpty()
        return if (raw.startsWith("ss;")) mapOf("serverEncryption" to "ss", "serverMethod" to raw.substringAfter(";").substringBefore(":"), "serverPassword1" to raw.substringAfter(":")) else mapOf("serverEncryption" to raw)
    }

    fun applyForm(kind: ProtocolKind, bean: AbstractBean, form: Map<String, String>): AbstractBean {
        bean.applyCommonForm(form + (FieldKeys.NAME to (form["profileName"] ?: form[FieldKeys.NAME].orEmpty())))
        when (kind) {
            ProtocolKind.SOCKS -> (bean as SOCKSBean).apply { protocol = value(form, "serverProtocol", "2").toIntOrNull() ?: 2; username = value(form, "serverUsername"); password = value(form, "serverPassword"); sUoT = value(form, "sUoT").toBoolean() }
            ProtocolKind.HTTP -> applyStandard(bean as HttpBean, kind, form)
            ProtocolKind.SHADOWSOCKS -> (bean as ShadowsocksBean).apply { method = value(form, "method"); password = value(form, "password"); val n = value(form, "pluginName"); plugin = if (n.isBlank()) "" else "$n;${value(form, "pluginConfig")}"; sUoT = value(form, "sUoT").toBoolean() }
            ProtocolKind.VMESS, ProtocolKind.VLESS, ProtocolKind.TROJAN -> applyStandard(bean as StandardV2RayBean, kind, form)
            ProtocolKind.TROJAN_GO -> (bean as TrojanGoBean).apply { password = value(form, "serverPassword"); sni = value(form, "serverSNI"); allowInsecure = value(form, "serverAllowInsecure").toBoolean(); type = value(form, "serverNetwork", "none"); host = value(form, "serverHost"); path = value(form, "serverPath"); encryption = if (value(form, "serverEncryption") == "ss") "ss;${value(form, "serverMethod", "AES-128-GCM")}:${value(form, "serverPassword1")}" else value(form, "serverEncryption", "none") }
            ProtocolKind.MIERU -> (bean as MieruBean).apply { protocol = value(form, "serverProtocol", "TCP"); username = value(form, "serverUsername"); password = value(form, "serverPassword"); mtu = value(form, "serverMTU", "1400").toIntOrNull() ?: 1400 }
            ProtocolKind.NAIVE -> (bean as NaiveBean).apply { proto = value(form, "serverProtocol", "https"); username = value(form, "serverUsername"); password = value(form, "serverPassword"); extraHeaders = value(form, "serverHeaders").replace("\r\n", "\n"); sni = value(form, "serverSNI"); certificates = value(form, "serverCertificates"); insecureConcurrency = value(form, "serverInsecureConcurrency").toIntOrNull() ?: 0; sUoT = value(form, "sUoT").toBoolean() }
            ProtocolKind.HYSTERIA, ProtocolKind.HYSTERIA2 -> (bean as HysteriaBean).apply { val defaultVersion = if (kind == ProtocolKind.HYSTERIA2) 2 else 1; protocolVersion = value(form, "protocolVersion", defaultVersion.toString()).toIntOrNull() ?: defaultVersion; serverPorts = value(form, "serverPorts", "443"); obfuscation = value(form, "serverObfs"); authPayloadType = value(form, "serverAuthType").toIntOrNull() ?: 0; authPayload = value(form, "serverPassword"); protocol = value(form, "serverProtocol").toIntOrNull() ?: 0; sni = value(form, "serverSNI"); alpn = value(form, "serverALPN"); caText = value(form, "serverCertificates"); allowInsecure = value(form, "serverAllowInsecure").toBoolean(); uploadMbps = value(form, "serverUploadSpeed").toIntOrNull() ?: 0; downloadMbps = value(form, "serverDownloadSpeed").toIntOrNull() ?: 0; streamReceiveWindow = value(form, "serverStreamReceiveWindow").toIntOrNull() ?: 0; connectionReceiveWindow = value(form, "serverConnectionReceiveWindow").toIntOrNull() ?: 0; disableMtuDiscovery = value(form, "serverDisableMtuDiscovery").toBoolean(); hopInterval = value(form, "hopInterval", "10").toIntOrNull() ?: 10 }
            ProtocolKind.SSH -> (bean as SSHBean).apply { username = value(form, "serverUsername", "root"); authType = value(form, "serverAuthType", "1").toIntOrNull() ?: 1; password = value(form, "serverPassword"); privateKey = value(form, "serverPrivateKey"); privateKeyPassphrase = value(form, "serverPassword1"); publicKey = value(form, "serverCertificates") }
            ProtocolKind.WIREGUARD -> (bean as WireGuardBean).apply { localAddress = value(form, "localAddress"); privateKey = value(form, "privateKey"); peerPublicKey = value(form, "peerPublicKey"); peerPreSharedKey = value(form, "peerPreSharedKey"); allowedIPs = value(form, "allowedIPs", "0.0.0.0/0,::/0").ifBlank { "0.0.0.0/0,::/0" }; mtu = value(form, "mtu", "1420").toIntOrNull() ?: 1420; reserved = value(form, "reserved"); persistentKeepalive = value(form, "persistentKeepalive").toIntOrNull() ?: 0 }
            ProtocolKind.TUIC -> (bean as TuicBean).apply { uuid = value(form, "serverUsername"); token = value(form, "serverPassword"); alpn = value(form, "serverALPN"); caText = value(form, "serverCertificates"); udpRelayMode = value(form, "serverUDPRelayMode", "native"); congestionController = value(form, "serverCongestionController", "cubic"); disableSNI = value(form, "serverDisableSNI").toBoolean(); sni = value(form, "serverSNI"); reduceRTT = value(form, "serverReduceRTT").toBoolean(); allowInsecure = value(form, "serverAllowInsecure").toBoolean() }
            ProtocolKind.SHADOW_TLS -> (bean as ShadowTLSBean).apply { version = value(form, "version", "3").toIntOrNull() ?: 3; password = value(form, "password"); sni = value(form, "sni"); alpn = value(form, "alpn"); certificates = value(form, "certificates"); allowInsecure = value(form, "allowInsecure").toBoolean(); utlsFingerprint = value(form, "utlsFingerprint") }
            ProtocolKind.ANY_TLS -> (bean as AnyTLSBean).apply { password = value(form, "password"); sni = value(form, "sni"); allowInsecure = value(form, "allowInsecure").toBoolean(); alpn = value(form, "alpn"); certificates = value(form, "certificates"); utlsFingerprint = value(form, "utlsFingerprint") }
            ProtocolKind.CHAIN -> (bean as ChainBean).apply { proxies = value(form, FieldKeys.CHAIN_PROXIES).split(',').mapNotNull { it.trim().toLongOrNull() } }
            ProtocolKind.CONFIG -> (bean as ConfigBean).apply { type = if (value(form, "isOutboundOnly").toBoolean()) 1 else 0; config = value(form, "serverConfig") }
            ProtocolKind.NEKO -> (bean as NekoBean).apply { plgId = value(form, "pluginId"); protocolId = value(form, "protocolId"); sharedStorage = NekoBean.tryParseJSON(value(form, "sharedStorage", "{}")) }
        }
        bean.initializeDefaultValues()
        return bean
    }

    private fun applyStandard(bean: StandardV2RayBean, kind: ProtocolKind, form: Map<String, String>) {
        bean.uuid = value(form, "uuid")
        if (bean is TrojanBean) bean.password = value(form, "uuid")
        if (bean is HttpBean) { bean.username = value(form, "username"); bean.password = value(form, "password") }
        if (bean is VMessBean) bean.alterId = if (kind == ProtocolKind.VLESS) -1 else value(form, "alterId").toIntOrNull() ?: 0
        bean.encryption = value(form, "encryption"); bean.type = value(form, "type", "tcp"); bean.host = value(form, "host"); bean.path = value(form, "path"); bean.packetEncoding = value(form, "packetEncoding").toIntOrNull() ?: 0; bean.wsMaxEarlyData = value(form, "wsMaxEarlyData").toIntOrNull() ?: 0; bean.earlyDataHeaderName = value(form, "earlyDataHeaderName"); bean.security = value(form, "security", if (kind == ProtocolKind.TROJAN) "tls" else "none"); bean.sni = value(form, "sni"); bean.alpn = value(form, "alpn"); bean.certificates = value(form, "certificates"); bean.allowInsecure = value(form, "allowInsecure").toBoolean(); bean.utlsFingerprint = value(form, "utlsFingerprint"); bean.realityPubKey = value(form, "realityPubKey"); bean.realityShortId = value(form, "realityShortId"); bean.enableECH = value(form, "enableECH").toBoolean(); bean.echConfig = value(form, "echConfig"); bean.enableMux = value(form, "enableMux").toBoolean(); bean.muxPadding = value(form, "muxPadding").toBoolean(); bean.muxType = value(form, "muxType").toIntOrNull() ?: 0; bean.muxConcurrency = value(form, "muxConcurrency", "1").toIntOrNull() ?: 1
    }

    fun validate(kind: ProtocolKind, form: Map<String, String>): Map<String, String> {
        val effectiveForm = if ("_protocol" in form) form else form + ("_protocol" to kind.routeKey)
        val errors = linkedMapOf<String, String>()
        if (kind.hasEndpoint) {
            if (value(effectiveForm, FieldKeys.SERVER_ADDRESS).isBlank()) errors[FieldKeys.SERVER_ADDRESS] = "Server address is required"
            if (kind == ProtocolKind.HYSTERIA || kind == ProtocolKind.HYSTERIA2) {
                val parts = value(effectiveForm, "serverPorts").split(',')
                val valid = parts.isNotEmpty() && parts.all { part ->
                    val bounds = part.trim().split('-')
                    bounds.size in 1..2 && bounds.all { bound ->
                        bound.toIntOrNull()?.let { it in 1..65535 } == true
                    } &&
                        (bounds.size == 1 || bounds[0].toInt() <= bounds[1].toInt())
                }
                if (!valid) errors["serverPorts"] = "Use ports or ranges between 1 and 65535"
            } else {
                val port = value(effectiveForm, FieldKeys.SERVER_PORT).toIntOrNull()
                if (port == null || port !in 1..65535) errors[FieldKeys.SERVER_PORT] = "Port must be between 1 and 65535"
            }
        }
        specs(kind).filter { it.isVisible(effectiveForm) }.forEach { field ->
            val raw = value(effectiveForm, field.key)
            if (field.required && raw.isBlank()) errors[field.key] = "${field.label} is required"
            if (field.kind == EditorFieldKind.NUMBER && raw.isNotBlank()) {
                val n = raw.toIntOrNull()
                if (n == null) errors[field.key] = "${field.label} must be a number"
                else if (field.min != null && n < field.min) errors[field.key] = "${field.label} is too small"
                else if (field.max != null && n > field.max) errors[field.key] = "${field.label} is too large"
            }
        }
        if (kind == ProtocolKind.CONFIG) {
            val json = value(effectiveForm, "serverConfig").trim()
            if (json.isNotEmpty()) try { JSONObject(json) } catch (_: Exception) { errors["serverConfig"] = "Invalid JSON" }
        }
        if (kind == ProtocolKind.NEKO) {
            val json = value(effectiveForm, "sharedStorage").trim()
            if (json.isNotEmpty()) try {
                JSONObject(json)
            } catch (_: Exception) {
                errors["sharedStorage"] = "Invalid JSON"
            }
        }
        listOf(FieldKeys.CUSTOM_OUTBOUND_JSON, FieldKeys.CUSTOM_CONFIG_JSON).forEach { key ->
            val json = value(effectiveForm, key).trim()
            if (json.isNotEmpty()) try {
                JSONObject(json)
            } catch (_: Exception) {
                errors[key] = "Invalid JSON"
            }
        }
        return errors
    }
}
