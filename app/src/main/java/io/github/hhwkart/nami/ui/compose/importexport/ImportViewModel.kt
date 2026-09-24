package io.github.hhwkart.nami.ui.compose.importexport

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.fmt.AbstractBean
import io.github.hhwkart.nami.group.RawUpdater
import io.github.hhwkart.nami.ktx.SubscriptionFoundException
import io.github.hhwkart.nami.ktx.readableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import javax.inject.Inject

data class ImportUiState(
    val busy: Boolean = false,
    val importedCount: Int = 0,
    val message: String? = null,
    val error: String? = null,
)

@HiltViewModel
class ImportViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    private val _subscriptionUris = MutableSharedFlow<Uri>(extraBufferCapacity = 4)
    val subscriptionUris: SharedFlow<Uri> = _subscriptionUris.asSharedFlow()

    fun importClipboard() {
        importText(SagerNet.getClipboardText(), "clipboard")
    }

    fun importFile(contentResolver: ContentResolver, uri: Uri) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                val fileName = displayName(contentResolver, uri)
                val proxies = withContext(Dispatchers.IO) {
                    val input = contentResolver.openInputStream(uri)
                        ?: error("Unable to open imported file")
                    input.use { stream ->
                        if (fileName.endsWith(".zip", ignoreCase = true)) {
                            parseZip(stream)
                        } else {
                            parseRawFile(stream, fileName)
                        }
                    }
                }
                importProfiles(proxies)
            }.onFailure { throwable -> handleFailure(throwable) }
            setBusy(false)
        }
    }

    private fun importText(text: String, sourceName: String) {
        viewModelScope.launch {
            setBusy(true)
            runCatching {
                val proxies = withContext(Dispatchers.IO) {
                    val boundedText = BoundedImportReader.validateRawText(text)
                    RawUpdater.parseRaw(boundedText, sourceName).orEmpty().also {
                        BoundedImportReader.validateProfileCount(it.size)
                    }
                }
                importProfiles(proxies)
            }.onFailure { throwable -> handleFailure(throwable) }
            setBusy(false)
        }
    }

    private suspend fun parseZip(input: java.io.InputStream): List<AbstractBean> {
        val profiles = mutableListOf<AbstractBean>()
        for (entry in BoundedImportReader.readZipEntries(input)) {
            val parsed = RawUpdater.parseRaw(entry.text, entry.name).orEmpty()
            BoundedImportReader.validateProfileCount(profiles.size + parsed.size)
            profiles.addAll(parsed)
        }
        return profiles
    }

    private suspend fun parseRawFile(input: InputStream, fileName: String): List<AbstractBean> {
        val text = BoundedImportReader.readRaw(input)
        val profiles = RawUpdater.parseRaw(text, fileName).orEmpty()
        BoundedImportReader.validateProfileCount(profiles.size)
        return profiles
    }

    private suspend fun importProfiles(proxies: List<AbstractBean>) {
        if (proxies.isEmpty()) error("No profiles found in import")
        val targetGroup = DataStore.selectedGroupForImport()
        for (proxy in proxies) ProfileManager.createProfile(targetGroup, proxy)
        _uiState.update {
            it.copy(
                importedCount = proxies.size,
                message = "Imported ${proxies.size} profile(s)",
                error = null,
            )
        }
        DataStore.editingGroup = targetGroup
    }

    private fun displayName(contentResolver: ContentResolver, uri: Uri): String {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }?.takeIf { it.isNotBlank() }
            ?: uri.path.orEmpty().substringAfterLast('/').substringAfter(':')
    }

    private fun handleFailure(throwable: Throwable) {
        if (throwable is SubscriptionFoundException) {
            _subscriptionUris.tryEmit(Uri.parse(throwable.link))
            _uiState.update { it.copy(error = null) }
        } else {
            _uiState.update { it.copy(error = throwable.readableMessage) }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun setBusy(value: Boolean) {
        _uiState.update { it.copy(busy = value) }
    }
}
