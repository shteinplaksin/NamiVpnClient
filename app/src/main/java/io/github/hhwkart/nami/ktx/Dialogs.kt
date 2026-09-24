package io.github.hhwkart.nami.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.annotation.RequiresApi
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.hhwkart.nami.R
import io.github.hhwkart.nami.database.DataStore
import io.github.hhwkart.nami.ui.compose.style.GlassSurfaceRole
import io.github.hhwkart.nami.ui.compose.style.LiquidGlassPolicy
import io.github.hhwkart.nami.ui.compose.style.LiquidGlassTier
import io.github.hhwkart.nami.ui.compose.style.NamiBackdropCapabilities
import java.util.function.Consumer
import kotlin.math.roundToInt

fun Context.alert(text: String): AlertDialog {
    return MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .create()
        .applyNamiLiquidGlassBlur(this)
}

/** Builds and displays an app-owned Material dialog with the Liquid Glass blur treatment when supported. */
fun MaterialAlertDialogBuilder.showWithNamiLiquidGlassBlur(context: Context): AlertDialog =
    create().applyNamiLiquidGlassBlur(context).also { it.show() }

/**
 * Enables whole-window system blur behind this legacy Material dialog only
 * when Liquid Glass quality policy and Android's current cross-window blur
 * capability allow it. Standard/Classic dialogs are left untouched.
 */
fun AlertDialog.applyNamiLiquidGlassBlur(context: Context): AlertDialog {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
    val activity = context.findActivity() ?: return this
    val windowManager = context.getSystemService(WindowManager::class.java) ?: return this
    val detected = NamiBackdropCapabilities.detect(context, activity.window.decorView)
    if (!detected.rendererAvailable || !detected.canUseBlur) return this

    // Treat runtime blur availability separately from renderer and quality
    // eligibility so an open dialog can respond if Android toggles window blur.
    val policyCapabilities = detected.copy(crossWindowBlurSupported = true)
    val style = LiquidGlassPolicy.resolveFor(
        context = context,
        capabilities = policyCapabilities,
        surfaceRole = GlassSurfaceRole.DIALOG,
    )
    val blurTier = style.effectiveTier == LiquidGlassTier.BLUR ||
        style.effectiveTier == LiquidGlassTier.BLUR_AND_LENS
    if (!style.isClearGlass || !blurTier) {
        return this
    }

    val radiusPx = (20f * activity.resources.displayMetrics.density).roundToInt()
    val dialogWindow = window ?: return this
    NativeDialogBlurAttachment(windowManager, dialogWindow, radiusPx).attach()
    return this
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@RequiresApi(Build.VERSION_CODES.S)
private class NativeDialogBlurAttachment(
    private val windowManager: WindowManager,
    private val window: Window,
    private val blurRadiusPx: Int,
) {
    private val decorView = window.decorView
    private val initialFlags = window.attributes.flags
    private val initialBlurRadius = window.attributes.blurBehindRadius
    private var registered = false
    private var disposed = false

    private val blurListener = Consumer<Boolean> { enabled ->
        if (enabled) {
            applyBlur()
        } else {
            restoreWindowState()
        }
    }

    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) {
            registerBlurListener()
        }

        override fun onViewDetachedFromWindow(view: View) {
            dispose()
        }
    }

    fun attach() {
        decorView.addOnAttachStateChangeListener(attachListener)
        if (decorView.isAttachedToWindow) registerBlurListener()
    }

    private fun registerBlurListener() {
        if (registered || disposed) return
        registered = true
        windowManager.addCrossWindowBlurEnabledListener(blurListener)
    }

    private fun applyBlur() {
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.apply {
            setBlurBehindRadius(blurRadiusPx)
        }
    }

    private fun restoreWindowState() {
        val originalBlurFlag = initialFlags and WindowManager.LayoutParams.FLAG_BLUR_BEHIND != 0
        if (originalBlurFlag) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
        window.attributes = window.attributes.apply {
            setBlurBehindRadius(initialBlurRadius)
        }
    }

    private fun dispose() {
        if (disposed) return
        disposed = true
        decorView.removeOnAttachStateChangeListener(attachListener)
        if (registered) {
            windowManager.removeCrossWindowBlurEnabledListener(blurListener)
            registered = false
        }
        restoreWindowState()
    }
}

fun AlertDialog.tryToShow() {
    try {
        val activity = context as Activity
        if (!activity.isFinishing) {
            show()
        }
    } catch (e: Exception) {
        Logs.e(e)
    }
}
