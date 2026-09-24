package io.github.hhwkart.nami.ui.compose.tools

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import io.github.hhwkart.nami.ui.compose.common.NamiAlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssetsScreen(
    onBack: () -> Unit,
    viewModel: AssetsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    var deleteTarget by remember { mutableStateOf<AssetItem?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.importAsset(context.contentResolver, it) }
    }
    LaunchedEffect(Unit) {
        viewModel.initialize(context.getExternalFilesDir(null) ?: context.filesDir)
    }

    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route assets") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::reload) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    TextButton(onClick = { launcher.launch("*/*") }) {
                        Text("Import")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(
                    start = padding.calculateStartPadding(layoutDirection),
                    top = padding.calculateTopPadding(),
                    end = padding.calculateEndPadding(layoutDirection),
                ),
        ) {
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            state.message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp,
                    top = 8.dp,
                    end = 8.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.assets, key = { it.file.absolutePath }) { asset ->
                    AssetCard(
                        asset = asset,
                        onUpdate = { viewModel.updateAsset(asset) },
                        onDelete = { deleteTarget = asset },
                    )
                }
            }
        }
    }

    deleteTarget?.let { asset ->
        NamiAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete ${asset.name}?") },
            text = { Text("The local route asset will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    deleteTarget = null
                    viewModel.deleteAsset(asset.file)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun AssetCard(
    asset: AssetItem,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    NamiCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(asset.name, style = MaterialTheme.typography.titleMedium)
                    Text(asset.status, style = MaterialTheme.typography.bodySmall)
                }
                if (asset.canUpdate) {
                    IconButton(onClick = onUpdate, enabled = !asset.updating) {
                        if (asset.updating) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Refresh, contentDescription = "Update")
                    }
                } else {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}
