package io.github.hhwkart.nami.ui.compose.importexport

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.BuildConfig
import io.github.hhwkart.nami.Key
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ParcelizeBridge
import io.github.hhwkart.nami.database.ProxyEntity
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.database.RuleEntity
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.database.preference.PublicDatabase
import io.github.hhwkart.nami.database.preference.KeyValuePair
import io.github.hhwkart.nami.data.backup.LegacyRestoreRecovery
import io.github.hhwkart.nami.data.backup.LogicalRestorePreImage
import io.github.hhwkart.nami.data.backup.RestoreJournalEntry
import io.github.hhwkart.nami.data.serialization.BackupJsonCodec
import io.github.hhwkart.nami.data.serialization.BackupJsonDocument
import io.github.hhwkart.nami.data.serialization.LegacyBackupThemePolicy
import io.github.hhwkart.nami.domain.model.BackupFormatVersion
import io.github.hhwkart.nami.ktx.readableMessage
import io.github.hhwkart.nami.ui.compose.routing.RoutingPresetManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import io.github.hhwkart.nami.core.utils.Util
import java.io.File
import java.io.Reader
import java.util.Date
import javax.inject.Inject

data class BackupImportSelection(
    val content: String,
    val hasConfigurations: Boolean,
    val hasRules: Boolean,
    val hasSettings: Boolean,
    val configurations: Boolean = hasConfigurations,
    val rules: Boolean = hasRules,
    val settings: Boolean = hasSettings,
)

data class BackupRestoreUiState(
    val busy: Boolean = false,
    val exportContent: String? = null,
    val shareUri: Uri? = null,
    val pendingImport: BackupImportSelection? = null,
    val message: String? = null,
    val error: String? = null,
)

private data class ProtectedSettingsSnapshot(
    val onboardingSeen: Boolean,
    val whatsNewVersion: String,
)

private data class DecodedBackupPayload(
    val profiles: List<ProxyEntity>?,
    val groups: List<ProxyGroup>?,
    val rules: List<RuleEntity>?,
    val settings: List<KeyValuePair>?,
    val importedSelectedProxyId: Long?,
    val importedCurrentProfileId: Long?,
)

@HiltViewModel
class BackupRestoreViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()
    private val restoreMutex = Mutex()

    private val restoreJournal by lazy { LegacyRestoreRecovery.journal(SagerNet.application) }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { LegacyRestoreRecovery.recoverPending(SagerNet.application) }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(error = "Restore recovery failed: ${throwable.message ?: "unknown error"}")
                    }
                }
        }
    }

    fun prepareExport(selection: BackupSelection) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching {
                withContext(Dispatchers.IO) {
                    doBackup(
                        profile = selection.configurations,
                        rule = selection.rules,
                        setting = selection.settings,
                    )
                }
            }.onSuccess { content ->
                _uiState.update { it.copy(busy = false, exportContent = content) }
            }.onFailure { throwable -> fail(throwable) }
        }
    }

    fun consumeExportContent() {
        _uiState.update { it.copy(exportContent = null) }
    }

    fun writeExport(contentResolver: ContentResolver, uri: Uri, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                contentResolver.openOutputStream(uri)?.bufferedWriter().use { writer ->
                    checkNotNull(writer) { "Unable to open export destination" }
                    writer.write(content)
                }
            }.onSuccess {
                _uiState.update {
                    it.copy(message = "Backup exported", error = null)
                }
            }.onFailure { throwable -> fail(throwable) }
        }
    }

    fun prepareShare(context: Context, selection: BackupSelection) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching {
                val content = withContext(Dispatchers.IO) {
                    doBackup(selection.configurations, selection.rules, selection.settings)
                }
                val cacheFile = File(
                    context.cacheDir,
                    "nami_backup_${Date().time}.json",
                ).apply {
                    parentFile?.mkdirs()
                    writeText(content)
                }
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.cache",
                    cacheFile,
                )
            }.onSuccess { uri ->
                _uiState.update { it.copy(busy = false, shareUri = uri) }
            }.onFailure { throwable -> fail(throwable) }
        }
    }

    fun consumeShareUri() {
        _uiState.update { it.copy(shareUri = null) }
    }

    fun importFile(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            runCatching {
                val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                } ?: uri.path.orEmpty().substringAfterLast('/').substringAfter(':')
                if (!fileName.endsWith(".json", ignoreCase = true)) {
                    error("Not a backup file: $fileName")
                }
                val text = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                        readBoundedText(it)
                    }
                        ?: error("Unable to open backup file")
                }
                val document = BackupJsonCodec.decode(text)
                BackupRestoreValidation.validate(document)
                BackupImportSelection(
                    content = text,
                    hasConfigurations = document.profiles != null || document.groups != null,
                    hasRules = document.rules != null,
                    hasSettings = document.settings != null,
                )
            }.onSuccess { pending ->
                _uiState.update { it.copy(busy = false, pendingImport = pending) }
            }.onFailure { throwable -> fail(throwable) }
        }
    }

    fun updateImportSelection(transform: (BackupImportSelection) -> BackupImportSelection) {
        _uiState.update { state -> state.pendingImport?.let { state.copy(pendingImport = transform(it)) } ?: state }
    }

    fun cancelImport() {
        _uiState.update { it.copy(pendingImport = null) }
    }

    /** Stops the service first, then restores selected categories, and restarts only on success. */
    fun applyImport(
        onStopService: () -> Unit,
        onRestartAfterSuccess: () -> Unit,
    ) {
        val pending = _uiState.value.pendingImport ?: return
        if (!pending.configurations && !pending.rules && !pending.settings) {
            _uiState.update { it.copy(error = "Select at least one backup category") }
            return
        }
        viewModelScope.launch {
            restoreMutex.withLock {
                if (_uiState.value.busy) return@withLock
                _uiState.update { it.copy(busy = true, error = null) }
                val decoded = try {
                    withContext(Dispatchers.IO) {
                        val document = BackupJsonCodec.decode(pending.content)
                        BackupRestoreValidation.validate(document)
                        document to decodeBackupPayload(document, pending)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (throwable: Exception) {
                    fail(throwable)
                    return@withLock
                }
                val document = decoded.first
                val payload = decoded.second
                runCatching {
                    SagerNet.withServiceLaunchLock {
                        withContext(Dispatchers.Main) { onStopService() }
                        withContext(Dispatchers.Default) { awaitServiceStopped() }
                        val protectedSettings = withContext(Dispatchers.IO) {
                            ProtectedSettingsSnapshot(
                                onboardingSeen = DataStore.onboardingSeen,
                                whatsNewVersion = DataStore.whatsNewVersion,
                            )
                        }
                        var journalEntry: RestoreJournalEntry? = null
                        var commitRecorded = false
                        withContext(Dispatchers.IO) {
                            capturePreImage(
                                document = document,
                                configurations = pending.configurations,
                                rules = pending.rules,
                                settings = pending.settings,
                                profilePointersWillBeReset = payload.profiles != null,
                            )?.let { preImage ->
                                journalEntry = restoreJournal.prepare(preImage)
                            }

                            try {
                                val importedSettings = finishImport(payload)
                                val expectedSelectedProxy = importedSettings
                                    ?.firstOrNull { it.key == Key.PROFILE_ID }
                                    ?.long
                                    ?: if (payload.profiles != null) 0L else null
                                val expectedCurrentProfile = importedSettings
                                    ?.firstOrNull { it.key == Key.PROFILE_CURRENT }
                                    ?.long
                                    ?: if (payload.profiles != null) 0L else null
                                verifyImport(
                                    document = document,
                                    configurations = pending.configurations,
                                    rules = pending.rules,
                                    settings = pending.settings,
                                    importedSettings = importedSettings,
                                    expectedSelectedProxy = expectedSelectedProxy,
                                    expectedCurrentProfile = expectedCurrentProfile,
                                    protectedSettings = protectedSettings,
                                )
                                journalEntry?.let { entry ->
                                    restoreJournal.markCommitted(entry.operationId)
                                    commitRecorded = true
                                    // A committed marker is cleanup state, not a
                                    // post-image transaction log. If deletion
                                    // fails, the next restore replaces it.
                                    try {
                                        restoreJournal.clearCommitted(entry.operationId)
                                    } catch (_: Throwable) {
                                        // Keep the committed marker for the
                                        // next recovery/cleanup pass.
                                    }
                                }
                            } catch (error: Throwable) {
                                if (journalEntry != null && !commitRecorded) {
                                    try {
                                        LegacyRestoreRecovery.restorePreImage(journalEntry!!.preImage)
                                        LegacyRestoreRecovery.verifyPreImage(journalEntry!!.preImage)
                                        restoreJournal.clearPreparedAfterRollback(
                                            journalEntry!!.operationId,
                                        )
                                    } catch (rollbackError: Throwable) {
                                        error.addSuppressed(rollbackError)
                                    }
                                }
                                throw error
                            }
                        }
                    }
                    withContext(Dispatchers.Main) { onRestartAfterSuccess() }
                }.onSuccess {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            pendingImport = null,
                            message = "Backup imported",
                        )
                    }
                }.onFailure { throwable -> fail(throwable) }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private suspend fun awaitServiceStopped() {
        SagerNet.awaitServiceStopped(SERVICE_QUIESCENCE_TIMEOUT_MILLIS)
    }

    private fun readBoundedText(reader: Reader): String {
        val result = StringBuilder()
        val buffer = CharArray(8 * 1024)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (result.length + count > MAX_BACKUP_CHARS) {
                error("Backup file is too large")
            }
            result.append(buffer, 0, count)
        }
        return result.toString()
    }

    private fun doBackup(profile: Boolean, rule: Boolean, setting: Boolean): String {
        val profiles = if (profile) {
            SagerDatabase.proxyDao.getAll().map { it.toBase64Str() }
        } else null
        val groups = if (profile) {
            SagerDatabase.groupDao.allGroups().map { it.toBase64Str() }
        } else null
        val rules = if (rule) {
            SagerDatabase.rulesDao.allRules().map { it.toBase64Str() }
        } else null
        val settings = if (setting) {
            PublicDatabase.kvPairDao.all()
                .filterNot {
                    it.key == Key.ONBOARDING_SEEN ||
                        it.key == Key.WHATS_NEW_VERSION ||
                        it.key == Key.ROUTING_PRESET ||
                        it.key == Key.ROUTING_PRESET_OWNERSHIP
                }
                .map { it.toBase64Str() }
        } else null

        return BackupJsonCodec.encode(
            BackupJsonDocument(
                version = BackupFormatVersion.CurrentV2,
                profiles = profiles,
                groups = groups,
                rules = rules,
                settings = settings,
            ),
        )
    }

    private fun decodeBackupPayload(
        document: BackupJsonDocument,
        pending: BackupImportSelection,
    ): DecodedBackupPayload {
        val profiles = if (pending.configurations) {
            document.profiles?.map {
                parcelize(it) { parcel -> ProxyEntity.CREATOR.createFromParcel(parcel) }
            }
        } else null
        val groups = if (pending.configurations) {
            document.groups?.map {
                parcelize(it) { parcel -> ProxyGroup.CREATOR.createFromParcel(parcel) }
            }
        } else null
        profiles?.let { BackupRestoreValidation.validateUniquePrimaryIds("profile", it.map { row -> row.id }) }
        groups?.let { BackupRestoreValidation.validateUniquePrimaryIds("group", it.map { row -> row.id }) }
        if (profiles != null) {
            val availableGroupIds = if (document.groups != null) {
                groups.orEmpty().mapTo(mutableSetOf()) { it.id }
            } else {
                SagerDatabase.groupDao.allGroups().mapTo(mutableSetOf()) { it.id }
            }
            BackupRestoreValidation.validateProfileGroupReferences(
                profileGroupIds = profiles.map { it.groupId },
                availableGroupIds = availableGroupIds,
            )
        }

        val rules = if (pending.rules) {
            document.rules?.map {
                parcelize(it) { parcel -> ParcelizeBridge.createRule(parcel) }
            }
        } else null
        rules?.let { BackupRestoreValidation.validateUniquePrimaryIds("rule", it.map { row -> row.id }) }
        var settings = if (pending.settings && document.settings != null) {
            normalizedImportedSettings(document)
        } else null
        val importedSelectedProxyId = settings
            ?.firstOrNull { it.key == Key.PROFILE_ID }
            ?.long
        val importedCurrentProfileId = settings
            ?.firstOrNull { it.key == Key.PROFILE_CURRENT }
            ?.long
        val availableProfileIds = if (profiles != null) {
            profiles.mapTo(mutableSetOf()) { it.id }
        } else if (settings != null) {
            SagerDatabase.proxyDao.getAll().mapTo(mutableSetOf()) { it.id }
        } else {
            emptySet()
        }
        val selectedProxyAfterRestore = BackupRestoreValidation.profilePointerAfterRestore(
            profilesReplaced = profiles != null,
            settingsRestored = settings != null,
            importedProfileId = importedSelectedProxyId,
            availableProfileIds = availableProfileIds,
        )
        val currentProfileAfterRestore = BackupRestoreValidation.profilePointerAfterRestore(
            profilesReplaced = profiles != null,
            settingsRestored = settings != null,
            importedProfileId = importedCurrentProfileId,
            availableProfileIds = availableProfileIds,
        )
        if (settings != null) {
            settings = settingsWithProfilePointers(
                settings,
                selectedProxyAfterRestore ?: 0L,
                currentProfileAfterRestore ?: 0L,
            )
        }

        return DecodedBackupPayload(
            profiles = profiles,
            groups = groups,
            rules = rules,
            settings = settings,
            importedSelectedProxyId = importedSelectedProxyId,
            importedCurrentProfileId = importedCurrentProfileId,
        )
    }

    private fun finishImport(payload: DecodedBackupPayload): List<KeyValuePair>? {
        if (payload.profiles != null || payload.groups != null) {
            // Keep lock order SagerDatabase -> PublicDatabase and clear profile pointers before replacement.
            SagerDatabase.instance.runInTransaction<Int> {
                val availableGroupIds = payload.groups?.mapTo(mutableSetOf()) { it.id }
                    ?: SagerDatabase.groupDao.allGroups().mapTo(mutableSetOf()) { it.id }
                payload.profiles?.let { profiles ->
                    BackupRestoreValidation.validateProfileGroupReferences(
                        profileGroupIds = profiles.map { it.groupId },
                        availableGroupIds = availableGroupIds,
                    )
                }
                PublicDatabase.instance.runInTransaction<Int> {
                    if (payload.profiles != null) {
                        DataStore.selectedProxy = 0L
                        DataStore.currentProfile = 0L
                    }
                    payload.profiles?.let { profiles ->
                        SagerDatabase.proxyDao.reset()
                        SagerDatabase.proxyDao.insert(profiles)
                    }
                    payload.groups?.let { groups ->
                        SagerDatabase.groupDao.reset()
                        SagerDatabase.groupDao.insert(groups)
                    }
                    1
                }
            }
        }
        if (payload.rules != null) {
            SagerDatabase.instance.runInTransaction {
                SagerDatabase.rulesDao.reset()
                SagerDatabase.rulesDao.insert(payload.rules)
            }
        }

        var importedSettings: List<KeyValuePair>? = null
        if (payload.settings != null) {
            val onboardingSeen = DataStore.onboardingSeen
            val whatsNewVersion = DataStore.whatsNewVersion
            SagerDatabase.instance.runInTransaction<Int> {
                val availableProfileIds = SagerDatabase.proxyDao.getAll().mapTo(mutableSetOf()) { it.id }
                val selectedProxyAfterRestore = BackupRestoreValidation.profilePointerAfterRestore(
                    profilesReplaced = false,
                    settingsRestored = true,
                    importedProfileId = payload.importedSelectedProxyId,
                    availableProfileIds = availableProfileIds,
                ) ?: 0L
                val currentProfileAfterRestore = BackupRestoreValidation.profilePointerAfterRestore(
                    profilesReplaced = false,
                    settingsRestored = true,
                    importedProfileId = payload.importedCurrentProfileId,
                    availableProfileIds = availableProfileIds,
                ) ?: 0L
                val settingsToWrite = settingsWithProfilePointers(
                    payload.settings,
                    selectedProxyAfterRestore,
                    currentProfileAfterRestore,
                )
                importedSettings = settingsToWrite
                PublicDatabase.instance.runInTransaction<Int> {
                    PublicDatabase.kvPairDao.reset()
                    PublicDatabase.kvPairDao.insert(settingsToWrite)
                    PublicDatabase.kvPairDao.put(KeyValuePair(Key.ONBOARDING_SEEN).put(onboardingSeen))
                    PublicDatabase.kvPairDao.put(KeyValuePair(Key.WHATS_NEW_VERSION).put(whatsNewVersion))
                    1
                }
            }
            RoutingPresetManager.clearOwnership()
        }
        return importedSettings
    }

    private fun capturePreImage(
        document: BackupJsonDocument,
        configurations: Boolean,
        rules: Boolean,
        settings: Boolean,
        profilePointersWillBeReset: Boolean,
    ): LogicalRestorePreImage? {
        val profiles = if (configurations && document.profiles != null) {
            SagerDatabase.proxyDao.getAll().map { it.toBase64Str() }
        } else null
        val groups = if (configurations && document.groups != null) {
            SagerDatabase.groupDao.allGroups().map { it.toBase64Str() }
        } else null
        val ruleRows = if (rules && document.rules != null) {
            SagerDatabase.rulesDao.allRules().map { it.toBase64Str() }
        } else null
        val settingRows = if ((settings && document.settings != null) || profilePointersWillBeReset) {
            PublicDatabase.kvPairDao.all().map { it.toBase64Str() }
        } else null

        return if (profiles == null && groups == null && ruleRows == null && settingRows == null) {
            null
        } else {
            LogicalRestorePreImage(
                profiles = profiles,
                groups = groups,
                rules = ruleRows,
                settings = settingRows,
            )
        }
    }

    private fun verifyImport(
        document: BackupJsonDocument,
        configurations: Boolean,
        rules: Boolean,
        settings: Boolean,
        importedSettings: List<KeyValuePair>?,
        expectedSelectedProxy: Long?,
        expectedCurrentProfile: Long?,
        protectedSettings: ProtectedSettingsSnapshot,
    ) {
        fun verifySection(name: String, expected: List<String>?, actual: List<String>) {
            if (expected == null) return
            check(expected.groupingBy { it }.eachCount() == actual.groupingBy { it }.eachCount()) {
                "Restored $name rows do not match the validated backup payload"
            }
        }

        if (configurations) {
            verifySection(
                "profiles",
                document.profiles,
                SagerDatabase.proxyDao.getAll().map { it.toBase64Str() },
            )
            verifySection(
                "groups",
                document.groups,
                SagerDatabase.groupDao.allGroups().map { it.toBase64Str() },
            )
        }
        if (rules) {
            verifySection(
                "rules",
                document.rules,
                SagerDatabase.rulesDao.allRules().map { it.toBase64Str() },
            )
        }
        if (settings && importedSettings != null) {
            val expectedSettings = importedSettings.map { it.toBase64Str() }
            val actualSettings = PublicDatabase.kvPairDao.all()
                .filterNot {
                    it.key == Key.ONBOARDING_SEEN ||
                        it.key == Key.WHATS_NEW_VERSION ||
                        it.key == Key.ROUTING_PRESET ||
                        it.key == Key.ROUTING_PRESET_OWNERSHIP
                }
                .map { it.toBase64Str() }
            verifySection("settings", expectedSettings, actualSettings)
            check(DataStore.onboardingSeen == protectedSettings.onboardingSeen) {
                "Restore changed the protected onboarding marker"
            }
            check(DataStore.whatsNewVersion == protectedSettings.whatsNewVersion) {
                "Restore changed the protected What's New marker"
            }
            check(DataStore.routingPreset == "custom" && DataStore.routingPresetOwnership.isBlank()) {
                "Restore did not clear routing preset ownership"
            }
        }
        expectedSelectedProxy?.let { expected ->
            check(DataStore.selectedProxy == expected) {
                "Restored selected profile does not match the validated backup state"
            }
        }
        expectedCurrentProfile?.let { expected ->
            check(DataStore.currentProfile == expected) {
                "Restored current profile does not match the validated backup state"
            }
        }
    }

    /**
     * V1 backups predate the Compose theme-mode keys and only contain the
     * legacy palette id. Treat that palette as an explicit Classic selection so
     * Android 12+ dynamic-color defaults cannot hide the restored theme.
     */
    private fun normalizedImportedSettings(document: BackupJsonDocument): List<KeyValuePair> {
        val settings = document.settings.orEmpty().map {
            parcelize(it) { parcel -> KeyValuePair.CREATOR.createFromParcel(parcel) }
        }
        BackupRestoreValidation.validateUniqueSettingKeys(settings.map { it.key })
        if (!LegacyBackupThemePolicy.shouldRestoreClassic(document.version, settings.map { it.key }.toSet())) {
            return settings
        }
        return settings.filterNot { it.key == Key.COMPOSE_DYNAMIC_COLORS } + listOf(
            KeyValuePair(Key.THEME_MODE).put(Key.THEME_MODE_CLASSIC.toLong()),
            KeyValuePair(Key.COMPOSE_DYNAMIC_COLORS).put(false),
        )
    }

    private fun settingsWithProfilePointers(
        settings: List<KeyValuePair>,
        selectedProxyId: Long,
        currentProfileId: Long,
    ): List<KeyValuePair> = settings.filterNot {
        it.key == Key.PROFILE_ID || it.key == Key.PROFILE_CURRENT
    } + listOf(
        KeyValuePair(Key.PROFILE_ID).put(selectedProxyId),
        KeyValuePair(Key.PROFILE_CURRENT).put(currentProfileId),
    )

    private fun Parcelable.toBase64Str(): String {
        val parcel = Parcel.obtain()
        return try {
            writeToParcel(parcel, 0)
            Util.b64EncodeUrlSafe(parcel.marshall())
        } finally {
            parcel.recycle()
        }
    }

    private fun <T> parcelize(encoded: String, creator: (Parcel) -> T): T {
        val data = Util.b64Decode(encoded)
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(data, 0, data.size)
            parcel.setDataPosition(0)
            creator(parcel)
        } finally {
            parcel.recycle()
        }
    }

    private fun fail(throwable: Throwable) {
        _uiState.update {
            it.copy(busy = false, error = throwable.readableMessage)
        }
    }

    private companion object {
        const val SERVICE_QUIESCENCE_TIMEOUT_MILLIS = 15_000L
        const val MAX_BACKUP_CHARS = 16 * 1024 * 1024
    }
}
