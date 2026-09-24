package io.github.hhwkart.nami.ui.compose.common

import io.github.hhwkart.nami.ui.compose.style.NamiLiquidButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.hhwkart.nami.ui.compose.style.NamiCard

enum class UserFacingErrorKind {
    NETWORK,
    AUTHENTICATION,
    SUBSCRIPTION,
    VALIDATION,
    UNKNOWN,
}

data class UserFacingError(
    val kind: UserFacingErrorKind,
    val message: String,
    val hint: String? = null,
    val source: String? = null,
) {
    val displayText: String
        get() = buildString {
            append(message)
            hint?.takeIf { it.isNotBlank() }?.let {
                append("\n").append(it)
            }
            source?.takeIf { it.isNotBlank() }?.let {
                append("\nSource: ").append(it)
            }
        }

    companion object {
        fun fromThrowable(error: Throwable, source: String? = null): UserFacingError {
            val raw = error.message?.takeIf { it.isNotBlank() }
                ?: error.javaClass.simpleName
            val lower = raw.lowercase()
            return when {
                lower.contains("401") || lower.contains("403") ||
                    lower.contains("auth") || lower.contains("unauthor") -> UserFacingError(
                    kind = UserFacingErrorKind.AUTHENTICATION,
                    message = "Authentication failed: $raw",
                    hint = "Check the subscription credentials and try again.",
                    source = source,
                )
                lower.contains("timeout") || lower.contains("unknownhost") ||
                    lower.contains("connection") || lower.contains("network") -> UserFacingError(
                    kind = UserFacingErrorKind.NETWORK,
                    message = "Network request failed: $raw",
                    hint = "Check the connection and try again.",
                    source = source,
                )
                lower.contains("invalid") || lower.contains("malformed") ||
                    lower.contains("parse") -> UserFacingError(
                    kind = UserFacingErrorKind.VALIDATION,
                    message = "Validation failed: $raw",
                    hint = "Check the input and try again.",
                    source = source,
                )
                source != null -> UserFacingError(
                    kind = UserFacingErrorKind.SUBSCRIPTION,
                    message = "Subscription import failed: $raw",
                    hint = "Check the subscription URL and try again.",
                    source = source,
                )
                else -> UserFacingError(
                    kind = UserFacingErrorKind.UNKNOWN,
                    message = "Operation failed: $raw",
                    source = source,
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    NamiCard(
        modifier = modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (primaryLabel != null && onPrimary != null || secondaryLabel != null && onSecondary != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (primaryLabel != null && onPrimary != null) {
                        NamiLiquidButton(onClick = onPrimary) { Text(primaryLabel) }
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(onClick = onSecondary) { Text(secondaryLabel) }
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    hint: String = "Check the connection and try again.",
    onRetry: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    NamiCard(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        tint = MaterialTheme.colorScheme.errorContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Text("Something went wrong", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            }
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                onRetry?.let { NamiLiquidButton(onClick = it) { Text("Retry") } }
                onDismiss?.let { OutlinedButton(onClick = it) { Text("Dismiss") } }
            }
        }
    }
}

fun Throwable.toFriendlyUiMessage(context: String? = null): String {
    return UserFacingError.fromThrowable(this, context).displayText
}
