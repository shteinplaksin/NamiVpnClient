package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.ui.compose.common.ErrorState
import io.github.hhwkart.nami.ui.compose.common.UserFacingError
import io.github.hhwkart.nami.ui.compose.common.UserFacingErrorKind
import io.github.hhwkart.nami.ui.compose.routing.RoutingPreset
import io.github.hhwkart.nami.ui.compose.style.NamiCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect

enum class QuickSetupSource {
    SUBSCRIPTION,
    QR,
    MANUAL,
}

data class QuickSetupResult(
    val completed: Boolean,
    val resultGroupId: Long?,
    val source: QuickSetupSource,
    val error: UserFacingError? = null,
    val routingPreset: String = RoutingPreset.GLOBAL.id,
)

object QuickSetupBus {
    private val _results = MutableStateFlow<QuickSetupResult?>(null)
    val results = _results

    fun publish(result: QuickSetupResult) {
        _results.value = result
    }

    fun clear() {
        _results.value = null
    }
}

private enum class QuickSetupStep {
    METHOD,
    INPUT,
    PRESET,
    DONE,
}

private data class SetupMethod(
    val source: QuickSetupSource,
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSetupScreen(
    onScanQr: () -> Unit,
    onManual: (Long) -> Unit,
    onSubscription: (String, (QuickSetupResult) -> Unit) -> Unit,
    onApplyPreset: (RoutingPreset, (Result<Unit>) -> Unit) -> Unit,
    onDone: (QuickSetupResult) -> Unit,
    onCancel: () -> Unit,
) {
    var step by rememberSaveable { mutableStateOf(QuickSetupStep.METHOD) }
    var method by rememberSaveable { mutableStateOf<QuickSetupSource?>(null) }
    var url by rememberSaveable { mutableStateOf("") }
    var groupId by rememberSaveable { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<UserFacingError?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    var applyingPreset by rememberSaveable { mutableStateOf(false) }
    var selectedPreset by rememberSaveable { mutableStateOf(RoutingPreset.GLOBAL.id) }

    LaunchedEffect(Unit) {
        QuickSetupBus.results.collectLatest { result ->
            result ?: return@collectLatest
            method = result.source
            groupId = result.resultGroupId
            error = result.error
            if (result.completed) step = QuickSetupStep.PRESET
            QuickSetupBus.clear()
        }
    }

    val methods = listOf(
        SetupMethod(
            QuickSetupSource.SUBSCRIPTION,
            Icons.Filled.Share,
            stringResource(R.string.quick_setup_subscription),
            stringResource(R.string.quick_setup_subscription_summary),
        ),
        SetupMethod(
            QuickSetupSource.QR,
            Icons.Filled.Place,
            stringResource(R.string.quick_setup_qr),
            stringResource(R.string.quick_setup_qr_summary),
        ),
        SetupMethod(
            QuickSetupSource.MANUAL,
            Icons.Filled.Settings,
            stringResource(R.string.quick_setup_manual),
            stringResource(R.string.quick_setup_manual_summary),
        ),
    )

    fun finish(source: QuickSetupSource) {
        if (applyingPreset) return
        val preset = RoutingPreset.entries.firstOrNull { it.id == selectedPreset }
            ?: RoutingPreset.GLOBAL
        applyingPreset = true
        error = null
        onApplyPreset(preset) { result ->
            applyingPreset = false
            result.onSuccess {
                onDone(
                    QuickSetupResult(
                        completed = true,
                        resultGroupId = groupId,
                        source = source,
                        routingPreset = preset.id,
                    ),
                )
            }.onFailure { throwable ->
                error = UserFacingError.fromThrowable(throwable)
            }
        }
    }

    fun submitSubscription() {
        val value = url.trim()
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            error = UserFacingError(
                kind = UserFacingErrorKind.VALIDATION,
                message = "Enter a valid HTTP or HTTPS subscription URL.",
                hint = "The URL must start with http:// or https://.",
            )
            return
        }
        busy = true
        error = null
        onSubscription(value) { result ->
            busy = false
            groupId = result.resultGroupId
            error = result.error
            if (result.completed) step = QuickSetupStep.PRESET
        }
    }

    BackHandler {
        if (busy || applyingPreset) return@BackHandler
        when (step) {
            QuickSetupStep.METHOD -> onCancel()
            QuickSetupStep.INPUT -> step = QuickSetupStep.METHOD
            QuickSetupStep.PRESET -> step = QuickSetupStep.INPUT
            QuickSetupStep.DONE -> onCancel()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.quick_setup_title)) },
                navigationIcon = {
                    TextButton(onClick = onCancel, enabled = !busy && !applyingPreset) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    when (step) {
                        QuickSetupStep.METHOD -> stringResource(R.string.quick_setup_choose_method)
                        QuickSetupStep.INPUT -> stringResource(R.string.quick_setup_add_source)
                        QuickSetupStep.PRESET -> stringResource(R.string.quick_setup_choose_routing)
                        QuickSetupStep.DONE -> stringResource(R.string.quick_setup_ready)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            error?.let { issue ->
                item {
                    ErrorState(
                        message = issue.message,
                        hint = buildString {
                            issue.hint?.takeIf { it.isNotBlank() }?.let(::append)
                            issue.source?.takeIf { it.isNotBlank() }?.let {
                                if (isNotEmpty()) append("\n")
                                append("Source: ").append(it)
                            }
                        }.ifBlank { "Check the input and try again." },
                        onRetry = if (method == QuickSetupSource.SUBSCRIPTION && !busy) {
                            ::submitSubscription
                        } else {
                            null
                        },
                    )
                }
            }
            when (step) {
                QuickSetupStep.METHOD -> {
                    items(methods, key = { it.source }) { option ->
                        NamiCard(
                            onClick = {
                                error = null
                                method = option.source
                                when (option.source) {
                                    QuickSetupSource.QR -> {
                                        groupId = DataStore.selectedGroupForImport()
                                        step = QuickSetupStep.PRESET
                                        onScanQr()
                                    }
                                    QuickSetupSource.MANUAL -> {
                                        val selected = DataStore.selectedGroupForImport()
                                        groupId = selected
                                        step = QuickSetupStep.PRESET
                                        onManual(selected)
                                    }
                                    QuickSetupSource.SUBSCRIPTION -> step = QuickSetupStep.INPUT
                                }
                            },
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(option.icon, contentDescription = null)
                                Column(Modifier.weight(1f)) {
                                    Text(option.title, style = MaterialTheme.typography.titleMedium)
                                    Text(option.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                QuickSetupStep.INPUT -> {
                    item {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.quick_setup_subscription_url)) },
                            singleLine = true,
                            isError = error != null,
                        )
                    }
                    item {
                        NamiLiquidButton(
                            onClick = ::submitSubscription,
                            enabled = !busy,
                        ) {
                            if (busy) CircularProgressIndicator(strokeWidth = 2.dp)
                            else Text(stringResource(R.string.quick_setup_continue))
                        }
                    }
                }
                QuickSetupStep.PRESET -> {
                    items(RoutingPreset.entries, key = { it.id }) { preset ->
                        NamiCard(
                            onClick = {
                                selectedPreset = preset.id
                            },
                            tint = if (selectedPreset == preset.id) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            },
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(preset.title, style = MaterialTheme.typography.titleMedium)
                                Text(preset.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item {
                        NamiLiquidButton(
                            onClick = { finish(method ?: QuickSetupSource.SUBSCRIPTION) },
                            enabled = !applyingPreset,
                        ) {
                            if (applyingPreset) CircularProgressIndicator(strokeWidth = 2.dp)
                            else Text(stringResource(R.string.quick_setup_done))
                        }
                    }
                }
                QuickSetupStep.DONE -> {
                    item {
                        NamiLiquidButton(
                            onClick = { finish(method ?: QuickSetupSource.SUBSCRIPTION) },
                            enabled = !applyingPreset,
                        ) {
                            Text(stringResource(R.string.quick_setup_open_profiles))
                        }
                    }
                }
            }
        }
    }
}
