package io.github.hhwkart.nami.ui.compose.tools

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StunTestScreen(
    onBack: () -> Unit,
    viewModel: StunTestViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NAT behaviour discovery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val safePadding = PaddingValues(
            start = padding.calculateStartPadding(layoutDirection),
            top = padding.calculateTopPadding(),
            end = padding.calculateEndPadding(layoutDirection),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(safePadding)
                .consumeWindowInsets(safePadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.server,
                onValueChange = viewModel::setServer,
                label = { Text("STUN server") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Determine the client's NAT mapping behaviour and NAT filtering behaviour.")
            NamiLiquidButton(
                onClick = viewModel::test,
                enabled = !state.testing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.testing) CircularProgressIndicator(strokeWidth = 2.dp)
                else Text("Start")
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            if (state.result.isNotBlank()) {
                Text("Result", style = MaterialTheme.typography.titleMedium)
                Text(state.result)
            }
        }
    }
}
