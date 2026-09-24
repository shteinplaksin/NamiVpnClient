package io.github.hhwkart.nami.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import io.github.hhwkart.nami.SagerNet
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.database.ProfileManager
import io.github.hhwkart.nami.database.SagerDatabase
import io.github.hhwkart.nami.ktx.runOnMainDispatcher
import io.github.hhwkart.nami.ui.compose.theme.NamiTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SwitchActivity : ThemedActivity() {

    override val isDialog = true

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            NamiTheme {
                ProfileSwitchScreen(
                    selectedId = DataStore.selectedProxy,
                    onSelected = ::returnProfile,
                    onBack = ::finish,
                )
            }
        }
    }

    private fun returnProfile(profileId: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = profileId
        runOnMainDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(profileId, true)
        }
        SagerNet.reloadService()
        finish()
    }
}

private data class SwitchProfile(val id: Long, val name: String, val type: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitchScreen(
    selectedId: Long,
    onSelected: (Long) -> Unit,
    onBack: () -> Unit,
) {
    var profiles by remember { mutableStateOf<List<SwitchProfile>?>(null) }
    LaunchedEffect(Unit) {
        profiles = withContext(Dispatchers.IO) {
            SagerDatabase.proxyDao.getAll()
                .sortedWith(compareBy({ it.groupId }, { it.userOrder }))
                .map { SwitchProfile(it.id, it.displayName(), it.displayType()) }
        }
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Switch profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                        )
                    }

                },
            )
        },
    ) { padding ->
        val values = profiles
        if (values == null) {
            androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                items(values, key = { it.id }) { profile ->
                    ListItem(
                        headlineContent = { Text(profile.name) },
                        supportingContent = { Text(profile.type) },
                        trailingContent = {
                            RadioButton(
                                selected = profile.id == selectedId,
                                onClick = { onSelected(profile.id) },
                            )
                        },
                        modifier = Modifier.clickable { onSelected(profile.id) },
                    )
                }
            }
        }
    }
}
