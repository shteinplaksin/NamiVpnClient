package io.github.hhwkart.nami.ui.compose.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ProtocolEditorScreen(
    viewModel: ProtocolEditorViewModel = hiltViewModel(),
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    fun requestBack() {
        if (state.dirty && !state.saved) confirmDiscard = true else onBack()
    }

    BackHandler(onBack = ::requestBack)

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }
    LaunchedEffect(state.message) {
        if (state.message != null) {
            snackbarHostState.showSnackbar(state.message!!)
            viewModel.consumeMessage()
        }
    }

    val busy = state.loading || state.isSaving || state.isTesting
    val fields = ProtocolEditorCatalog.fields(state.protocol)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New profile" else "Edit profile") },
                navigationIcon = {
                    IconButton(onClick = ::requestBack, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = viewModel::test,
                        enabled = state.protocol.hasEndpoint && state.isValid && !busy,
                    ) {
                        Text(if (state.isTesting) "Testing…" else "Test")
                    }
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.isValid && !busy,
                    ) {
                        Text(if (state.isSaving) "Saving…" else "Save")
                    }
                },
                scrollBehavior = androidx.compose.material3.TopAppBarDefaults.pinnedScrollBehavior(
                    rememberTopAppBarState()
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
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
            if (state.isNew) {
                item {
                    Text("Protocol", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProtocolKind.entries.filter { it.selectableForNew }.forEach { protocol ->
                            FilterChip(
                                selected = protocol == state.protocol,
                                onClick = { viewModel.selectProtocol(protocol) },
                                label = { Text(protocol.displayName) },
                            )
                        }
                    }
                }
            }

            EditorSectionItems(
                fields = fields,
                section = EditorSection.COMMON,
                state = state,
                onFieldChange = viewModel::updateField,
                onFormatJson = viewModel::formatJson,
            )
            EditorSectionItems(
                fields = fields,
                section = EditorSection.PROTOCOL,
                state = state,
                onFieldChange = viewModel::updateField,
                onFormatJson = viewModel::formatJson,
            )
            EditorSectionItems(
                fields = fields,
                section = EditorSection.TRANSPORT,
                state = state,
                onFieldChange = viewModel::updateField,
                onFormatJson = viewModel::formatJson,
            )
            EditorSectionItems(
                fields = fields,
                section = EditorSection.TLS,
                state = state,
                onFieldChange = viewModel::updateField,
                onFormatJson = viewModel::formatJson,
            )

            item {
                TextButton(
                    onClick = { advancedExpanded = !advancedExpanded },
                    modifier = Modifier.semantics {
                        contentDescription = "Advanced settings"
                    },
                ) {
                    Text(if (advancedExpanded) "Hide advanced settings" else "Show advanced settings")
                }
            }
            if (advancedExpanded) {
                EditorSectionItems(
                    fields = fields,
                    section = EditorSection.ADVANCED,
                    state = state,
                    onFieldChange = viewModel::updateField,
                    onFormatJson = viewModel::formatJson,
                )
            }

            if (state.protocol == ProtocolKind.CHAIN && state.chainCandidates.isNotEmpty()) {
                item {
                    Text("Chain profiles", style = MaterialTheme.typography.titleMedium)
                }
                val selectedCandidates = state.chainCandidates.filter { it.selected }
                items(state.chainCandidates, key = { it.id }) { candidate ->
                    ChainCandidateRow(
                        candidate = candidate,
                        onToggle = { viewModel.toggleChain(candidate.id) },
                        onMoveUp = {
                            val index = selectedCandidates.indexOfFirst { it.id == candidate.id }
                            if (index > 0) viewModel.moveChain(index, index - 1)
                        },
                        onMoveDown = {
                            val index = selectedCandidates.indexOfFirst { it.id == candidate.id }
                            if (index >= 0 && index < selectedCandidates.lastIndex) {
                                viewModel.moveChain(index, index + 1)
                            }
                        },
                    )
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (confirmDiscard) {
        NamiAlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved profile changes will be lost.") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.EditorSectionItems(
    fields: List<EditorFieldSpec>,
    section: EditorSection,
    state: ProtocolEditorUiState,
    onFieldChange: (String, String) -> Unit,
    onFormatJson: (String) -> Unit,
) {
    fields.filter { it.section == section && it.isVisible(state.form) }.forEach { field ->
        item(key = "${section.name}:${field.key}") {
            EditorField(
                field = field,
                value = state.form[field.key] ?: field.defaultValue,
                error = state.errors[field.key],
                onValueChange = { onFieldChange(field.key, it) },
                onFormatJson = { onFormatJson(field.key) },
            )
        }
    }
}

@Composable
private fun EditorField(
    field: EditorFieldSpec,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    onFormatJson: () -> Unit,
) {
    when (field.kind) {
        EditorFieldKind.NUMBER -> EditorNumberField(
            label = field.label,
            value = value,
            onValueChange = onValueChange,
            supportingText = field.supportingText,
            error = error,
        )
        EditorFieldKind.PASSWORD -> EditorPasswordField(
            label = field.label,
            value = value,
            onValueChange = onValueChange,
            supportingText = field.supportingText,
            error = error,
        )
        EditorFieldKind.SWITCH -> EditorSwitchField(
            label = field.label,
            checked = value.toBoolean(),
            onCheckedChange = { onValueChange(it.toString()) },
            supportingText = field.supportingText,
        )
        EditorFieldKind.DROPDOWN -> EditorDropdownField(
            label = field.label,
            value = value,
            options = field.options,
            onValueChange = onValueChange,
            supportingText = field.supportingText,
            error = error,
        )
        EditorFieldKind.JSON -> Column {
            EditorTextField(
                label = field.label,
                value = value,
                onValueChange = onValueChange,
                supportingText = field.supportingText,
                error = error,
                singleLine = false,
            )
            TextButton(onClick = onFormatJson, enabled = value.isNotBlank()) {
                Text("Format JSON")
            }
        }
        EditorFieldKind.MULTILINE -> EditorTextField(
            label = field.label,
            value = value,
            onValueChange = onValueChange,
            supportingText = field.supportingText,
            error = error,
            singleLine = false,
        )
        EditorFieldKind.TEXT -> EditorTextField(
            label = field.label,
            value = value,
            onValueChange = onValueChange,
            supportingText = field.supportingText,
            error = error,
        )
    }
}

@Composable
private fun ChainCandidateRow(
    candidate: ChainCandidate,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorSwitchField(
            label = "${candidate.name} (${candidate.type})",
            checked = candidate.selected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onMoveUp, enabled = candidate.selected) { Text("↑") }
        TextButton(onClick = onMoveDown, enabled = candidate.selected) { Text("↓") }
    }
}
