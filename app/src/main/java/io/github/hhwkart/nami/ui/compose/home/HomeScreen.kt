@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package io.github.hhwkart.nami.ui.compose.home


import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import io.github.hhwkart.nami.ui.compose.theme.tvFocusable
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.aidl.SpeedDisplayData
import io.github.hhwkart.nami.bg.BaseService
import io.github.hhwkart.nami.ktx.app
import io.github.hhwkart.nami.ui.compose.style.NamiCard

@Composable
fun HomeScreen(bottomBarPadding: Dp = 0.dp, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val layoutDirection = LocalLayoutDirection.current

    Scaffold { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp + bottomBarPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ConnectionStatusCard(
                    state = state.state,
                    profileName = state.profileName,
                    serverLocation = state.serverLocation,
                    sessionDuration = state.sessionDuration,
                    statusText = state.statusText,
                )
            }
            item {
                TestSpeedButton(
                    isTesting = state.isTesting,
                    result = state.testResult,
                    onClick = viewModel::testConnection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                ConnectFab(
                    state = state.state,
                    onClick = viewModel::onToggleConnection,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                SpeedGraphCard(
                    speed = state.speed,
                    history = state.speedHistory,
                )
            }
            item {
                TrafficStatsCard(
                    speed = state.speed,
                    sessionDuration = state.sessionDuration,
                )
            }
            if (state.recentProfiles.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.recent_profiles),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                items(state.recentProfiles, key = { it.id }) { profile ->
                    RecentProfileRow(profile = profile, onClick = { viewModel.onSelectProfile(profile.id) })
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    state: BaseService.State,
    profileName: String,
    serverLocation: String,
    sessionDuration: String,
    statusText: String,
) {
    NamiCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StateIcon(state = state)
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        BaseService.State.Connected -> stringResource(R.string.vpn_connected)
                        BaseService.State.Connecting -> stringResource(R.string.connecting)
                        BaseService.State.Stopping -> stringResource(R.string.stopping)
                        else -> stringResource(R.string.not_connected)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                if (profileName.isNotBlank()) {
                    Text(profileName, style = MaterialTheme.typography.bodyLarge)
                }
                if (serverLocation.isNotBlank()) {
                    Text(
                        serverLocation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (sessionDuration.isNotBlank()) {
                    Text(
                        sessionDuration,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (statusText.isNotBlank()) {
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StateIcon(state: BaseService.State) {
    val icon: ImageVector
    val tint: androidx.compose.ui.graphics.Color
    when (state) {
        BaseService.State.Connected -> {
            icon = Icons.Filled.Check
            tint = MaterialTheme.colorScheme.primary
        }

        BaseService.State.Connecting, BaseService.State.Stopping -> {
            icon = Icons.Filled.PlayArrow
            tint = MaterialTheme.colorScheme.tertiary
        }

        else -> {
            icon = Icons.Filled.Close
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Box(contentAlignment = Alignment.Center) {
        if (state == BaseService.State.Connecting || state == BaseService.State.Stopping) {
            CircularProgressIndicator(
                modifier = Modifier.size(40.dp),
                color = tint,
            )
        }
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(if (state == BaseService.State.Connecting || state == BaseService.State.Stopping) 20.dp else 40.dp),
        )
    }
}

@Composable
private fun ConnectFab(
    state: BaseService.State,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (label, icon) = when (state) {
        BaseService.State.Connected -> stringResource(R.string.action_disconnect) to Icons.Filled.Close
        BaseService.State.Connecting -> stringResource(R.string.connecting) to Icons.Filled.Close
        BaseService.State.Stopping -> stringResource(R.string.stopping) to Icons.Filled.Close
        else -> stringResource(R.string.action_connect) to Icons.Filled.PlayArrow
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.tvFocusable(shape = MaterialTheme.shapes.large),
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
    )
}

@Composable
private fun TestSpeedButton(
    isTesting: Boolean,
    result: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !isTesting,
        modifier = modifier.height(48.dp),
        shape = MaterialTheme.shapes.large,
    ) {
        if (isTesting) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.connection_testing))
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            val text = if (result != null) {
                stringResource(R.string.test_result_prefix, result)
            } else {
                stringResource(R.string.check_connection)
            }
            Text(text)
        }
    }
}

@Composable
private fun SpeedGraphCard(speed: SpeedDisplayData, history: List<Pair<Long, Long>>) {
    val downColor = MaterialTheme.colorScheme.primary
    val upColor = MaterialTheme.colorScheme.tertiary
    NamiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val points = if (history.size < 60) {
                List(60 - history.size) { 0L to 0L } + history
            } else {
                history.takeLast(60)
            }
            val targetMax = points.maxOfOrNull { maxOf(it.first, it.second) }
                ?.coerceAtLeast(1024L)?.toFloat() ?: 1024f
            val animatedMax by animateFloatAsState(
                targetValue = targetMax,
                animationSpec = tween(durationMillis = 350),
                label = "speed graph scale",
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                SpeedLine("↓", downColor, speed.rxRateProxy)
                SpeedLine("↑", upColor, speed.txRateProxy)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            ) {
                if (points.isEmpty()) return@Canvas
                val maxVal = animatedMax
                val stepX = size.width / (points.size - 1).coerceAtLeast(1).toFloat()

                fun buildPath(selector: (Pair<Long, Long>) -> Long): Pair<Path, Path> {
                    val strokePath = Path()
                    val fillPath = Path()
                    val coords = points.mapIndexed { i, pair ->
                        val value = selector(pair)
                        val x = i * stepX
                        val y = size.height - (value.toFloat() / maxVal) * (size.height - 8f) - 4f
                        Offset(x, y.coerceIn(0f, size.height))
                    }
                    if (coords.isEmpty()) return strokePath to fillPath

                    strokePath.moveTo(coords[0].x, coords[0].y)
                    fillPath.moveTo(coords[0].x, size.height)
                    fillPath.lineTo(coords[0].x, coords[0].y)

                    for (i in 1 until coords.size) {
                        val prev = coords[i - 1]
                        val curr = coords[i]
                        val cx = (prev.x + curr.x) / 2f
                        strokePath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                        fillPath.cubicTo(cx, prev.y, cx, curr.y, curr.x, curr.y)
                    }
                    fillPath.lineTo(coords.last().x, size.height)
                    fillPath.close()
                    return strokePath to fillPath
                }

                val (rxStroke, rxFill) = buildPath { it.first }
                val (txStroke, txFill) = buildPath { it.second }

                // Wave gradient fills
                drawPath(
                    rxFill,
                    brush = Brush.verticalGradient(
                        listOf(downColor.copy(alpha = 0.35f), downColor.copy(alpha = 0.02f))
                    )
                )
                drawPath(
                    txFill,
                    brush = Brush.verticalGradient(
                        listOf(upColor.copy(alpha = 0.25f), upColor.copy(alpha = 0.02f))
                    )
                )

                // Smooth wave lines
                drawPath(
                    rxStroke,
                    color = downColor,
                    style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawPath(
                    txStroke,
                    color = upColor,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                drawLine(
                    downColor.copy(alpha = 0.2f),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    strokeWidth = 1f
                )
            }
        }
    }
}

@Composable
private fun SpeedLine(arrow: String, color: androidx.compose.ui.graphics.Color, rate: Long) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(arrow, color = color, style = MaterialTheme.typography.bodyMedium)
        Text(
            Formatter.formatFileSize(context, rate) + "/s",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrafficStatsCard(speed: SpeedDisplayData, sessionDuration: String) {
    val context = LocalContext.current
    NamiCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.traffic_statistics),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.traffic_session_totals,
                    Formatter.formatFileSize(context, speed.txTotal),
                    Formatter.formatFileSize(context, speed.rxTotal),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (sessionDuration.isNotBlank()) {
                Text(
                    stringResource(R.string.session_duration, sessionDuration),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun RecentProfileRow(profile: RecentProfileUi, onClick: () -> Unit) {
    NamiCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .tvFocusable(shape = MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    profile.type,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile.selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
