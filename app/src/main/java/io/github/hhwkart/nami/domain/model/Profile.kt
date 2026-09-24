package io.github.hhwkart.nami.domain.model

/** Network endpoint information that can be displayed without exposing protocol-specific beans. */
data class Endpoint(
    val host: String,
    val port: Int? = null,
) {
    init {
        require(host.isNotBlank()) { "Endpoint host cannot be blank" }
        require(port == null || port in 0..65535) { "Endpoint port is out of range: $port" }
    }
}

/**
 * Transitional protocol payload boundary.
 *
 * The current database stores protocol beans as serialized BLOBs. Keeping the bytes opaque in the
 * domain allows the first architecture step to remain lossless while concrete protocol models are
 * migrated one at a time outside this package.
 */
class ProfilePayload(
    val protocol: ProtocolType,
    payload: ByteArray,
) {
    private val serialized = payload.copyOf()

    val bytes: ByteArray
        get() = serialized.copyOf()

    override fun equals(other: Any?): Boolean =
        other is ProfilePayload && protocol == other.protocol && serialized.contentEquals(other.serialized)

    override fun hashCode(): Int = 31 * protocol.hashCode() + serialized.contentHashCode()

    override fun toString(): String = "ProfilePayload(protocol=$protocol, bytes=${serialized.size})"
}

data class TrafficTotals(
    val uploadedBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
) {
    init {
        require(uploadedBytes >= 0L) { "Uploaded bytes cannot be negative" }
        require(downloadedBytes >= 0L) { "Downloaded bytes cannot be negative" }
    }

    val totalBytes: Long
        get() = uploadedBytes + downloadedBytes
}

sealed interface ProfileLatency {
    data object NotTested : ProfileLatency
    data object Pending : ProfileLatency

    data class Measured(val milliseconds: Int) : ProfileLatency {
        init {
            require(milliseconds >= 0) { "Latency cannot be negative" }
        }
    }

    data class Failed(val message: String) : ProfileLatency {
        init {
            require(message.isNotBlank()) { "Latency failure message cannot be blank" }
        }
    }
}

enum class ProfileAvailability {
    Unknown,
    Available,
    Unavailable,
}

data class Profile(
    val id: ProfileId = ProfileId.Unassigned,
    val groupId: GroupId = GroupId.Unassigned,
    val userOrder: Long = 0L,
    val displayName: String = "",
    val protocol: ProtocolType,
    val endpoint: Endpoint? = null,
    val traffic: TrafficTotals = TrafficTotals(),
    val latency: ProfileLatency = ProfileLatency.NotTested,
    val availability: ProfileAvailability = ProfileAvailability.Unknown,
    val errorMessage: String? = null,
    val configuration: ProfilePayload,
) {
    init {
        require(userOrder >= 0L) { "Profile order cannot be negative" }
        require(configuration.protocol == protocol) {
            "Profile protocol and payload protocol must match"
        }
    }
}
