package io.github.hhwkart.nami.ui.compose.common

import android.os.Build
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.annotation.RequiresApi
import io.github.hhwkart.nami.ui.compose.style.LocalNamiVisualStyle
import io.github.hhwkart.nami.ui.compose.style.LiquidGlassTier
import java.util.function.Consumer
import kotlin.math.roundToInt

/**
 * Shared dialog chrome for Compose surfaces that must remain visually distinct
 * from the page behind them. The content and actions remain standard Material 3
 * slots so existing dialogs keep their behavior while sharing one surface.
 */
@Composable
fun NamiAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable (() -> Unit))? = null,
    icon: (@Composable (() -> Unit))? = null,
    title: (@Composable (() -> Unit))? = null,
    text: (@Composable (() -> Unit))? = null,
    properties: DialogProperties = DialogProperties(),
) {
    val shape = MaterialTheme.shapes.extraLarge
    val visualStyle = LocalNamiVisualStyle.current
    val supportsWindowBlur = rememberCrossWindowBlurEnabled()
    val useGlassBlur = visualStyle.isClearGlass &&
        visualStyle.effectiveTier in setOf(
            LiquidGlassTier.BLUR,
            LiquidGlassTier.BLUR_AND_LENS,
        ) && supportsWindowBlur
    val dialogSurface = MaterialTheme.colorScheme.surfaceContainerHigh
    val containerColor = when {
        !visualStyle.isClearGlass -> dialogSurface
        useGlassBlur -> dialogSurface.copy(alpha = 0.76f)
        else -> dialogSurface.copy(alpha = 0.92f)
    }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier
            .then(
                if (useGlassBlur) {
                    Modifier.dialogWindowBackgroundBlur(24.dp)
                } else {
                    Modifier
                },
            )
            .shadow(if (visualStyle.liquidEnabled) 24.dp else 12.dp, shape),
        icon = icon,
        title = title,
        text = text,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        properties = properties,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = if (visualStyle.liquidEnabled) 0.dp else 8.dp,
    )
}

/**
 * System window blur is safe for a Dialog because it is rendered by Android
 * across windows. The Backdrop library is deliberately not used here: Dialog
 * content has its own window and cannot safely sample the Activity layer.
 */
private fun Modifier.dialogWindowBackgroundBlur(radius: Dp): Modifier = composed {
    val view = LocalView.current
    val radiusPx = with(LocalDensity.current) { radius.toPx().roundToInt() }
    val windowCornerRadiusPx = with(LocalDensity.current) { 28.dp.toPx() }
    DisposableEffect(view, radiusPx) {
        val window = view.findDialogWindow()
        var originalBackground: Drawable? = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && window != null) {
            originalBackground = window.decorView.background
            // Compose 1.12.1 does not expose DialogProperties.windowShape yet.
            // A transparent rounded drawable supplies the system blur region's
            // outline while leaving the Compose dialog card itself untouched.
            window.setBackgroundDrawable(
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = windowCornerRadiusPx
                    setColor(AndroidColor.TRANSPARENT)
                },
            )
            window.setBackgroundBlurRadius(radiusPx)
        }
        onDispose {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                window?.setBackgroundBlurRadius(0)
                window?.setBackgroundDrawable(
                    originalBackground ?: ColorDrawable(AndroidColor.TRANSPARENT),
                )
            }
        }
    }
    this
}

private fun View.findDialogWindow(): Window? {
    var current: View? = this
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent as? View
    }
    return null
}

@Composable
private fun rememberCrossWindowBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    val windowManager = context.getSystemService(WindowManager::class.java) ?: return false
    return rememberCrossWindowBlurEnabled(windowManager)
}

@Composable
@RequiresApi(Build.VERSION_CODES.S)
private fun rememberCrossWindowBlurEnabled(windowManager: WindowManager): Boolean {
    val enabled = remember(windowManager) {
        mutableStateOf(windowManager.isCrossWindowBlurEnabled)
    }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled.value = it }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled.value
}

/**
 * Text input for dialogs. Dialog windows do not automatically focus their
 * first field, so explicitly request focus after the window is attached and
 * ask the IME to open. This keeps all editable dialogs consistent on Android
 * 7+ without affecting choice or confirmation dialogs.
 */
@Composable
fun NamiAutoFocusTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable (() -> Unit))? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        // AlertDialog is hosted in a separate window; wait until its first
        // frame so the requester is attached before asking for focus.
        withFrameNanos { }
        focusRequester.requestFocus()
        withFrameNanos { }
        keyboardController?.show()
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.focusRequester(focusRequester),
        label = label,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}
