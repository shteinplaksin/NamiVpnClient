package io.github.hhwkart.nami.ui.compose.perapp

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.flow.first
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.ui.compose.common.EmptyState
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import io.github.hhwkart.nami.ui.compose.style.NamiSwitch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerAppProxyScreen(
    onBack: (() -> Unit)? = null,
    viewModel: PerAppViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.reload()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.reload()
    }

    onBack?.let { BackHandler(onBack = it) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.perapp_title)) },
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
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(64.dp))
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val layoutDirection = LocalLayoutDirection.current
        val safePadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
                .consumeWindowInsets(safePadding)
                .imePadding(),
        ) {
            NamiCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.proxied_apps),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (state.modeEnabled) stringResource(R.string.enabled) else stringResource(R.string.disabled),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (state.modeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    NamiSwitch(
                        checked = state.modeEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) viewModel.setBypass(state.bypass) else viewModel.setModeDisabled()
                        },
                    )
                }
            }

            if (state.modeEnabled) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !state.bypass,
                        onClick = { viewModel.setBypass(false) },
                        label = { Text(stringResource(R.string.perapp_mode_on)) },
                    )
                    FilterChip(
                        selected = state.bypass,
                        onClick = { viewModel.setBypass(true) },
                        label = { Text(stringResource(R.string.perapp_mode_bypass)) },
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = { Text(stringResource(R.string.profiles_search_hint)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (state.query.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                            }
                        }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.perapp_selected_count, state.selectedCount),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.perapp_show_system),
                    style = MaterialTheme.typography.bodyMedium,
                )
                NamiSwitch(checked = state.showSystem, onCheckedChange = viewModel::setShowSystem)
            }
            if (state.apps.isEmpty()) {
                val context = LocalContext.current
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(R.string.app_list_permission_denied),
                    body = "Allow app-list access to manage per-app proxy rules.",
                    primaryLabel = stringResource(R.string.grant_permission),
                    onPrimary = { permissionLauncher.launch("com.android.permission.GET_INSTALLED_APPS") },
                    secondaryLabel = stringResource(R.string.settings),
                    onSecondary = {
                        val intent = android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
            } else {
                val visible = remember(state) { viewModel.visibleApps(state) }
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        val isSelected = state.selectedPackages.contains(app.packageName)
                        AppRow(
                            app = app,
                            isSelected = isSelected,
                            onToggle = { viewModel.toggle(app) },
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: ProxiedAppUi,
    isSelected: Boolean,
    onToggle: () -> Unit,
    viewModel: PerAppViewModel,
) {
    val cachedIcon = viewModel.getCachedIcon(app.packageName)
    var iconBitmap by remember(app.packageName) { mutableStateOf(cachedIcon) }
    LaunchedEffect(app.packageName) {
        val cached = viewModel.getCachedIcon(app.packageName)
        if (cached != null) {
            iconBitmap = cached
        } else {
            viewModel.loadIcon(app)
            try {
                viewModel.iconUpdateTrigger.first { it == app.packageName }
                iconBitmap = viewModel.getCachedIcon(app.packageName)
            } catch (_: Exception) {
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val bitmap = iconBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        app.name.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(app.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${app.packageName} (${app.uid})",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
    }
}
