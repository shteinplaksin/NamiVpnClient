package io.github.hhwkart.nami.domain.repository

import io.github.hhwkart.nami.domain.model.ProfileId
import io.github.hhwkart.nami.domain.model.RuleId
import io.github.hhwkart.nami.domain.model.RoutingPresetId
import io.github.hhwkart.nami.domain.model.RoutingPresetSelection
import io.github.hhwkart.nami.domain.model.RoutingRule
import kotlinx.coroutines.flow.Flow

interface RoutingRepository {
    fun observeRules(): Flow<List<RoutingRule>>

    fun observePresetSelection(): Flow<RoutingPresetSelection>

    suspend fun getRule(ruleId: RuleId): RoutingRule?

    suspend fun saveRule(rule: RoutingRule): RoutingRule

    suspend fun deleteRule(ruleId: RuleId): Boolean

    suspend fun applyPreset(presetId: RoutingPresetId): RoutingPresetSelection

    suspend fun markCustomPreset()

    suspend fun normalizeProfileOutbound(profileId: ProfileId): RoutingRule
}
