package io.github.hhwkart.nami.ui.compose.groupeditor

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import io.github.hhwkart.nami.ui.compose.common.NamiDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.GroupOrder
import io.github.hhwkart.nami.GroupType
import io.github.hhwkart.nami.ui.compose.style.NamiSwitch
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GroupEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    BackHandler(enabled = !state.saving) {
        viewModel.requestBack()
    }

    if (state.showDiscardWarning) {
        NamiAlertDialog(
            onDismissRequest = viewModel::dismissDiscardWarning,
            title = { Text("Discard changes?") },
            text = { Text("Your group changes have not been saved.") },
            confirmButton = {
                TextButton(onClick = viewModel::discardChanges) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDiscardWarning) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.groupId == 0L) "New group" else "Group settings") },
                navigationIcon = {
                    IconButton(onClick = viewModel::requestBack, enabled = !state.saving) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(
                        onClick = viewModel::save,
                        enabled = !state.loading && !state.saving && !state.entityMissing,
                    ) { Text("Save") }
                },
            )
        },
    ) { padding ->
        if (state.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(padding))
        } else {
            GroupEditorForm(
                state = state,
                viewModel = viewModel,
                contentPadding = padding,
            )
        }
    }
}

@Composable
private fun GroupEditorForm(
    state: GroupEditorState,
    viewModel: GroupEditorViewModel,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(top = contentPadding.calculateTopPadding())
            .consumeWindowInsets(PaddingValues(top = contentPadding.calculateTopPadding()))
            .imePadding(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
            top = 8.dp,
            end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.error?.let { message ->
            item {
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text("Group name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            EditorDropdown(
                label = "Group type",
                selected = if (state.type == GroupType.SUBSCRIPTION) "Subscription" else "Basic",
                values = listOf(GroupType.BASIC to "Basic", GroupType.SUBSCRIPTION to "Subscription"),
                onSelected = viewModel::setType,
            )
        }
        item {
            EditorDropdown(
                label = "Group order",
                selected = when (state.order) {
                    GroupOrder.BY_NAME -> "By name"
                    GroupOrder.BY_DELAY -> "By delay"
                    else -> "Original order"
                },
                values = listOf(
                    GroupOrder.ORIGIN to "Original order",
                    GroupOrder.BY_NAME to "By name",
                    GroupOrder.BY_DELAY to "By delay",
                ),
                onSelected = viewModel::setOrder,
            )
        }
        item {
            SwitchRow("Use selector", state.isSelector, viewModel::setSelector)
        }
        item {
            ProfileDropdown(
                label = "Front proxy",
                selected = state.frontProxy,
                profiles = state.profiles,
                onSelected = viewModel::setFrontProxy,
            )
        }
        item {
            ProfileDropdown(
                label = "Landing proxy",
                selected = state.landingProxy,
                profiles = state.profiles,
                onSelected = viewModel::setLandingProxy,
            )
        }
        if (state.type == GroupType.SUBSCRIPTION) {
            item { Text("Subscription settings", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = state.subscriptionLink,
                    onValueChange = viewModel::setSubscriptionLink,
                    label = { Text("Subscription link") },
                    isError = "subscriptionLink" in state.errors,
                    supportingText = state.errors["subscriptionLink"]?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SwitchRow("Force resolve", state.subscriptionForceResolve, viewModel::setForceResolve) }
            item { SwitchRow("Deduplication", state.subscriptionDeduplication, viewModel::setDeduplication) }
            item {
                SwitchRow(
                    "Update only when connected",
                    state.subscriptionUpdateWhenConnectedOnly,
                    viewModel::setUpdateWhenConnectedOnly,
                )
            }
            item {
                OutlinedTextField(
                    value = state.subscriptionUserAgent,
                    onValueChange = viewModel::setUserAgent,
                    label = { Text("Subscription user agent") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SwitchRow("Auto update", state.subscriptionAutoUpdate, viewModel::setAutoUpdate) }
            item {
                OutlinedTextField(
                    value = state.subscriptionAutoUpdateDelay,
                    onValueChange = viewModel::setAutoUpdateDelay,
                    label = { Text("Auto-update delay (minutes)") },
                    isError = "subscriptionAutoUpdateDelay" in state.errors,
                    supportingText = state.errors["subscriptionAutoUpdateDelay"]?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = state.subscriptionAutoUpdate,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
        item {
            NamiLiquidButton(
                onClick = viewModel::save,
                enabled = !state.saving && !state.entityMissing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        NamiSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun <T> EditorDropdown(
    label: String,
    selected: String,
    values: List<Pair<T, String>>,
    onSelected: (T) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = {
                focusManager.clearFocus()
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(selected, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        NamiDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            values.forEach { (value, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelected(value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ProfileDropdown(
    label: String,
    selected: Long,
    profiles: List<GroupProfileChoice>,
    onSelected: (Long) -> Unit,
) {
    val values = listOf(GroupEditorViewModel.NO_PROXY to "None") + profiles.map { it.id to "${it.title} (${it.type})" }
    EditorDropdown(
        label = label,
        selected = values.firstOrNull { it.first == selected }?.second ?: "Select profile",
        values = values,
        onSelected = onSelected,
    )
}
