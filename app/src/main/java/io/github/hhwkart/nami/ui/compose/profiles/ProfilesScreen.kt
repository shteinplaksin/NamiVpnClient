package io.github.hhwkart.nami.ui.compose.profiles

import android.content.Context
import android.content.Intent
import android.os.Build
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.hhwkart.nami.ui.compose.theme.tvFocusable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import io.github.hhwkart.nami.ui.compose.common.NamiDropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.GroupOrder
import io.github.hhwkart.nami.GroupType
import io.github.hhwkart.nami.QuickToggleShortcut
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.ProxyGroup
import io.github.hhwkart.nami.ui.ConnectionTestViewModel
import io.github.hhwkart.nami.ui.compose.common.QrCodeDialog
import io.github.hhwkart.nami.ui.compose.common.EmptyState
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import io.github.hhwkart.nami.fmt.toUniversalLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfilesScreen(
    onEditProfile: (ProfileUi) -> Unit,
    onAddProfile: () -> Unit,
    onQuickSetup: () -> Unit = {},
    viewModel: ProfilesViewModel = hiltViewModel(),
    testViewModel: ConnectionTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var qrPayload by remember { mutableStateOf<QrPayload?>(null) }
    var moveTarget by remember { mutableStateOf<ProfileUi?>(null) }
    var pendingExport by remember { mutableStateOf<ProfileExport?>(null) }
    var cleanupTarget by remember { mutableStateOf<GroupCleanup?>(null) }
    var testProgressTick by remember { mutableStateOf(0) }
    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
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

    androidx.compose.runtime.LaunchedEffect(testViewModel) {
        viewModel.syncTestState(testViewModel)
        testViewModel.finished.collectLatest {
            viewModel.syncTestState(testViewModel)
        }
    }
    androidx.compose.runtime.LaunchedEffect(testViewModel) {
        testViewModel.updates.collectLatest { testProgressTick++ }
    }

    fun copy(text: String) {
        val result = if (SagerNet.trySetPrimaryClip(text)) {
            R.string.action_export_msg
        } else {
            R.string.action_export_err
        }
        Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
    }

    fun handleProfileAction(profile: ProfileUi, action: ProfileAction) {
        runCatching {
            when (action) {
                ProfileAction.STANDARD_QR -> qrPayload = QrPayload(
                    profile.name,
                    profile.entity.toStdLink(),
                )
                ProfileAction.UNIVERSAL_QR -> qrPayload = QrPayload(
                    profile.name,
                    profile.entity.requireBean().toUniversalLink(),
                )
                ProfileAction.COPY_STANDARD -> copy(profile.entity.toStdLink())
                ProfileAction.COPY_UNIVERSAL -> copy(
                    profile.entity.requireBean().toUniversalLink(),
                )
                ProfileAction.COPY_CONFIG -> copy(profile.entity.exportConfig().first)
                ProfileAction.EXPORT_CONFIG -> {
                    val (content, fileName) = profile.entity.exportConfig()
                    pendingExport = ProfileExport(content, fileName)
                    exportFile.launch(fileName)
                }
                ProfileAction.MOVE -> moveTarget = profile
                ProfileAction.MOVE_UP -> viewModel.moveWithinGroup(profile, -1)
                ProfileAction.MOVE_DOWN -> viewModel.moveWithinGroup(profile, 1)
                ProfileAction.SHORTCUT -> requestProfileShortcut(context, profile)
            }
        }.onFailure { error ->
            Toast.makeText(context, error.message ?: "Action failed", Toast.LENGTH_LONG).show()
        }
    }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { contentPadding ->
        val safePadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
                .consumeWindowInsets(safePadding)
                .imePadding(),
        ) {
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
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TestAllButton(
                    testing = state.testingGroupIds.isNotEmpty(),
                    onClick = {
                        val firstGroup = state.groups.firstOrNull() ?: return@TestAllButton
                        viewModel.testAll(firstGroup.id, firstGroup.displayName(), testViewModel)
                    },
                )
                IconButton(onClick = onAddProfile) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_profile))
                }
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
            if (testProgressTick >= 0 && testViewModel.isRunning) {
                item {
                    val completed = testViewModel.finishedN.get()
                    val total = testViewModel.proxyN
                    NamiCard(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Testing profiles", style = MaterialTheme.typography.titleMedium)
                            Text("$completed/$total", style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(
                                progress = { if (total > 0) completed.toFloat() / total else 0f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            androidx.compose.material3.TextButton(onClick = testViewModel::cancel) {
                                Text("Cancel all")
                            }
                        }
                    }
                }
            }
            if (state.items.isEmpty() && state.query.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Add,
                        title = stringResource(R.string.profiles_empty),
                        body = stringResource(R.string.profiles_empty_body),
                        primaryLabel = stringResource(R.string.action_add_profile),
                        onPrimary = onAddProfile,
                        secondaryLabel = stringResource(R.string.profiles_quick_setup),
                        onSecondary = onQuickSetup,
                    )
                }
            } else if (state.items.isEmpty() && state.query.isNotEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Filled.Search,
                        title = stringResource(R.string.profiles_search_empty_title),
                        body = stringResource(R.string.profiles_search_empty_body),
                    )
                }
            }
            state.groups.forEach { group ->
                val groupItems = state.items.filter { it.groupId == group.id }
                if (groupItems.isEmpty()) return@forEach
                item(key = "header-${group.id}") {
                    GroupHeader(
                        group = group,
                        testing = state.testingGroupIds.contains(group.id),
                        onOrderChange = { viewModel.setOrder(group, it) },
                        onTestAll = { icmp ->
                            viewModel.testAll(
                                group.id,
                                group.displayName(),
                                testViewModel,
                                icmp,
                            )
                        },
                        onUpdateSubscription = { viewModel.updateSubscription(group) },
                        onClearTraffic = { viewModel.clearTraffic(group.id) },
                        onClearResults = { viewModel.clearTestResults(group.id) },
                        onDeleteUnavailable = {
                            cleanupTarget = GroupCleanup(group, GroupCleanupAction.DELETE_UNAVAILABLE)
                        },
                        onRemoveDuplicates = {
                            cleanupTarget = GroupCleanup(group, GroupCleanupAction.REMOVE_DUPLICATES)
                        },
                    )
                }
                items(groupItems.size, key = { groupItems[it].id }) { index ->
                    val profile = groupItems[index]
                    ProfileCard(
                        profile = profile,
                        group = group,
                        canMoveToAnotherGroup = group.type == GroupType.BASIC &&
                                state.groups.any { it.type == GroupType.BASIC && it.id != group.id },
                        onConnect = { viewModel.connectProfile(profile) },
                        onEdit = { onEditProfile(profile) },
                        onDelete = {
                            viewModel.stageDelete(profile)
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.profile_removed, profile.name),
                                    actionLabel = context.getString(R.string.undo),
                                    withDismissAction = true,
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete(profile)
                                }
                            }
                        },
                        onQuickTest = {
                            viewModel.testProfile(profile)
                        },
                        onAction = { handleProfileAction(profile, it) },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    }

    qrPayload?.let { qr ->
        QrCodeDialog(qr.title, qr.content) { qrPayload = null }
    }

    moveTarget?.let { profile ->
        val destinations = state.groups.filter {
            it.type == GroupType.BASIC && it.id != profile.groupId
        }
        NamiAlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(stringResource(R.string.profile_move_title)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    destinations.forEach { group ->
                        Text(
                            group.displayName(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    moveTarget = null
                                    viewModel.moveProfile(profile, group)
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { moveTarget = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    cleanupTarget?.let { cleanup ->
        NamiAlertDialog(
            onDismissRequest = { cleanupTarget = null },
            title = { Text(stringResource(R.string.confirm)) },
            text = { Text(stringResource(R.string.delete_confirm_prompt)) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    cleanupTarget = null
                    when (cleanup.action) {
                        GroupCleanupAction.DELETE_UNAVAILABLE -> viewModel.deleteUnavailable(cleanup.group.id)
                        GroupCleanupAction.REMOVE_DUPLICATES -> viewModel.removeDuplicates(cleanup.group.id)
                    }
                }) { Text(stringResource(R.string.yes)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { cleanupTarget = null }) {
                    Text(stringResource(R.string.no))
                }
            },
        )
    }
}

@Composable
private fun TestAllButton(testing: Boolean, onClick: () -> Unit) {
    if (testing) {
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        }
    } else {
        IconButton(onClick = onClick) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.profiles_test_all),
            )
        }
    }
}

@Composable
private fun GroupHeader(
    group: ProxyGroup,
    testing: Boolean,
    onOrderChange: (Int) -> Unit,
    onTestAll: (Boolean) -> Unit,
    onUpdateSubscription: () -> Unit,
    onClearTraffic: () -> Unit,
    onClearResults: () -> Unit,
    onDeleteUnavailable: () -> Unit,
    onRemoveDuplicates: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var actionsMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(enabled = false) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.displayName(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (testing) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        }
        Text(
            stringResource(R.string.profiles_test_all),
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable { onTestAll(false) },
            style = MaterialTheme.typography.labelLarge,
        )
        Box {
        Text(
            stringResource(R.string.group_order),
            modifier = Modifier
                .padding(start = 8.dp)
                .clickable { sortMenu = true },
            style = MaterialTheme.typography.labelLarge,
        )
            NamiDropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_order_origin)) },
                    onClick = {
                        sortMenu = false
                        onOrderChange(GroupOrder.ORIGIN)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_order_by_name)) },
                    onClick = {
                        sortMenu = false
                        onOrderChange(GroupOrder.BY_NAME)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.group_order_by_delay)) },
                    onClick = {
                        sortMenu = false
                        onOrderChange(GroupOrder.BY_DELAY)
                    },
                )
            }
        }
        Box {
            IconButton(onClick = { actionsMenu = true }) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.group_actions),
                )
            }
            NamiDropdownMenu(expanded = actionsMenu, onDismissRequest = { actionsMenu = false }) {
                if (group.type == GroupType.SUBSCRIPTION) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.update_current_subscription)) },
                        onClick = {
                            actionsMenu = false
                            onUpdateSubscription()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.connection_test_url_test)) },
                    onClick = {
                        actionsMenu = false
                        onTestAll(false)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.connection_test_tcp_ping)) },
                    onClick = {
                        actionsMenu = false
                        onTestAll(true)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.clear_traffic_statistics)) },
                    onClick = {
                        actionsMenu = false
                        onClearTraffic()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.connection_test_clear_results)) },
                    onClick = {
                        actionsMenu = false
                        onClearResults()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.connection_test_delete_unavailable)) },
                    onClick = {
                        actionsMenu = false
                        onDeleteUnavailable()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.remove_duplicate)) },
                    onClick = {
                        actionsMenu = false
                        onRemoveDuplicates()
                    },
                )
            }
        }
    }
}

@Composable
fun latencyColor(ping: Int): androidx.compose.ui.graphics.Color = when {
    ping in 1..99 -> MaterialTheme.colorScheme.primary
    ping < 300 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

@Composable
private fun ProfileCard(
    profile: ProfileUi,
    group: ProxyGroup,
    canMoveToAnotherGroup: Boolean,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickTest: () -> Unit,
    onAction: (ProfileAction) -> Unit,
) {
    val context = LocalContext.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    true
                }

                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }

                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
            val icon = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> Icons.Filled.Delete
                else -> Icons.Filled.Edit
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(color, MaterialTheme.shapes.medium),
                contentAlignment = when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    else -> Alignment.CenterStart
                },
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        },
        enableDismissFromStartToEnd = profile.canEdit && !profile.running,
        enableDismissFromEndToStart = profile.canDelete && !profile.running,
        content = {
            ProfileCardContent(
                profile = profile,
                group = group,
                canMoveToAnotherGroup = canMoveToAnotherGroup,
                context = context,
                onConnect = onConnect,
                onEdit = onEdit,
                onDelete = onDelete,
                onQuickTest = onQuickTest,
                onAction = onAction,
            )
        },
    )
}

@Composable
private fun ProfileCardContent(
    profile: ProfileUi,
    group: ProxyGroup,
    canMoveToAnotherGroup: Boolean,
    context: Context,
    onConnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onQuickTest: () -> Unit,
    onAction: (ProfileAction) -> Unit,
) {
    var actionsExpanded by remember { mutableStateOf(false) }
    NamiCard(
        onClick = onConnect,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .tvFocusable(shape = MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                // quick action: test latency
                IconButton(onClick = onQuickTest) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = stringResource(R.string.connection_test),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (profile.running) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (profile.canEdit && !profile.running) {
                    Box {
                        IconButton(onClick = { actionsExpanded = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.profile_actions),
                            )
                        }
                        NamiDropdownMenu(
                            expanded = actionsExpanded,
                            onDismissRequest = { actionsExpanded = false },
                        ) {
                            if (profile.entity.haveStandardLink()) {
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_qr_standard),
                                    action = ProfileAction.STANDARD_QR,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_copy_standard),
                                    action = ProfileAction.COPY_STANDARD,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                            }
                            if (profile.entity.haveLink() && profile.entity.nekoBean == null) {
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_qr_universal),
                                    action = ProfileAction.UNIVERSAL_QR,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_copy_universal),
                                    action = ProfileAction.COPY_UNIVERSAL,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                            }
                            if (profile.entity.nekoBean == null) {
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_copy_config),
                                    action = ProfileAction.COPY_CONFIG,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.action_export_file),
                                    action = ProfileAction.EXPORT_CONFIG,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                            }
                            if (canMoveToAnotherGroup) {
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.move),
                                    action = ProfileAction.MOVE,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                            }
                            if (group.order == GroupOrder.ORIGIN) {
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_move_up),
                                    action = ProfileAction.MOVE_UP,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.profile_move_down),
                                    action = ProfileAction.MOVE_DOWN,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                ProfileActionMenuItem(
                                    label = stringResource(R.string.create_shortcut),
                                    action = ProfileAction.SHORTCUT,
                                    onAction = onAction,
                                    onDismiss = { actionsExpanded = false },
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    profile.type,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.shapes.extraSmall,
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Spacer(Modifier.size(8.dp))
                if (profile.address.isNotBlank()) {
                    Text(
                        profile.address,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (profile.status == 1) {
                    Text(
                        "${profile.ping} ms",
                        color = latencyColor(profile.ping),
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else if (profile.status >= 2) {
                    Text(
                        profile.error?.take(24) ?: stringResource(R.string.unavailable),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }
            }
            val totalTraffic = profile.tx + profile.rx
            if (totalTraffic > 0L) {
                Spacer(Modifier.height(4.dp))
                // ponytail: no per-profile quota known; bar shows relative
                // share within the rendered list, upgrade when quota data exists
                LinearProgressIndicator(
                    progress = { (totalTraffic % 100).toFloat() / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${Formatter.formatFileSize(context, profile.tx)} ↑ / ${
                        Formatter.formatFileSize(context, profile.rx)
                    } ↓",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                if (profile.canEdit && !profile.running) {
                    TextButton(R.string.edit, onEdit)
                }
                if (profile.canDelete && !profile.running) {
                    TextButton(R.string.delete, onDelete)
                }
            }
        }
    }
}

@Composable
private fun TextButton(textRes: Int, onClick: () -> Unit) {
    androidx.compose.material3.TextButton(onClick = onClick) {
        Text(stringResource(textRes))
    }
}

@Composable
private fun ProfileActionMenuItem(
    label: String,
    action: ProfileAction,
    onAction: (ProfileAction) -> Unit,
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

private fun requestProfileShortcut(context: Context, profile: ProfileUi) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        !ShortcutManagerCompat.isRequestPinShortcutSupported(context)
    ) {
        Toast.makeText(context, R.string.profile_shortcut_unavailable, Toast.LENGTH_SHORT).show()
        return
    }
    val shortcut = ShortcutInfoCompat.Builder(context, "shortcut-profile-${profile.id}")
        .setShortLabel(profile.name)
        .setLongLabel(profile.name)
        .setIcon(IconCompat.createWithResource(context, R.drawable.ic_qu_shadowsocks_launcher))
        .setIntent(
            Intent(context, QuickToggleShortcut::class.java).apply {
                action = Intent.ACTION_MAIN
                putExtra("profile", profile.id)
            },
        )
        .build()
    val requested = ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    Toast.makeText(
        context,
        if (requested) R.string.profile_shortcut_requested else R.string.profile_shortcut_unavailable,
        Toast.LENGTH_SHORT,
    ).show()
}

private enum class ProfileAction {
    STANDARD_QR,
    UNIVERSAL_QR,
    COPY_STANDARD,
    COPY_UNIVERSAL,
    COPY_CONFIG,
    EXPORT_CONFIG,
    MOVE,
    MOVE_UP,
    MOVE_DOWN,
    SHORTCUT,
}

private data class QrPayload(val title: String, val content: String)

private data class ProfileExport(val content: String, val fileName: String)

private enum class GroupCleanupAction {
    DELETE_UNAVAILABLE,
    REMOVE_DUPLICATES,
}

private data class GroupCleanup(val group: ProxyGroup, val action: GroupCleanupAction)
