package io.github.hhwkart.nami.ui.compose.utility

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.hhwkart.nami.BuildConfig
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.plugin.PluginManager.loadString
import io.github.hhwkart.nami.utils.PackageCache
import io.github.hhwkart.nami.ui.compose.common.NamiAutoFocusTextField
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import io.github.hhwkart.nami.ui.ProjectLinks
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import libcore.Libcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.hhwkart.nami.core.plugin.Plugins
import io.github.hhwkart.nami.core.utils.SendLog

/** Compose replacement for the legacy log viewer. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var logText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            error = null
            runCatching {
                withContext(Dispatchers.IO) {
                    String(SendLog.getNekoLog(50L * 1024L))
                }
            }.onSuccess { logText = it }
                .onFailure { error = it.message ?: it.javaClass.simpleName }
            loading = false
        }
    }

    onBack?.let { BackHandler(onBack = it) }

    LaunchedEffect(Unit) { reload() }

    val scrollState = rememberScrollState()
    LaunchedEffect(logText) {
        if (logText.isNotEmpty()) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Log") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = ::reload) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Refresh log")
                    }
                    IconButton(onClick = {
                        runCatching {
                            Libcore.nekoLogClear()
                            Runtime.getRuntime().exec(arrayOf("/system/bin/logcat", "-c"))
                        }.onSuccess { logText = "" }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear log")
                    }
                    IconButton(onClick = { SendLog.sendLog(context, "Nami") }) {
                        Icon(Icons.Outlined.Send, contentDescription = "Share log")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                ),
        ) {
            if (loading) Text("Loading log…", style = MaterialTheme.typography.bodyMedium)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(bottom = padding.calculateBottomPadding() + 16.dp),
            ) {
                Text(
                    text = logText.ifBlank { "No logs recorded" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

private data class PluginSummary(
    val id: String,
    val packageName: String,
    val version: String,
    val provider: String,
)

/** Compose replacement for About, license, update links, and installed plugins. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenOnboarding: (() -> Unit)? = null,
    onOpenWhatsNew: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var license by remember { mutableStateOf<String?>(null) }
    var plugins by remember { mutableStateOf<List<PluginSummary>>(emptyList()) }

    LaunchedEffect(Unit) {
        val result = withContext(Dispatchers.IO) {
            val licenseText = runCatching {
                context.assets.open("LICENSE").bufferedReader().use { it.readText() }
            }.getOrNull()
            val installed = runCatching {
                PackageCache.awaitLoadSync()
                PackageCache.installedPluginPackages.values.mapNotNull { packageInfo ->
                    val provider = packageInfo.providers?.firstOrNull() ?: return@mapNotNull null
                    val id = runCatching {
                        provider.loadString(Plugins.METADATA_KEY_ID)
                    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    PluginSummary(
                        id = id,
                        packageName = packageInfo.packageName,
                        version = packageInfo.versionName ?: "unknown",
                        provider = Plugins.displayExeProvider(packageInfo.packageName),
                    )
                }.sortedBy { it.id }
            }.getOrDefault(emptyList())
            licenseText to installed
        }
        license = result.first
        plugins = result.second
    }

    fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
    }

    onBack?.let { BackHandler(onBack = it) }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.app_name_long), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.app_desc))
                Text("Version ${SagerNet.appVersionNameForDisplay.ifBlank { BuildConfig.VERSION_NAME }}")
                Text("sing-box ${runCatching { Libcore.versionBox() }.getOrDefault("unknown")}")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { openUrl(ProjectLinks.REPOSITORY) }) {
                        Text("Project")
                    }
                    OutlinedButton(onClick = { openUrl(ProjectLinks.README) }) {
                        Text("README")
                    }
                }
            }
            if (onOpenOnboarding != null || onOpenWhatsNew != null) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        onOpenOnboarding?.let { action ->
                            OutlinedButton(onClick = action) { Text("Review onboarding") }
                        }
                        onOpenWhatsNew?.let { action ->
                            OutlinedButton(onClick = action) { Text("What’s new") }
                        }
                    }
                }
            }
            item {
                Text("Installed plugins", style = MaterialTheme.typography.titleMedium)
            }
            if (plugins.isEmpty()) {
                item { Text("No external plugins detected") }
            } else {
                items(plugins, key = { it.packageName }) { plugin ->
                    NamiCard(onClick = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${plugin.packageName}"),
                        )
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                    }) {
                        Column(Modifier.padding(16.dp)) {
                            Text(plugin.id, style = MaterialTheme.typography.titleSmall)
                            Text("${plugin.packageName} · v${plugin.version} · ${plugin.provider}")
                        }
                    }
                }
            }
            item {
                Text("License", style = MaterialTheme.typography.titleMedium)
                Text(
                    license ?: "Loading license…",
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/** Compose entry point for the legacy Tools tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onStun: () -> Unit,
    onBackup: () -> Unit,
    onRouteAssets: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    onBack?.let { BackHandler(onBack = it) }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Tools") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NamiCard(
                onClick = onStun,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("STUN test", style = MaterialTheme.typography.titleMedium)
                        Text("Determine client's NAT mapping and filtering behaviour", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            NamiCard(
                onClick = onBackup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Backup and restore", style = MaterialTheme.typography.titleMedium)
                        Text("Export or import configurations, rules, and settings", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            NamiCard(
                onClick = onRouteAssets,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f)) {
                        Text("Route assets", style = MaterialTheme.typography.titleMedium)
                        Text("Manage geoip and geosite rule files", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/** Compose replacement for the Clash/YACD WebView dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf(DataStore.yacdURL) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var draftUrl by remember { mutableStateOf(url) }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                onPause()
                stopLoading()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }

    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onBack?.invoke()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Dashboard") },
                navigationIcon = {
                    onBack?.let {
                        IconButton(onClick = it) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    TextButton(onClick = {
                        draftUrl = url
                        showUrlDialog = true
                    }) { Text("URL") }
                },
            )
        },
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = {
                WebView(it).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl(url)
                    webView = this
                }
            },
            update = { view ->
                webView = view
                if (view.url != url) view.loadUrl(url)
            },
        )
    }

    if (showUrlDialog) {
        NamiAlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = { Text("Dashboard URL") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    NamiAutoFocusTextField(
                        value = draftUrl,
                        onValueChange = { draftUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val candidate = draftUrl.trim()
                    if (candidate.isNotBlank()) {
                        DataStore.yacdURL = candidate
                        url = candidate
                    }
                    showUrlDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            },
        )
    }
}
