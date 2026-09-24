package io.github.hhwkart.nami.ui.compose.importexport

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog

/** Functional add/import screen backed by the existing RawUpdater/ProfileManager pipeline. */
@Composable
fun FunctionalAddImportScreen(
    onScanQr: () -> Unit,
    onSubscriptionUri: (Uri) -> Unit,
    onCreateProfile: (ManualProfileType) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importFile(context.contentResolver, it) } }

    LaunchedEffect(viewModel) {
        viewModel.subscriptionUris.collectLatest(onSubscriptionUri)
    }

    Box(modifier = modifier.fillMaxSize()) {
        AddImportScreen(
            onScanQr = onScanQr,
            onImportClipboard = viewModel::importClipboard,
            onImportFile = { fileLauncher.launch("*/*") },
            onCreateProfile = onCreateProfile,
            onBack = onBack,
        )
        ImportStatusOverlay(
            busy = state.busy,
            message = state.message,
            error = state.error,
            onDismiss = viewModel::consumeMessage,
        )
    }
}

/** Fully functional backup launcher with Compose ActivityResult contracts and import selection. */
@Composable
fun FunctionalBackupRestoreScreen(
    onResetSettings: () -> Unit,
    onStopService: () -> Unit,
    onRestartAfterImport: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    viewModel: BackupRestoreViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExportContent
        pendingExportContent = null
        if (uri != null && content != null) {
            viewModel.writeExport(context.contentResolver, uri, content)
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.importFile(context.contentResolver, it) } }

    LaunchedEffect(state.exportContent) {
        state.exportContent?.let { content ->
            pendingExportContent = content
            viewModel.consumeExportContent()
            exportLauncher.launch("nami_backup_${System.currentTimeMillis()}.json")
        }
    }
    LaunchedEffect(state.shareUri) {
        state.shareUri?.let { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, uri)
            }
            context.startActivity(Intent.createChooser(send, "Share backup"))
            viewModel.consumeShareUri()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        BackupRestoreScreen(
            onExport = viewModel::prepareExport,
            onShare = { viewModel.prepareShare(context, it) },
            onImportFile = { importLauncher.launch("*/*") },
            onResetSettings = onResetSettings,
            onBack = onBack,
        )
        ImportStatusOverlay(
            busy = state.busy,
            message = state.message,
            error = state.error,
            onDismiss = viewModel::consumeMessage,
        )
        state.pendingImport?.let { pending ->
            BackupImportDialog(
                pending = pending,
                onUpdate = viewModel::updateImportSelection,
                onCancel = viewModel::cancelImport,
                onApply = {
                    viewModel.applyImport(onStopService, onRestartAfterImport)
                },
            )
        }
    }
}

@Composable
private fun BackupImportDialog(
    pending: BackupImportSelection,
    onUpdate: ((BackupImportSelection) -> BackupImportSelection) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    NamiAlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Import backup") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (pending.hasConfigurations) {
                    BackupImportCheck("Groups and configurations", pending.configurations) {
                        onUpdate { it.copy(configurations = !it.configurations) }
                    }
                }
                if (pending.hasRules) {
                    BackupImportCheck("Rules", pending.rules) {
                        onUpdate { it.copy(rules = !it.rules) }
                    }
                }
                if (pending.hasSettings) {
                    BackupImportCheck("Settings", pending.settings) {
                        onUpdate { it.copy(settings = !it.settings) }
                    }
                }
                Text(
                    "Selected data replaces the corresponding local data.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onApply) { Text("Import") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

@Composable
private fun BackupImportCheck(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(label)
    }
}

@Composable
private fun ImportStatusOverlay(
    busy: Boolean,
    message: String?,
    error: String?,
    onDismiss: () -> Unit,
) {
    if (busy) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    }
    if (message != null || error != null) {
        NamiAlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (error != null) "Import error" else "Import") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(message ?: error.orEmpty())
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("OK") }
            },
        )
    }
}
