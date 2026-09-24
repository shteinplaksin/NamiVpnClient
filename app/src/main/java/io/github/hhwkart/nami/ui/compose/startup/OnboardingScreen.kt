package io.github.hhwkart.nami.ui.compose.startup

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.hhwkart.nami.R
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,
    onGetStarted: () -> Unit,
    onFirstPageBack: () -> Unit,
) {
    val pages = listOf(
        OnboardingPage(
            Icons.Filled.CheckCircle,
            stringResource(R.string.onboarding_welcome_title),
            stringResource(R.string.onboarding_welcome_body),
        ),
        OnboardingPage(
            Icons.Filled.Lock,
            stringResource(R.string.onboarding_privacy_title),
            stringResource(R.string.onboarding_privacy_body),
        ),
        OnboardingPage(
            Icons.Filled.Settings,
            stringResource(R.string.onboarding_setup_title),
            stringResource(R.string.onboarding_setup_body),
        ),
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    BackHandler {
        if (pagerState.currentPage == 0) onFirstPageBack()
        else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text(stringResource(R.string.onboarding_skip)) }
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) { page ->
                val content = pages[page]
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.extraLarge),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            content.icon,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Text(content.title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        content.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { page ->
                    Box(
                        Modifier
                            .size(if (page == pagerState.currentPage) 24.dp else 8.dp, 8.dp)
                            .background(
                                if (page == pagerState.currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                MaterialTheme.shapes.small,
                            ),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            if (pagerState.currentPage < pages.lastIndex) {
                NamiLiquidButton(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.onboarding_next)) }
            } else {
                NamiLiquidButton(onClick = onGetStarted, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
        }
    }
}
