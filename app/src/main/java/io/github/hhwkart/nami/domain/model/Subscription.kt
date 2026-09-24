package io.github.hhwkart.nami.domain.model

data class Subscription(
    val typeCode: Int = 0,
    val link: String = "",
    val token: String = "",
    val forceResolve: Boolean = false,
    val deduplication: Boolean = false,
    val updateWhenConnectedOnly: Boolean = false,
    val customUserAgent: String = "",
    val autoUpdate: Boolean = false,
    val autoUpdateDelayMinutes: Int = 1440,
    val lastUpdatedEpochSeconds: Long = 0L,
    val bytesUsed: Long = 0L,
    val bytesRemaining: Long = 0L,
    val username: String = "",
    val expiryEpochSeconds: Long? = null,
    val protocols: Set<String> = emptySet(),
    val userInfo: String = "",
) {
    init {
        require(autoUpdateDelayMinutes >= 0) { "Subscription update delay cannot be negative" }
        require(lastUpdatedEpochSeconds >= 0L) { "Last update time cannot be negative" }
        require(bytesUsed >= 0L) { "Used subscription bytes cannot be negative" }
        require(bytesRemaining >= 0L) { "Remaining subscription bytes cannot be negative" }
        require(expiryEpochSeconds == null || expiryEpochSeconds >= 0L) {
            "Subscription expiry time cannot be negative"
        }
    }
}

data class SubscriptionRecord(
    val groupId: GroupId,
    val subscription: Subscription,
    val updateState: SubscriptionUpdateState = SubscriptionUpdateState.Idle,
)

sealed interface SubscriptionUpdateState {
    data object Idle : SubscriptionUpdateState

    data class Running(val progressPercent: Int? = null) : SubscriptionUpdateState {
        init {
            require(progressPercent == null || progressPercent in 0..100) {
                "Subscription progress must be between 0 and 100"
            }
        }
    }

    data class Succeeded(
        val changedProfiles: Int,
        val completedAtEpochSeconds: Long,
    ) : SubscriptionUpdateState {
        init {
            require(changedProfiles >= 0) { "Changed profile count cannot be negative" }
            require(completedAtEpochSeconds >= 0L) { "Completion time cannot be negative" }
        }
    }

    data class Failed(val message: String) : SubscriptionUpdateState {
        init {
            require(message.isNotBlank()) { "Subscription failure message cannot be blank" }
        }
    }
}

enum class SubscriptionUpdateReason {
    Manual,
    Automatic,
    Startup,
}
