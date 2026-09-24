package io.github.hhwkart.nami.ui.compose.scanner

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.zxing.Result
import com.king.zxing.CameraScan
import com.king.zxing.DefaultCameraScan
import com.king.zxing.analyze.QRCodeAnalyzer

/**
 * Compose QR scanner used by MainActivity destinations. Camera ownership remains
 * in zxing-lite; Compose owns permission, lifecycle, image import, and results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrScannerScreen(
    onBack: () -> Unit,
    onSubscriptionUri: (Uri) -> Unit,
    viewModel: QrScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.importImages(context.contentResolver, uris)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.setCameraPermission(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        viewModel.setCameraPermission(granted)
        if (!granted && !state.permissionRequested) {
            viewModel.markPermissionRequested()
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(state.pendingSubscriptionUri) {
        state.pendingSubscriptionUri?.let { value ->
            viewModel.consumeSubscriptionUri()
            onSubscriptionUri(Uri.parse(value))
        }
    }
    LaunchedEffect(state.message, state.error) {
        val message = state.message ?: state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    val previewView = remember { PreviewView(context) }
    val cameraScan = remember(previewView) {
        DefaultCameraScan(activity, previewView).apply {
            setAnalyzer(QRCodeAnalyzer())
            setNeedAutoZoom(true)
            setOnScanResultCallback(object : CameraScan.OnScanResultCallback {
                override fun onScanResultCallback(result: Result?): Boolean {
                    viewModel.onCameraResult(result?.text)
                    return true
                }
            })
        }
    }

    DisposableEffect(cameraScan, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME &&
                viewModel.uiState.value.cameraPermissionGranted &&
                !viewModel.uiState.value.finished
            ) {
                cameraScan.startCamera()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            state.cameraPermissionGranted && !state.finished
        ) {
            cameraScan.startCamera()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            cameraScan.release()
        }
    }

    LaunchedEffect(state.cameraPermissionGranted, state.finished) {
        if (state.cameraPermissionGranted && !state.finished &&
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        ) {
            cameraScan.startCamera()
        }
    }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan QR code") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { imageLauncher.launch("image/*") }) {
                        Text("Images")
                    }
                    TextButton(
                        enabled = state.cameraPermissionGranted && !state.finished,
                        onClick = {
                            val enabled = !state.torchEnabled
                            cameraScan.enableTorch(enabled)
                            viewModel.setTorchEnabled(enabled)
                        },
                    ) { Text(if (state.torchEnabled) "Torch off" else "Torch") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
            if (!state.permissionKnown || state.busy) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.busy) CircularProgressIndicator()
                    else CircularProgressIndicator()
                }
            } else if (!state.cameraPermissionGranted) {
                PermissionNotice(
                    onRequest = {
                        viewModel.markPermissionRequested()
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                )
            } else if (state.finished) {
                CompletionNotice(
                    importedCount = state.importedCount,
                    onBack = onBack,
                )
            }
            if (state.importedCount > 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Imported ${state.importedCount} profile(s)",
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionNotice(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Camera permission is required to scan QR codes.")
        NamiLiquidButton(onClick = onRequest, modifier = Modifier.padding(top = 16.dp)) {
            Text("Allow camera")
        }
    }
}

@Composable
private fun CompletionNotice(
    importedCount: Int,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            if (importedCount > 0) "QR import complete" else "QR code processed",
            style = MaterialTheme.typography.titleLarge,
        )
        NamiLiquidButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
            Text("Done")
        }
    }
}
