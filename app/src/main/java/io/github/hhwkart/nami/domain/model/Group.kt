package io.github.hhwkart.nami.domain.model

data class ProfileGroup(
    val id: GroupId = GroupId.Unassigned,
    val userOrder: Long = 0L,
    val ungrouped: Boolean = false,
    val name: String? = null,
    val type: GroupType = GroupType.Basic,
    val order: GroupSortOrder = GroupSortOrder.Origin,
    val isSelector: Boolean = false,
    val frontProxy: ProfileId? = null,
    val landingProxy: ProfileId? = null,
    val subscription: Subscription? = null,
) {
    init {
        require(userOrder >= 0L) { "Group order cannot be negative" }
    }

    val hasSubscription: Boolean
        get() = subscription != null
}
