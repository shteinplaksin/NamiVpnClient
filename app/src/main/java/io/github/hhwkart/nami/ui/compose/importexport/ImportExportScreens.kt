package io.github.hhwkart.nami.ui.compose.importexport

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** The complete manual-profile menu formerly provided by add_profile_menu.xml. */
enum class ManualProfileType(
    val label: String,
    val routeKey: String,
) {
    SOCKS("SOCKS", "socks"),
    HTTP("HTTP", "http"),
    SHADOWSOCKS("Shadowsocks", "shadowsocks"),
    VMESS("VMess", "vmess"),
    VLESS("VLESS", "vless"),
    TROJAN("Trojan", "trojan"),
    TROJAN_GO("Trojan-Go", "trojan-go"),
    MIERU("Mieru", "mieru"),
    NAIVE("NaïveProxy", "naive"),
    HYSTERIA("Hysteria", "hysteria"),
    TUIC("TUIC", "tuic"),
    SHADOWTLS("ShadowTLS", "shadowtls"),
    ANYTLS("AnyTLS", "anytls"),
    SSH("SSH", "ssh"),
    WIREGUARD("WireGuard", "wireguard"),
    CUSTOM_CONFIG("Custom config", "config"),
    CHAIN("Proxy chain", "chain"),
}

/**
 * Compose replacement for the legacy add-profile chooser.
 *
 * The callbacks deliberately remain host-owned: they can launch the existing scanner,
 * ActivityResult file pickers, clipboard parser, or the unified protocol editor without
 * changing import semantics in this UI-only migration step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddImportScreen(
    onScanQr: () -> Unit,
    onImportClipboard: () -> Unit,
    onImportFile: () -> Unit,
    onCreateProfile: (ManualProfileType) -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    onBack?.let { BackHandler(onBack = it) }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Add profile") },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Import", style = MaterialTheme.typography.titleMedium) }
            item {
                ImportActionButton(
                    text = "Scan QR code",
                    contentDescription = "Scan a profile QR code",
                    onClick = onScanQr,
                )
            }
            item {
                ImportActionButton(
                    text = "Import from clipboard",
                    contentDescription = "Import profiles from clipboard",
                    onClick = onImportClipboard,
                )
            }
            item {
                ImportActionButton(
                    text = "Import from file",
                    contentDescription = "Import profiles from a file",
                    onClick = onImportFile,
                )
            }
            item {
                Text(
                    "Manual profile",
                    modifier = Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            items(ManualProfileType.entries, key = { it.routeKey }) { type ->
                OutlinedButton(
                    onClick = { onCreateProfile(type) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Create ${type.label} profile" },
                ) {
                    Text(type.label)
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ImportActionButton(
    text: String,
    contentDescription: String,
    onClick: () -> Unit,
) {
    NamiLiquidButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { this.contentDescription = contentDescription },
    ) { Text(text) }
}

/** Independent selection flags for the three backup payload sections. */
data class BackupSelection(
    val configurations: Boolean = true,
    val rules: Boolean = true,
    val settings: Boolean = true,
)

/**
 * Compose launcher for backup/restore. Export/share/import/reset operations are delegated
 * to host callbacks so existing ActivityResult and destructive-operation safeguards remain
 * authoritative until the data layer is migrated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onExport: (BackupSelection) -> Unit,
    onShare: (BackupSelection) -> Unit,
    onImportFile: () -> Unit,
    onResetSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    onBack?.let { BackHandler(onBack = it) }

    var selection by remember { mutableStateOf(BackupSelection()) }
    var showResetConfirmation by remember { mutableStateOf(false) }

    if (showResetConfirmation) {
        NamiAlertDialog(
            onDismissRequest = { showResetConfirmation = false },
            title = { Text("Reset settings?") },
            text = { Text("All saved settings will be cleared and the app will restart.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirmation = false
                    onResetSettings()
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmation = false }) { Text("Cancel") }
            },
        )
    }

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Backup and restore") },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = { showResetConfirmation = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Reset settings") }
            BackupCheckRow(
                label = "Groups and configurations",
                checked = selection.configurations,
                onCheckedChange = { selection = selection.copy(configurations = it) },
            )
            BackupCheckRow(
                label = "Rules",
                checked = selection.rules,
                onCheckedChange = { selection = selection.copy(rules = it) },
            )
            BackupCheckRow(
                label = "Settings",
                checked = selection.settings,
                onCheckedChange = { selection = selection.copy(settings = it) },
            )
            Spacer(Modifier.height(8.dp))
            NamiLiquidButton(
                onClick = { onExport(selection) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export") }
            NamiLiquidButton(
                onClick = { onShare(selection) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Share") }
            Text(
                "Backups contain profiles, groups, routing rules, and selected settings.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = onImportFile,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import from file") }
        }
    }
}

@Composable
private fun BackupCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label)
    }
}
