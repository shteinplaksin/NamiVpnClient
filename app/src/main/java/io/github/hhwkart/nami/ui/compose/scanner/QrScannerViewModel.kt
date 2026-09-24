package io.github.hhwkart.nami.ui.compose.scanner

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.king.zxing.util.CodeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.group.RawUpdater
import io.github.hhwkart.nami.ktx.SubscriptionFoundException
import io.github.hhwkart.nami.ktx.readableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class QrScannerUiState(
    val permissionKnown: Boolean = false,
    val cameraPermissionGranted: Boolean = false,
    val permissionRequested: Boolean = false,
    val torchEnabled: Boolean = false,
    val busy: Boolean = false,
    val importedCount: Int = 0,
    val message: String? = null,
    val error: String? = null,
    val pendingSubscriptionUri: String? = null,
    val finished: Boolean = false,
)

@HiltViewModel
class QrScannerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private companion object {
        const val PERMISSION_REQUESTED_KEY = "qrScanner.permissionRequested"
        const val SUBSCRIPTION_URI_KEY = "qrScanner.subscriptionUri"
    }

    private val acceptingCameraResult = AtomicBoolean(true)
    private val _uiState = MutableStateFlow(
        QrScannerUiState(
            permissionRequested = savedStateHandle[PERMISSION_REQUESTED_KEY] ?: false,
            pendingSubscriptionUri = savedStateHandle[SUBSCRIPTION_URI_KEY],
        ),
    )
    val uiState: StateFlow<QrScannerUiState> = _uiState.asStateFlow()

    fun markPermissionRequested() {
        savedStateHandle[PERMISSION_REQUESTED_KEY] = true
        _uiState.update { it.copy(permissionRequested = true) }
    }

    fun setCameraPermission(granted: Boolean) {
        _uiState.update {
            it.copy(permissionKnown = true, cameraPermissionGranted = granted)
        }
    }

    fun setTorchEnabled(enabled: Boolean) {
        _uiState.update { it.copy(torchEnabled = enabled) }
    }

    fun onCameraResult(text: String?) {
        if (!acceptingCameraResult.compareAndSet(true, false)) return
        importPayload(text)
    }

    fun importImages(contentResolver: ContentResolver, uris: List<Uri>) {
        if (uris.isEmpty() || !_uiState.compareAndSet(_uiState.value, _uiState.value.copy(busy = true))) {
            return
        }
        viewModelScope.launch {
            try {
                val imported = mutableListOf<io.github.hhwkart.nami.fmt.AbstractBean>()
                var subscriptionUri: Uri? = null
                val failures = mutableListOf<String>()
                withContext(Dispatchers.IO) {
                    for (uri in uris) {
                        try {
                            val text = decodeQrText(contentResolver, uri)
                                ?: error("QR code not found")
                            try {
                                imported += RawUpdater.parseRaw(text).orEmpty()
                            } catch (e: SubscriptionFoundException) {
                                subscriptionUri = subscriptionUri ?: Uri.parse(e.link)
                            }
                        } catch (e: Throwable) {
                            failures += e.readableMessage
                        }
                    }
                }
                if (imported.isNotEmpty()) {
                    importProfiles(imported)
                } else if (subscriptionUri == null) {
                    error(failures.firstOrNull() ?: "No profiles found in QR images")
                }
                subscriptionUri?.let(::publishSubscriptionUri)
                if (imported.isNotEmpty() || subscriptionUri != null) {
                    _uiState.update {
                        it.copy(
                            busy = false,
                            finished = true,
                            error = if (failures.isEmpty()) null
                            else "Some images could not be imported: ${failures.first()}",
                        )
                    }
                }
            } catch (throwable: Throwable) {
                _uiState.update {
                    it.copy(busy = false, error = throwable.readableMessage)
                }
            }
        }
    }

    fun consumeSubscriptionUri() {
        savedStateHandle.remove<String>(SUBSCRIPTION_URI_KEY)
        _uiState.update { it.copy(pendingSubscriptionUri = null) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    private fun importPayload(text: String?) {
        _uiState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val value = text?.takeIf { it.isNotBlank() } ?: error("QR code not found")
                try {
                    val profiles = withContext(Dispatchers.IO) { RawUpdater.parseRaw(value).orEmpty() }
                    if (profiles.isEmpty()) error("No profiles found in QR code")
                    importProfiles(profiles)
                } catch (e: SubscriptionFoundException) {
                    publishSubscriptionUri(Uri.parse(e.link))
                    _uiState.update { it.copy(finished = true) }
                }
            }.onSuccess {
                _uiState.update { it.copy(busy = false, finished = true) }
            }.onFailure { throwable ->
                acceptingCameraResult.set(true)
                _uiState.update {
                    it.copy(busy = false, error = throwable.readableMessage)
                }
            }
        }
    }

    private suspend fun importProfiles(
        profiles: List<io.github.hhwkart.nami.fmt.AbstractBean>,
    ) = withContext(Dispatchers.IO) {
        val targetGroup = DataStore.selectedGroupForImport()
        if (DataStore.selectedGroup != targetGroup) DataStore.selectedGroup = targetGroup
        profiles.forEach { ProfileManager.createProfile(targetGroup, it) }
        DataStore.editingGroup = targetGroup
        _uiState.update {
            it.copy(
                importedCount = it.importedCount + profiles.size,
                message = "Imported ${profiles.size} profile(s)",
            )
        }
    }

    private fun publishSubscriptionUri(uri: Uri) {
        savedStateHandle[SUBSCRIPTION_URI_KEY] = uri.toString()
        _uiState.update { it.copy(pendingSubscriptionUri = uri.toString()) }
    }

    private fun decodeQrText(contentResolver: ContentResolver, uri: Uri): String? {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, uri)
        }
        return try {
            CodeUtils.parseCodeResult(bitmap)?.text
        } finally {
            bitmap.recycle()
        }
    }
}
