@file:OptIn(
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)
package io.github.hhwkart.nami.ui.compose.groups


import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import io.github.hhwkart.nami.ui.compose.common.NamiDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import io.github.hhwkart.nami.ui.compose.theme.tvFocusable
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.fmt.toUniversalLink
import io.github.hhwkart.nami.ui.compose.common.QrCodeDialog
import io.github.hhwkart.nami.ui.compose.common.EmptyState
import io.github.hhwkart.nami.ui.compose.common.ErrorState
import io.github.hhwkart.nami.ui.compose.common.NamiAutoFocusTextField
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import io.github.hhwkart.nami.ui.compose.style.NamiSwitch
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun GroupsScreen(
    onEditGroup: (GroupUi) -> Unit,
    bottomBarPadding: Dp = 0.dp,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var showAddBasic by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<GroupUi?>(null) }
    var clearTarget by remember { mutableStateOf<GroupUi?>(null) }
    var qrPayload by remember { mutableStateOf<GroupQrPayload?>(null) }
    var pendingExport by remember { mutableStateOf<GroupExport?>(null) }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val export = pendingExport
        pendingExport = null
        if (uri != null && export != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(export.content)
                    } ?: error("Unable to open the selected file")
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.action_export_msg, Toast.LENGTH_SHORT).show()
                    }
                }.onFailure { error ->
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, error.message ?: "Export failed", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun copy(text: String) {
        Toast.makeText(
            context,
            if (SagerNet.trySetPrimaryClip(text)) R.string.action_export_msg else R.string.action_export_err,
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun handleGroupAction(group: GroupUi, action: GroupAction) {
        when (action) {
            GroupAction.SUBSCRIPTION_QR -> runCatching { group.group.toUniversalLink() }
                .onSuccess { qrPayload = GroupQrPayload(group.name, it) }
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            GroupAction.COPY_SUBSCRIPTION -> runCatching { group.group.toUniversalLink() }
                .onSuccess(::copy)
                .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            GroupAction.COPY_PROFILES -> scope.launch {
                runCatching { viewModel.profileLinks(group.id) }
                    .onSuccess(::copy)
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
            GroupAction.EXPORT_PROFILES -> scope.launch {
                runCatching { viewModel.profileLinks(group.id) }.onSuccess { links ->
                    val fileName = "profiles_${group.name}.txt"
                    pendingExport = GroupExport(links, fileName)
                    exportFile.launch(fileName)
                }.onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
            GroupAction.CLEAR -> clearTarget = group
            GroupAction.MOVE_UP -> viewModel.moveGroup(group, -1)
            GroupAction.MOVE_DOWN -> viewModel.moveGroup(group, 1)
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + 4.dp,
                end = contentPadding.calculateEndPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding() + 24.dp + bottomBarPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let { message ->
                item {
                    ErrorState(message = message, onRetry = viewModel::reload)
                }
            }
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.groups.any { it.isSubscription }) {
                        TextButton(onClick = viewModel::updateAll) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text(stringResource(R.string.update_all_subscription))
                        }
                    }
                    TextButton(onClick = { showAddBasic = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.groups_add_basic))
                    }
                    TextButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Text(stringResource(R.string.groups_add_subscription))
                    }
                }
            }
            items(state.groups, key = { it.id }) { group ->
                SubscriptionCard(
                    group = group,
                    onToggle = { viewModel.setAutoUpdate(group, it) },
                    onUpdate = { viewModel.updateGroup(group) },
                    onEdit = { onEditGroup(group) },
                    onDelete = { deleteTarget = group },
                    onAction = { handleGroupAction(group, it) },
                )
            }
            if (state.groups.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Add,
                        title = stringResource(R.string.groups_empty_title),
                        body = stringResource(R.string.groups_empty_body),
                        primaryLabel = stringResource(R.string.groups_add_basic),
                        onPrimary = { showAddBasic = true },
                        secondaryLabel = stringResource(R.string.groups_add_subscription),
                        onSecondary = { showAdd = true },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddSubscriptionDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, url ->
                showAdd = false
                viewModel.addSubscription(name, url)
            },
        )
    }

    if (showAddBasic) {
        AddBasicGroupDialog(
            onDismiss = { showAddBasic = false },
            onConfirm = { name ->
                showAddBasic = false
                viewModel.addBasicGroup(name)
            },
        )
    }

    qrPayload?.let { qr ->
        QrCodeDialog(qr.title, qr.content) { qrPayload = null }
    }

    clearTarget?.let { target ->
        NamiAlertDialog(
            onDismissRequest = { clearTarget = null },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.clear_profiles_message)) },
            confirmButton = {
                TextButton(onClick = {
                    clearTarget = null
                    viewModel.clearGroup(target)
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { clearTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    deleteTarget?.let { target ->
        NamiAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.groups_delete_named_confirm, target.name)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteGroup(target)
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun SubscriptionCard(
    group: GroupUi,
    onToggle: (Boolean) -> Unit,
    onUpdate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAction: (GroupAction) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val daysLeft = if (group.expireAt > 0) {
        TimeUnit.SECONDS.toDays(group.expireAt - System.currentTimeMillis() / 1000)
    } else -1L

    NamiCard(
        onClick = onEdit,
        enabled = !group.group.ungrouped && !group.updating,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(shape = MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(group.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.group_status_proxies, group.profileCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (group.isSubscription) {
                    if (group.updating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp), strokeWidth = 2.dp,
                        )
                    } else {
                        IconButton(onClick = onUpdate) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = stringResource(R.string.group_update),
                            )
                        }
                    }
                }
                if (!group.group.ungrouped) {
                    IconButton(onClick = onEdit, enabled = !group.updating) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = onDelete, enabled = !group.updating) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                androidx.compose.foundation.layout.Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.group_actions),
                        )
                    }
                    NamiDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        if (group.isSubscription) {
                            GroupActionMenuItem(
                                stringResource(R.string.group_subscription_qr),
                                GroupAction.SUBSCRIPTION_QR,
                                onAction,
                            ) { menuExpanded = false }
                            GroupActionMenuItem(
                                stringResource(R.string.group_copy_subscription),
                                GroupAction.COPY_SUBSCRIPTION,
                                onAction,
                            ) { menuExpanded = false }
                        }
                        GroupActionMenuItem(
                            stringResource(R.string.group_copy_profiles),
                            GroupAction.COPY_PROFILES,
                            onAction,
                        ) { menuExpanded = false }
                        GroupActionMenuItem(
                            stringResource(R.string.action_export_file),
                            GroupAction.EXPORT_PROFILES,
                            onAction,
                        ) { menuExpanded = false }
                        if (!group.updating) {
                            GroupActionMenuItem(
                                stringResource(R.string.clear_profiles),
                                GroupAction.CLEAR,
                                onAction,
                            ) { menuExpanded = false }
                        }
                        if (!group.group.ungrouped && !group.updating) {
                            GroupActionMenuItem(
                                stringResource(R.string.profile_move_up),
                                GroupAction.MOVE_UP,
                                onAction,
                            ) { menuExpanded = false }
                            GroupActionMenuItem(
                                stringResource(R.string.profile_move_down),
                                GroupAction.MOVE_DOWN,
                                onAction,
                            ) { menuExpanded = false }
                        }
                    }
                }
            }

            if (group.isSubscription && daysLeft >= 0) {
                val bannerColor = when {
                    daysLeft <= 7 -> MaterialTheme.colorScheme.errorContainer
                    daysLeft <= 30 -> MaterialTheme.colorScheme.secondaryContainer
                    else -> null
                }
                bannerColor?.let {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            stringResource(R.string.groups_expire_days_left, daysLeft),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            if (group.bytesTotal > 0L) {
                val fraction = (group.bytesUsed.toFloat() / group.bytesTotal).coerceIn(0f, 1f)
                val barColor = when {
                    fraction > 0.9f -> MaterialTheme.colorScheme.error
                    fraction > 0.7f -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    color = barColor,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${Formatter.formatFileSize(null, group.bytesUsed)} / ${
                        Formatter.formatFileSize(null, group.bytesTotal)
                    }",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else if (group.bytesUsed > 0L) {
                Text(
                    "${Formatter.formatFileSize(null, group.bytesUsed)} / " +
                            stringResource(R.string.subscription_unlimited),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (group.isSubscription && group.lastUpdated > 0) {
                Text(
                    stringResource(
                        R.string.groups_last_updated,
                        relativeTime(group.lastUpdated * 1000L),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (group.isSubscription) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.auto_update),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    NamiSwitch(checked = group.autoUpdate, onCheckedChange = onToggle)
                }
            }
        }
    }
}

private fun relativeTime(then: Long): String {
    val diff = System.currentTimeMillis() - then
    val minutes = diff / 60000
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 1440 -> "${minutes / 60}h"
        else -> "${minutes / 1440}d"
    }
}

@Composable
private fun AddSubscriptionDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_add_subscription)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NamiAutoFocusTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.groups_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.groups_url_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank(),
                onClick = { onConfirm(name, url.trim()) },
            ) { Text(stringResource(R.string.yes)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
        properties = DialogProperties(usePlatformDefaultWidth = true),
    )
}

@Composable
private fun AddBasicGroupDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    NamiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.groups_add_basic)) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                NamiAutoFocusTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.groups_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }) {
                Text(stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun GroupActionMenuItem(
    label: String,
    action: GroupAction,
    onAction: (GroupAction) -> Unit,
    onDismiss: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = {
            onDismiss()
            onAction(action)
        },
    )
}

private enum class GroupAction {
    SUBSCRIPTION_QR,
    COPY_SUBSCRIPTION,
    COPY_PROFILES,
    EXPORT_PROFILES,
    CLEAR,
    MOVE_UP,
    MOVE_DOWN,
}

private data class GroupQrPayload(val title: String, val content: String)

private data class GroupExport(val content: String, val fileName: String)
