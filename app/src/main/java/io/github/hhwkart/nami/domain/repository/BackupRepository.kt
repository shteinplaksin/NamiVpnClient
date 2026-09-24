package io.github.hhwkart.nami.domain.repository

import io.github.hhwkart.nami.domain.model.BackupArtifact
import io.github.hhwkart.nami.domain.model.BackupInput
import io.github.hhwkart.nami.domain.model.BackupPreview
import io.github.hhwkart.nami.domain.model.BackupRestoreRequest
import io.github.hhwkart.nami.domain.model.BackupRestoreResult
import io.github.hhwkart.nami.domain.model.BackupSelection
import io.github.hhwkart.nami.domain.model.BackupValidation

interface BackupRepository {
    suspend fun export(selection: BackupSelection): BackupArtifact

    suspend fun inspect(input: BackupInput): BackupPreview

    suspend fun validate(input: BackupInput, selection: BackupSelection): BackupValidation

    /** Implementations validate fully and acquire service quiescence before mutating durable state. */
    suspend fun restore(request: BackupRestoreRequest): BackupRestoreResult
}
