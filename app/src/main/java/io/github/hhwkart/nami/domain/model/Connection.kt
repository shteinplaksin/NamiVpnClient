package io.github.hhwkart.nami.domain.model

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Connecting : ConnectionState
    data object Connected : ConnectionState
    data object Stopping : ConnectionState
    data object Stopped : ConnectionState

    data class Failed(val message: String) : ConnectionState {
        init {
            require(message.isNotBlank()) { "Connection failure message cannot be blank" }
        }
    }
}

data class TrafficStats(
    val uploadedBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val uploadRateBytesPerSecond: Long = 0L,
    val downloadRateBytesPerSecond: Long = 0L,
    val durationMillis: Long = 0L,
) {
    init {
        require(uploadedBytes >= 0L) { "Uploaded bytes cannot be negative" }
        require(downloadedBytes >= 0L) { "Downloaded bytes cannot be negative" }
        require(uploadRateBytesPerSecond >= 0L) { "Upload rate cannot be negative" }
        require(downloadRateBytesPerSecond >= 0L) { "Download rate cannot be negative" }
        require(durationMillis >= 0L) { "Connection duration cannot be negative" }
    }

    val totalBytes: Long
        get() = uploadedBytes + downloadedBytes
}

data class ConnectionSnapshot(
    val state: ConnectionState = ConnectionState.Idle,
    val profileId: ProfileId? = null,
    val profileName: String? = null,
    val message: String? = null,
    val connectedAtEpochMillis: Long? = null,
    val traffic: TrafficStats = TrafficStats(),
)

data class LatencyRequest(
    val url: String,
    val timeoutMillis: Long = 3_000L,
    val concurrency: Int = 1,
) {
    init {
        require(timeoutMillis > 0L) { "Latency timeout must be positive" }
        require(concurrency > 0) { "Latency concurrency must be positive" }
    }
}

sealed interface LatencyResult {
    data object NotStarted : LatencyResult
    data object InProgress : LatencyResult

    data class Success(val milliseconds: Int) : LatencyResult {
        init {
            require(milliseconds >= 0) { "Latency cannot be negative" }
        }
    }

    data object TimedOut : LatencyResult

    data class Failed(val message: String) : LatencyResult {
        init {
            require(message.isNotBlank()) { "Latency failure message cannot be blank" }
        }
    }
}
