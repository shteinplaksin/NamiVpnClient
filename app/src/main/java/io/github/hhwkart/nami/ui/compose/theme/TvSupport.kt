package io.github.hhwkart.nami.ui.compose.theme

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val LocalIsTv = compositionLocalOf { false }

fun Context.isTvDevice(): Boolean {
    val uiMode = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

@Composable
fun Modifier.tvFocusable(
    shape: Shape = MaterialTheme.shapes.medium,
    makeFocusable: Boolean = false,
): Modifier {
    val isTv = LocalIsTv.current
    if (!isTv) return this
    var isFocused by remember { mutableStateOf(false) }
    val focusableMod = if (makeFocusable) Modifier.focusable() else Modifier
    return this
        .onFocusChanged { isFocused = it.isFocused }
        .then(focusableMod)
        .then(
            if (isFocused) {
                Modifier.border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = shape,
                )
            } else {
                Modifier
            }
        )
}
