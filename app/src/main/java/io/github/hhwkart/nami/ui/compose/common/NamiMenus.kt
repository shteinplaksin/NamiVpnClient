package io.github.hhwkart.nami.ui.compose.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.hhwkart.nami.ui.compose.style.LocalNamiVisualStyle

/**
 * Shared styling for the Compose popup menus. Popup uses a separate Android
 * window, so it must not sample the Activity Backdrop layer. Liquid Glass uses
 * a conservative translucent tint and outline; Standard and Classic delegate
 * to Material defaults unchanged.
 */
@Composable
fun NamiDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!LocalNamiVisualStyle.current.isClearGlass) {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier,
            content = content,
        )
        return
    }

    val colors = MaterialTheme.colorScheme
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        containerColor = colors.surfaceContainerHigh.copy(alpha = 0.9f),
        tonalElevation = 0.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, colors.onSurface.copy(alpha = 0.18f)),
        content = content,
    )
}
