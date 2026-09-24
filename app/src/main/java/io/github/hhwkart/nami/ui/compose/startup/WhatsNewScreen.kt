package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hhwkart.nami.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsNewScreen(
    onOpened: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    LaunchedEffect(Unit) { onOpened() }
    BackHandler(onBack = onDismiss)
    val changes = listOf(
        stringResource(R.string.whats_new_onboarding),
        stringResource(R.string.whats_new_quick_setup),
        stringResource(R.string.whats_new_routing),
        stringResource(R.string.whats_new_empty_states),
    )
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.whats_new_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item { Text(stringResource(R.string.whats_new_intro), style = MaterialTheme.typography.bodyLarge) }
                items(changes) { change ->
                    Text("• $change", style = MaterialTheme.typography.bodyLarge)
                }
            }
            NamiLiquidButton(
                onClick = onDismiss,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            ) { Text(stringResource(R.string.whats_new_done)) }
        }
    }
}
