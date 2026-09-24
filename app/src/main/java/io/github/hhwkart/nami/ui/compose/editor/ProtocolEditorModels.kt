package io.github.hhwkart.nami.ui.compose.editor

import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.fmt.AbstractBean
import io.github.hhwkart.nami.fmt.hysteria.HysteriaBean
import io.github.hhwkart.nami.fmt.v2ray.VMessBean

enum class EditorSection {
    COMMON,
    PROTOCOL,
    TRANSPORT,
    TLS,
    ADVANCED,
}

enum class EditorFieldKind {
    TEXT,
    NUMBER,
    PASSWORD,
    SWITCH,
    DROPDOWN,
    MULTILINE,
    JSON,
}

data class EditorOption(
    val label: String,
    val value: String,
)

data class FieldVisibility(
    val key: String,
    val values: Set<String>,
    val negate: Boolean = false,
) {
    fun matches(form: Map<String, String>): Boolean {
        val contains = form[key] in values
        return if (negate) !contains else contains
    }
}

data class EditorFieldSpec(
    val key: String,
    val label: String,
    val kind: EditorFieldKind,
    val section: EditorSection,
    val defaultValue: String = "",
    val options: List<EditorOption> = emptyList(),
    val required: Boolean = false,
    val min: Int? = null,
    val max: Int? = null,
    val visibility: List<FieldVisibility> = emptyList(),
    val supportingText: String? = null,
    val visibleWhen: ((Map<String, String>) -> Boolean)? = null,
) {
    fun isVisible(form: Map<String, String>): Boolean =
        visibility.all { it.matches(form) } && visibleWhen?.invoke(form) != false
}

enum class ProtocolKind(
    val routeKey: String,
    val displayName: String,
    val entityType: Int,
    val hasEndpoint: Boolean = true,
    val selectableForNew: Boolean = true,
) {
    SOCKS("socks", "SOCKS", ProxyEntity.TYPE_SOCKS),
    HTTP("http", "HTTP / HTTPS", ProxyEntity.TYPE_HTTP),
    SHADOWSOCKS("shadowsocks", "Shadowsocks", ProxyEntity.TYPE_SS),
    VMESS("vmess", "VMess", ProxyEntity.TYPE_VMESS),
    VLESS("vless", "VLESS", ProxyEntity.TYPE_VMESS),
    TROJAN("trojan", "Trojan", ProxyEntity.TYPE_TROJAN),
    TROJAN_GO("trojan-go", "Trojan-Go", ProxyEntity.TYPE_TROJAN_GO),
    MIERU("mieru", "Mieru", ProxyEntity.TYPE_MIERU),
    NAIVE("naive", "NaiveProxy", ProxyEntity.TYPE_NAIVE),
    HYSTERIA("hysteria", "Hysteria", ProxyEntity.TYPE_HYSTERIA),
    HYSTERIA2("hysteria2", "Hysteria 2", ProxyEntity.TYPE_HYSTERIA),
    SSH("ssh", "SSH", ProxyEntity.TYPE_SSH),
    WIREGUARD("wireguard", "WireGuard", ProxyEntity.TYPE_WG),
    TUIC("tuic", "TUIC", ProxyEntity.TYPE_TUIC),
    SHADOW_TLS("shadowtls", "ShadowTLS", ProxyEntity.TYPE_SHADOWTLS),
    ANY_TLS("anytls", "AnyTLS", ProxyEntity.TYPE_ANYTLS),
    CHAIN("chain", "Proxy chain", ProxyEntity.TYPE_CHAIN, hasEndpoint = false),
    CONFIG("config", "Custom sing-box", ProxyEntity.TYPE_CONFIG, hasEndpoint = false),
    NEKO(
        "neko",
        "Neko plugin",
        ProxyEntity.TYPE_NEKO,
        hasEndpoint = false,
        selectableForNew = false,
    ),
    ;

    companion object {
        fun fromRouteKey(value: String?): ProtocolKind =
            entries.firstOrNull { it.routeKey == value } ?: SOCKS

        fun fromEntity(entity: ProxyEntity): ProtocolKind = when (entity.type) {
            ProxyEntity.TYPE_VMESS -> if ((entity.requireBean() as VMessBean).isVLESS) VLESS else VMESS
            ProxyEntity.TYPE_HYSTERIA -> if ((entity.requireBean() as HysteriaBean).protocolVersion == 2) {
                HYSTERIA2
            } else {
                HYSTERIA
            }
            else -> entries.firstOrNull { it.entityType == entity.type } ?: SOCKS
        }
    }
}

data class ProtocolEditorUiState(
    val loading: Boolean = true,
    val profileId: Long = 0L,
    val groupId: Long = 0L,
    val protocol: ProtocolKind = ProtocolKind.SOCKS,
    val isNew: Boolean = true,
    val form: Map<String, String> = emptyMap(),
    val errors: Map<String, String> = emptyMap(),
    val isValid: Boolean = false,
    val isSaving: Boolean = false,
    val isTesting: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
    val dirty: Boolean = false,
    val chainCandidates: List<ChainCandidate> = emptyList(),
)

data class ChainCandidate(
    val id: Long,
    val name: String,
    val type: String,
    val selected: Boolean,
)

internal fun AbstractBean.applyCommonForm(form: Map<String, String>) {
    name = form[FieldKeys.NAME].orEmpty()
    serverAddress = form[FieldKeys.SERVER_ADDRESS].orEmpty()
    serverPort = form[FieldKeys.SERVER_PORT]?.toIntOrNull() ?: serverPort
    customOutboundJson = form[FieldKeys.CUSTOM_OUTBOUND_JSON].orEmpty()
    customConfigJson = form[FieldKeys.CUSTOM_CONFIG_JSON].orEmpty()
}

object FieldKeys {
    const val NAME = "name"
    const val SERVER_ADDRESS = "serverAddress"
    const val SERVER_PORT = "serverPort"
    const val CUSTOM_OUTBOUND_JSON = "customOutboundJson"
    const val CUSTOM_CONFIG_JSON = "customConfigJson"
    const val CHAIN_PROXIES = "chainProxies"
}
