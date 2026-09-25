package io.github.hhwkart.nami.ui.compose.routing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.ui.compose.style.NamiGlassSurface
import io.github.hhwkart.nami.ui.compose.style.LocalNamiVisualStyle
import io.github.hhwkart.nami.ui.compose.style.NamiSwitch

@Composable
fun WebsiteBypassSection(
    state: RoutingUiState,
    viewModel: RoutingViewModel,
    onReloadRequired: () -> Unit,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
    if (LocalNamiVisualStyle.current.liquidEnabled) {
        NamiGlassSurface(modifier = modifier) {
            WebsiteBypassContent(state, viewModel, onReloadRequired)
        }
    } else {
        Card(
            modifier = modifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        ) {
            WebsiteBypassContent(state, viewModel, onReloadRequired)
        }
    }
}

@Composable
private fun WebsiteBypassContent(
    state: RoutingUiState,
    viewModel: RoutingViewModel,
    onReloadRequired: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            leadingContent = { Icon(Icons.Filled.Language, contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.website_bypass_title)) },
            supportingContent = {
                Text(
                    if (state.websiteBypassRequiresVpn) {
                        stringResource(R.string.website_bypass_requires_vpn)
                    } else {
                        stringResource(R.string.website_bypass_summary)
                    },
                )
            },
            trailingContent = {
                NamiSwitch(
                    checked = state.websiteBypassEnabled,
                    enabled = !state.websiteBypassRequiresVpn,
                    onCheckedChange = { viewModel.setWebsiteBypassEnabled(it, onReloadRequired) },
                )
            },
        )
        if (state.websiteBypassEnabled && !state.websiteBypassRequiresVpn) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val addDescription = stringResource(R.string.website_bypass_add)
                    OutlinedTextField(
                        value = state.websiteBypassInput,
                        onValueChange = viewModel::updateWebsiteBypassInput,
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text(stringResource(R.string.website_bypass_add_hint)) },
                    )
                    IconButton(
                        onClick = { viewModel.addWebsiteBypass(onReloadRequired) },
                        modifier = Modifier.semantics {
                            contentDescription = addDescription
                        },
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                    }
                }
                state.websiteBypassError?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                }
                state.websiteBypassDomains.forEach { host ->
                    val removeDescription = stringResource(
                        R.string.website_bypass_remove,
                        host,
                    )
                    ListItem(
                        headlineContent = { Text(host) },
                        trailingContent = {
                            IconButton(
                                onClick = { viewModel.removeWebsiteBypass(host, onReloadRequired) },
                                modifier = Modifier.semantics {
                                    contentDescription = removeDescription
                                },
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = null)
                            }
                        },
                    )
                }
            }
        } else if (state.websiteBypassError != null) {
            Text(
                state.websiteBypassError,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
