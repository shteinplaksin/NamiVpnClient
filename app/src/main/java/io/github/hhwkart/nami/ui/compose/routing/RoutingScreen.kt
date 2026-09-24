package io.github.hhwkart.nami.ui.compose.routing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import io.github.hhwkart.nami.ui.compose.theme.tvFocusable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.ui.compose.common.EmptyState
import io.github.hhwkart.nami.ui.compose.common.ErrorState
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import io.github.hhwkart.nami.ui.compose.style.NamiSwitch
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Composable
fun RoutingScreen(
    onAddRule: () -> Unit,
    onEditRule: (RuleUi) -> Unit,
    onAdvanced: () -> Unit,
    onReloadRequired: () -> Unit,
    viewModel: RoutingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var resetRequested by remember { mutableStateOf(false) }
    var presetDialog by remember { mutableStateOf<RoutingPreset?>(null) }
    val layoutDirection = LocalLayoutDirection.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val export = RoutingJsonTransfer.export(SagerDatabase.rulesDao.allRules())
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(export.content)
                    } ?: error("Unable to open the selected file")
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(
                            if (export.normalizedProfileOutboundCount == 0) {
                                "Routing configuration exported"
                            } else {
                                "Exported; ${export.normalizedProfileOutboundCount} profile outbounds were normalized to proxy"
                            },
                        )
                    }
                }.onFailure {
                    withContext(Dispatchers.Main) { snackbarHostState.showSnackbar(it.message ?: "Export failed") }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    val content = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("Unable to open the selected file")
                    RoutingJsonTransfer.import(content).getOrThrow()
                }.onSuccess { result ->
                    viewModel.reload()
                    withContext(Dispatchers.Main) {
                        snackbarHostState.showSnackbar(
                            "Imported ${result.added}; skipped ${result.skippedDuplicates}; " +
                                "normalized ${result.normalized}; rejected ${result.rejected}",
                        )
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        val rejected = (error as? RoutingJsonException)?.rejectedCount ?: 0
                        val rejectedText = if (rejected > 0) " Rejected $rejected rule(s)." else ""
                        snackbarHostState.showSnackbar(
                            "Import failed.$rejectedText ${error.message ?: "Invalid routing JSON."}",
                        )
                    }
                }
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + 4.dp,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        if (state.error != null) {
            item {
                ErrorState(
                    message = state.error!!,
                    onRetry = viewModel::reload,
                )
            }
        }
        item {
            Text("Routing presets", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.titleMedium)
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RoutingPreset.entries.forEach { preset ->
                    val available = preset != RoutingPreset.GFW_LIST || RoutingPresetManager.gfwAvailability.available
                    NamiCard(
                        onClick = {
                            if (available) {
                                presetDialog = preset
                            }
                        },
                        enabled = available,
                        tint = if (state.presetId == preset.id) {
                            MaterialTheme.colorScheme.secondaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.Place, contentDescription = null)
                            Column(Modifier.weight(1f)) {
                                Text(preset.title, style = MaterialTheme.typography.titleMedium)
                                Text(preset.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (!available) Text("Unavailable: bundled geosite data has no verified gfw tag", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
        item {
            WebsiteBypassSection(
                state = state,
                viewModel = viewModel,
                onReloadRequired = onReloadRequired,
            )
        }
        item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                FilterChip(
                    selected = state.filter == -1,
                    onClick = { viewModel.setFilter(-1) },
                    label = { Text(stringResource(R.string.routing_filter_all)) },
                )
                FilterChip(
                    selected = state.filter == 0,
                    onClick = { viewModel.setFilter(0) },
                    label = { Text(stringResource(R.string.routing_section_domain)) },
                )
                FilterChip(
                    selected = state.filter == 1,
                    onClick = { viewModel.setFilter(1) },
                    label = { Text(stringResource(R.string.routing_section_ip)) },
                )
                FilterChip(
                    selected = state.filter == 2,
                    onClick = { viewModel.setFilter(2) },
                    label = { Text(stringResource(R.string.routing_section_geo)) },
                )
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    stringResource(R.string.route_add),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onAddRule)
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(R.string.routing_advanced),
                    modifier = Modifier
                        .clickable(onClick = onAdvanced)
                        .padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge,
                )
                IconButton(onClick = { exportLauncher.launch("nami-routing.json") }) {
                    Icon(Icons.Filled.Share, contentDescription = "Export routing JSON")
                }
                IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                    Icon(Icons.Filled.Add, contentDescription = "Import routing JSON")
                }
                Text(
                    stringResource(R.string.route_reset),
                    modifier = Modifier
                        .clickable { resetRequested = true }
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        if (state.domainRules.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.routing_section_domain)) }
            state.domainRules.forEach { rule ->
                item(key = "d${rule.id}") {
                    RuleCard(
                        rule = rule,
                        onEnabled = { item, enabled ->
                            viewModel.setEnabled(item, enabled, onReloadRequired)
                        },
                        onEdit = onEditRule,
                    )
                }
            }
        }
        if (state.ipRules.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.routing_section_ip)) }
            state.ipRules.forEach { rule ->
                item(key = "i${rule.id}") {
                    RuleCard(
                        rule = rule,
                        onEnabled = { item, enabled ->
                            viewModel.setEnabled(item, enabled, onReloadRequired)
                        },
                        onEdit = onEditRule,
                    )
                }
            }
        }
        if (state.geoRules.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.routing_section_geo)) }
            state.geoRules.forEach { rule ->
                item(key = "g${rule.id}") {
                    RuleCard(
                        rule = rule,
                        onEnabled = { item, enabled ->
                            viewModel.setEnabled(item, enabled, onReloadRequired)
                        },
                        onEdit = onEditRule,
                    )
                }
            }
        }
        if (!state.loading && state.allRuleCount == 0) {
            item {
                EmptyState(
                    icon = Icons.Filled.Place,
                    title = stringResource(R.string.routing_empty_title),
                    body = stringResource(R.string.routing_empty_body),
                    primaryLabel = stringResource(R.string.route_add),
                    onPrimary = onAddRule,
                    secondaryLabel = stringResource(R.string.routing_import_json),
                    onSecondary = { importLauncher.launch(arrayOf("application/json", "text/*")) },
                )
            }
        } else if (!state.loading && state.domainRules.isEmpty() && state.ipRules.isEmpty() && state.geoRules.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Filled.Place,
                    title = stringResource(R.string.routing_no_matches_title),
                    body = stringResource(R.string.routing_no_matches_body),
                )
            }
        }
    }
    }

    if (resetRequested) {
        NamiAlertDialog(
            onDismissRequest = { resetRequested = false },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.routing_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    resetRequested = false
                    viewModel.resetRules(onReloadRequired)
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { resetRequested = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    presetDialog?.let { initialPreset ->
        RoutingPresetDialog(
            initialPreset = initialPreset,
            applying = state.presetApplying,
            onDismiss = { if (!state.presetApplying) presetDialog = null },
            onConfirm = { selectedPreset ->
                viewModel.applyPreset(
                    preset = selectedPreset,
                    onReloadRequired = onReloadRequired,
                ) { result ->
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            if (result.isSuccess) {
                                "Applied ${selectedPreset.title}"
                            } else {
                                result.exceptionOrNull()?.message ?: "Unable to apply preset"
                            },
                        )
                    }
                    if (result.isSuccess) presetDialog = null
                }
            },
        )
    }
}

@Composable
private fun RoutingPresetDialog(
    initialPreset: RoutingPreset,
    applying: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (RoutingPreset) -> Unit,
) {
    var draft by remember(initialPreset) { mutableStateOf(initialPreset) }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.routing_preset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RoutingPreset.entries.forEach { preset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !applying) { draft = preset }
                            .padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = draft == preset,
                            onClick = { if (!applying) draft = preset },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(preset.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                preset.description,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(draft) },
                enabled = !applying,
            ) {
                if (applying) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text(stringResource(R.string.apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !applying) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun RuleCard(
    rule: RuleUi,
    onEnabled: (RuleUi, Boolean) -> Unit,
    onEdit: (RuleUi) -> Unit,
) {
    NamiCard(
        onClick = { onEdit(rule) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .tvFocusable(shape = MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    rule.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
                Text(
                    rule.outbound,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(8.dp))
            NamiSwitch(
                checked = rule.enabled,
                onCheckedChange = { onEnabled(rule, it) },
            )
        }
    }
}
