package io.github.hhwkart.nami.ui.compose.routeeditor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import io.github.hhwkart.nami.ui.compose.common.NamiDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteEditorScreen(
    onBack: () -> Unit,
    viewModel: RouteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = !state.saving) { viewModel.requestBack() }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    if (state.showEmptyRuleWarning) {
        NamiAlertDialog(
            onDismissRequest = viewModel::dismissEmptyRuleWarning,
            title = { Text("Empty route") },
            text = { Text("Set some rules before saving") },
            confirmButton = {
                TextButton(onClick = viewModel::dismissEmptyRuleWarning) { Text("OK") }
            },
        )
    }
    if (state.showDiscardWarning) {
        NamiAlertDialog(
            onDismissRequest = viewModel::dismissDiscardWarning,
            title = { Text("Discard changes?") },
            text = { Text("Your route changes have not been saved.") },
            confirmButton = {
                TextButton(onClick = viewModel::discardChanges) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscardWarning) { Text("Cancel") }
            },
        )
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    if (showDeleteConfirm) {
        NamiAlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_route_prompt)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) {
                    Text(
                        stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.ruleId == 0L) "Create route" else "Edit route") },
                navigationIcon = {
                    IconButton(
                        onClick = viewModel::requestBack,
                        enabled = !state.saving,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.ruleId != 0L) {
                        IconButton(
                            onClick = { showDeleteConfirm = true },
                            enabled = !state.loading && !state.saving,
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = !state.loading && !state.saving,
                    ) { Text(if (state.saving) "Saving…" else "Save") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .consumeWindowInsets(PaddingValues(top = padding.calculateTopPadding()))
                .imePadding(),
            contentPadding = PaddingValues(
                start = padding.calculateStartPadding(layoutDirection) + 16.dp,
                top = 8.dp,
                end = padding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                RouteTextField("Route name", state.name, viewModel::updateName)
            }
            item {
                RouteTextField(
                    "Custom JSON",
                    state.config,
                    viewModel::updateConfig,
                    minLines = 3,
                )
            }
            item { Text("Match conditions", style = MaterialTheme.typography.titleMedium) }
            item { RouteTextField("Domains", state.domains, viewModel::updateDomains) }
            item { RouteTextField("Destination IP", state.ip, viewModel::updateIp) }
            item { RouteTextField("Destination port", state.port, viewModel::updatePort) }
            item { RouteTextField("Source IP", state.source, viewModel::updateSource) }
            item { RouteTextField("Source port", state.sourcePort, viewModel::updateSourcePort) }
            item { Text("Outbound", style = MaterialTheme.typography.titleMedium) }
            item {
                RouteDropdown(
                    label = "Outbound action",
                    value = outboundLabel(state.outboundMode),
                    options = listOf(
                        "Proxy" to RouteEditorUiState.OUTBOUND_PROXY.toString(),
                        "Bypass" to RouteEditorUiState.OUTBOUND_BYPASS.toString(),
                        "Block" to RouteEditorUiState.OUTBOUND_BLOCK.toString(),
                        "Profile" to RouteEditorUiState.OUTBOUND_PROFILE.toString(),
                    ),
                    onSelected = { viewModel.updateOutboundMode(it.toInt()) },
                )
            }
            if (state.outboundMode == RouteEditorUiState.OUTBOUND_PROFILE) {
                item {
                    ProfileDropdown(state, viewModel::updateOutboundProfile)
                }
            }
            item { Text("Applications", style = MaterialTheme.typography.titleMedium) }
            item {
                AppSelector(state, viewModel)
            }
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun RouteTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        minLines = minLines,
        singleLine = minLines == 1,
    )
}

@Composable
private fun RouteDropdown(
    label: String,
    value: String,
    options: List<Pair<String, String>>,
    onSelected: (String) -> Unit,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                focusManager.clearFocus()
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("$label: $value", modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        NamiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (display, stored) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = {
                        expanded = false
                        onSelected(stored)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileDropdown(
    state: RouteEditorUiState,
    onSelected: (Long) -> Unit,
) {
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    val selected = state.profiles.firstOrNull { it.id == state.outboundProfileId }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = {
                focusManager.clearFocus()
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                selected?.let { "Profile: ${it.name} (${it.type})" } ?: "Select profile",
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        NamiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            state.profiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text("${profile.name} (${profile.type})") },
                    onClick = {
                        expanded = false
                        onSelected(profile.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun AppSelector(
    state: RouteEditorUiState,
    viewModel: RouteEditorViewModel,
) {
    OutlinedTextField(
        value = state.appSearch,
        onValueChange = viewModel::updateAppSearch,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Search apps") },
        singleLine = true,
    )
    Text(
        "${state.packages.size} selected",
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.bodyMedium,
    )
    val query = state.appSearch.trim()
    val filtered = state.appPackages.filter { packageName ->
        query.isBlank() || packageName.contains(query, ignoreCase = true) ||
            state.appLabels[packageName]?.contains(query, ignoreCase = true) == true
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        filtered.forEach { packageName ->
            LaunchedEffect(packageName) { viewModel.loadAppLabel(packageName) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable { viewModel.togglePackage(packageName) }
                    .semantics { contentDescription = "Select $packageName" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = packageName in state.packages,
                    onCheckedChange = { viewModel.togglePackage(packageName) },
                )
                Column {
                    Text(state.appLabels[packageName] ?: packageName)
                    Text(
                        packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun outboundLabel(mode: Int): String = when (mode) {
    RouteEditorUiState.OUTBOUND_BYPASS -> "Bypass"
    RouteEditorUiState.OUTBOUND_BLOCK -> "Block"
    RouteEditorUiState.OUTBOUND_PROFILE -> "Profile"
    else -> "Proxy"
}
