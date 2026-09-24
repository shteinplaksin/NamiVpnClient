package io.github.hhwkart.nami.ui.compose.perapp

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.hhwkart.nami.BuildConfig
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class ProxiedAppUi(
    val packageName: String,
    val uid: Int,
    val name: String,
    val isSystem: Boolean,
    val icon: ImageBitmap?,
)

data class PerAppUiState(
    val loading: Boolean = true,
    val modeEnabled: Boolean = DataStore.proxyApps,
    val bypass: Boolean = DataStore.bypass,
    val query: String = "",
    val showSystem: Boolean = true,
    val apps: List<ProxiedAppUi> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val selectedCount: Int = 0,
)

@HiltViewModel
class PerAppViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(PerAppUiState())
    val uiState: StateFlow<PerAppUiState> = _uiState.asStateFlow()

    /** packageName -> uid map for selected apps */
    private val selectedUids = LinkedHashMap<String, Int>()
    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private val pendingIconLoads = ConcurrentHashMap.newKeySet<String>()
    val iconUpdateTrigger = MutableSharedFlow<String>(extraBufferCapacity = 64)

    init {
        reload()
    }

    private fun persistSelection() {
        DataStore.individual = selectedUids.keys.joinToString("\n")
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            PackageCache.reload()
            val pm = io.github.hhwkart.nami.ktx.app.packageManager
            // rebuild selected set from DataStore
            selectedUids.clear()
            val cached = PackageCache.installedPackages.toMutableMap().apply {
                remove(BuildConfig.APPLICATION_ID)
            }
            for (line in DataStore.individual.lineSequence()) {
                val info = cached[line]?.applicationInfo ?: continue
                selectedUids[line] = info.uid
            }
            val apps = cached.mapNotNull { (packageName, packageInfo) ->
                val info = packageInfo.applicationInfo ?: return@mapNotNull null
                ProxiedAppUi(
                    packageName = packageName,
                    uid = info.uid,
                    name = info.loadLabel(pm).toString(),
                    isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    icon = iconCache[packageName],
                )
            }.sortedWith(compareBy({ it.packageName !in selectedUids }, { it.name }))
            val selectedSet = selectedUids.keys.toSet()
            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        apps = apps,
                        selectedPackages = selectedSet,
                        selectedCount = selectedSet.size,
                    )
                }
            }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun setShowSystem(show: Boolean) {
        _uiState.update { it.copy(showSystem = show) }
    }

    fun setBypass(bypass: Boolean) {
        DataStore.bypass = bypass
        DataStore.proxyApps = true
        _uiState.update { it.copy(bypass = bypass, modeEnabled = true) }
    }

    fun setModeDisabled() {
        DataStore.proxyApps = false
        _uiState.update { it.copy(modeEnabled = false) }
    }

    fun toggle(app: ProxiedAppUi) {
        if (app.packageName in selectedUids) selectedUids.remove(app.packageName)
        else selectedUids[app.packageName] = app.uid
        persistSelection()
        val set = selectedUids.keys.toSet()
        _uiState.update { it.copy(selectedPackages = set, selectedCount = set.size) }
    }

    fun isSelected(app: ProxiedAppUi) = app.packageName in selectedUids

    fun invert() {
        val apps = _uiState.value.apps
        for (app in apps) {
            if (app.packageName in selectedUids) selectedUids.remove(app.packageName)
            else selectedUids[app.packageName] = app.uid
        }
        persistSelection()
        val set = selectedUids.keys.toSet()
        _uiState.update { it.copy(selectedPackages = set, selectedCount = set.size) }
    }

    fun clearSelection() {
        selectedUids.clear()
        persistSelection()
        _uiState.update { it.copy(selectedPackages = emptySet(), selectedCount = 0) }
    }

    fun getCachedIcon(packageName: String): ImageBitmap? = iconCache[packageName]

    /** Asynchronously load and cache icon; survives row recomposition */
    fun loadIcon(app: ProxiedAppUi) {
        if (iconCache.containsKey(app.packageName) || !pendingIconLoads.add(app.packageName)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = loadDrawable(app)?.toBitmap()
                if (bitmap != null) {
                    iconCache[app.packageName] = bitmap
                    iconUpdateTrigger.tryEmit(app.packageName)
                }
            } catch (_: Exception) {
            } finally {
                pendingIconLoads.remove(app.packageName)
            }
        }
    }

    fun visibleApps(state: PerAppUiState): List<ProxiedAppUi> {
        val query = state.query.lowercase()
        return state.apps.filter { app ->
            if (!state.showSystem && app.isSystem) return@filter false
            if (query.isEmpty()) return@filter true
            app.name.lowercase().contains(query) ||
                    app.packageName.lowercase().contains(query) ||
                    app.uid.toString().contains(query)
        }
    }

    private fun Drawable.toBitmap(): ImageBitmap {
        val width = intrinsicWidth.coerceAtLeast(1)
        val height = intrinsicHeight.coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, width, height)
        draw(canvas)
        return bitmap.asImageBitmap()
    }

    suspend fun loadIconSafe(app: ProxiedAppUi): ImageBitmap? {
        val drawable = loadDrawable(app) ?: return null
        return drawable.toBitmap()
    }

    private suspend fun loadDrawable(app: ProxiedAppUi): Drawable? = withContext(Dispatchers.IO) {
        try {
            PackageCache.installedPackages[app.packageName]?.applicationInfo?.loadIcon(
                io.github.hhwkart.nami.ktx.app.packageManager
            )
        } catch (_: Exception) {
            null
        }
    }
}
