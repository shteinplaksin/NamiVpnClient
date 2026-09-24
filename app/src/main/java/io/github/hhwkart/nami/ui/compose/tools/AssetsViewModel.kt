package io.github.hhwkart.nami.ui.compose.tools

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.ktx.applyLocalProxy
import io.github.hhwkart.nami.ktx.getStr
import io.github.hhwkart.nami.ktx.readableMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import libcore.Libcore
import io.github.hhwkart.nami.core.utils.Util
import org.json.JSONObject
import java.io.File
import java.util.Date
import java.text.DateFormat
import javax.inject.Inject

data class AssetItem(
    val file: File,
    val name: String,
    val status: String,
    val canUpdate: Boolean,
    val updating: Boolean = false,
)

data class AssetsState(
    val initialized: Boolean = false,
    val refreshing: Boolean = false,
    val assets: List<AssetItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
)

@HiltViewModel
class AssetsViewModel @Inject constructor() : ViewModel() {
    companion object {
        private val builtInNames = setOf("geoip.db", "geosite.db")
    }

    private val _uiState = MutableStateFlow(AssetsState())
    val uiState: StateFlow<AssetsState> = _uiState.asStateFlow()
    private var root: File? = null

    private val rulesProviders = listOf(
        mapOf("geoip.db" to "SagerNet/sing-geoip", "geosite.db" to "SagerNet/sing-geosite"),
        mapOf("geoip.db" to "soffchen/sing-geoip", "geosite.db" to "soffchen/sing-geosite"),
        mapOf("geoip.db" to "Chocolate4U/Iran-sing-box-rules"),
        mapOf("geoip.db" to "L11R/antizapret-sing-box-geo"),
    )

    fun initialize(directory: File) {
        if (root?.canonicalPath == directory.canonicalPath && _uiState.value.initialized) return
        root = directory
        reload()
    }

    fun reload() {
        val directory = root ?: return
        _uiState.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            directory.mkdirs()
            val custom = directory.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".db") && it.name !in builtInNames }
                .orEmpty()
            val files = listOf(File(directory, "geoip.db"), File(directory, "geosite.db")) + custom
            val items = files.map { file -> item(file) }
            _uiState.update { it.copy(initialized = true, refreshing = false, assets = items) }
        }
    }

    fun importAsset(resolver: ContentResolver, uri: Uri) {
        val directory = root ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val fileName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }?.takeIf { it.isNotBlank() } ?: uri.path.orEmpty().substringAfterLast('/').substringAfter(':')
                require(fileName.endsWith(".db")) { "Unsupported asset: $fileName" }
                val outFile = File(directory, fileName)
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Unable to open asset" }
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                File(directory, fileName.substringBeforeLast('.') + ".version.txt").writeText("Custom")
            }.onSuccess {
                reload()
                _uiState.update { it.copy(message = "Asset imported") }
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.readableMessage) }
            }
        }
    }

    fun deleteAsset(file: File) {
        if (file.name in builtInNames) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                file.deleteRecursively()
                File(file.parentFile, file.nameWithoutExtension + ".version.txt").delete()
            }.onSuccess { reload() }.onFailure { error ->
                _uiState.update { it.copy(error = error.readableMessage) }
            }
        }
    }

    fun updateAsset(asset: AssetItem) {
        val directory = root ?: return
        if (!asset.canUpdate || asset.updating) return
        _uiState.update { state -> state.copy(assets = state.assets.map { if (it.file == asset.file) it.copy(updating = true) else it }) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val versionFile = File(asset.file.parentFile, "${asset.file.nameWithoutExtension}.version.txt")
                updateAssetFile(asset.file, versionFile, asset.status)
            }.onSuccess {
                reload()
                _uiState.update { it.copy(message = "Asset updated") }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(assets = state.assets.map { if (it.file == asset.file) it.copy(updating = false) else it }, error = error.readableMessage) }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null, error = null) }

    private fun item(file: File): AssetItem {
        val versionFile = File(file.parentFile, "${file.nameWithoutExtension}.version.txt")
        val status = if (file.isFile) {
            if (versionFile.isFile) runCatching { versionFile.readText().trim() }.getOrDefault("<unknown>")
            else "Unknown-${DateFormat.getDateInstance().format(Date(file.lastModified()))}"
        } else "<unknown>"
        return AssetItem(file, file.name, status, file.name in builtInNames)
    }

    private suspend fun updateAssetFile(file: File, versionFile: File, localVersion: String) {
        val repo = rulesProviders.getOrNull(DataStore.rulesProvider)?.get(file.name)
            ?: error("No rule provider for ${file.name}")
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            applyLocalProxy()
        }
        try {
            var response = client.newRequest().apply {
                setURL("https://api.github.com/repos/$repo/releases/latest")
            }.execute()
            val release = JSONObject(Util.getStringBox(response.contentString))
            val tagName = release.optString("tag_name")
            if (tagName == localVersion) return
            val releaseAssets = release.getJSONArray("assets")
            var remote: JSONObject? = null
            for (index in 0 until releaseAssets.length()) {
                val candidate = releaseAssets.optJSONObject(index) ?: continue
                if (candidate.getStr("name") == file.name) {
                    remote = candidate
                    break
                }
            }
            remote ?: error("File ${file.name} not found in release ${release["url"]}")
            response = client.newRequest().apply {
                setURL(remote.getStr("browser_download_url"))
            }.execute()
            val cacheFile = File(file.parentFile, file.name + ".tmp")
            response.writeTo(cacheFile.canonicalPath)
            if (file.name.endsWith(".xz")) {
                Libcore.unxz(cacheFile.absolutePath, file.absolutePath)
                cacheFile.delete()
            } else {
                cacheFile.renameTo(file)
            }
            versionFile.writeText(tagName)
        } finally {
            client.close()
        }
    }
}
