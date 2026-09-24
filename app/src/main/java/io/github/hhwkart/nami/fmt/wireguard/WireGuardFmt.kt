package io.github.hhwkart.nami.fmt.wireguard

import io.github.hhwkart.nami.core.SingBoxOptions
import io.github.hhwkart.nami.core.utils.Util
import io.github.hhwkart.nami.core.utils.listByLineOrComma

fun genReserved(anyStr: String): String {
    try {
        val list = anyStr.listByLineOrComma()
        val ba = ByteArray(3)
        if (list.size == 3) {
            list.forEachIndexed { index, s ->
                val i = s
                    .replace("[", "")
                    .replace("]", "")
                    .replace(" ", "")
                    .toIntOrNull() ?: return anyStr
                ba[index] = i.toByte()
            }
            return Util.b64EncodeOneLine(ba)
        } else {
            return anyStr
        }
    } catch (e: Exception) {
        return anyStr
    }
}

fun buildSingBoxOutboundWireguardBean(bean: WireGuardBean): SingBoxOptions.Outbound_WireGuardOptions {
    return SingBoxOptions.Outbound_WireGuardOptions().apply {
        type = "wireguard"
        local_address = bean.localAddress.listByLineOrComma()
        private_key = bean.privateKey
        mtu = bean.mtu
        peers = listOf(SingBoxOptions.WireGuardPeer().apply {
            server = bean.serverAddress
            server_port = bean.serverPort
            public_key = bean.peerPublicKey
            pre_shared_key = bean.peerPreSharedKey
            allowed_ips = bean.allowedIPs.listByLineOrComma()
            if (bean.reserved.isNotBlank()) reserved = genReserved(bean.reserved)
            if (bean.persistentKeepalive in 1..65535) {
                persistent_keepalive_interval = bean.persistentKeepalive
            }
        })
    }
}
