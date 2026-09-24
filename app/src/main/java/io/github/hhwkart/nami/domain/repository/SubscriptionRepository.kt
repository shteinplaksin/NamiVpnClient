package io.github.hhwkart.nami.domain.repository

import io.github.hhwkart.nami.domain.model.GroupId
import io.github.hhwkart.nami.domain.model.Subscription
import io.github.hhwkart.nami.domain.model.SubscriptionRecord
import io.github.hhwkart.nami.domain.model.SubscriptionUpdateReason
import kotlinx.coroutines.flow.Flow

interface SubscriptionRepository {
    fun observeSubscriptions(): Flow<List<SubscriptionRecord>>

    suspend fun saveSubscription(groupId: GroupId, subscription: Subscription): SubscriptionRecord

    suspend fun updateSubscription(
        groupId: GroupId,
        reason: SubscriptionUpdateReason = SubscriptionUpdateReason.Manual,
    ): SubscriptionRecord

    suspend fun cancelSubscriptionUpdate(groupId: GroupId)
}
