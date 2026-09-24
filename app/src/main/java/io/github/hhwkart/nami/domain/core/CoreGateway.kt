package io.github.hhwkart.nami.domain.core

import io.github.hhwkart.nami.domain.model.ConnectionSnapshot
import io.github.hhwkart.nami.domain.model.LatencyRequest
import io.github.hhwkart.nami.domain.model.LatencyResult
import io.github.hhwkart.nami.domain.model.ProfileId
import kotlinx.coroutines.flow.Flow

/** Runtime boundary for service/core operations; concrete Android and sing-box types stay outside domain. */
interface CoreGateway {
    fun observeConnection(): Flow<ConnectionSnapshot>

    suspend fun connect(profileId: ProfileId): Result<ConnectionSnapshot>

    suspend fun disconnect(): Result<Unit>

    suspend fun reload(): Result<Unit>

    suspend fun testLatency(profileId: ProfileId, request: LatencyRequest): LatencyResult

    suspend fun resetUpstreamConnections(): Result<Unit>
}
