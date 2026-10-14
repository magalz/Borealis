package io.github.magalz.borealis.settings

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import android.view.View
import android.view.WindowInsets
import kotlin.math.max

/**
 * Padding of the settings content: the system-bar and display-cutout insets on handhelds (the
 * window is edge-to-edge from Android 15), and the same 15 dp overscan padding on a TV that the
 * upstream activities get from `UiHelper.notifyNewRootView`.
 */
internal object ContentPadding {
    private const val TV_PADDING_DP = 15

    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)

    fun compute(isTelevision: Boolean, density: Float, systemInsets: Padding): Padding {
        if (!isTelevision) return systemInsets
        val pixels = (TV_PADDING_DP * density + 0.5f).toInt()
        return Padding(pixels, pixels, pixels, pixels)
    }

    /** Pads the content view of [activity] now (TV) or whenever insets arrive (handhelds). */
    fun install(activity: Activity) {
        val content = activity.findViewById<View>(android.R.id.content)
        val uiMode = activity.resources.configuration.uiMode
        val television =
            (uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION
        val density = activity.resources.displayMetrics.density
        if (television) {
            content.setPaddingFrom(compute(true, density, Padding(0, 0, 0, 0)))
            return
        }
        content.setOnApplyWindowInsetsListener { view, insets ->
            view.setPaddingFrom(compute(false, density, systemInsets(insets)))
            insets
        }
        content.requestApplyInsets()
    }

    private fun View.setPaddingFrom(padding: Padding) =
        setPadding(padding.left, padding.top, padding.right, padding.bottom)

    @Suppress("DEPRECATION")
    private fun systemInsets(insets: WindowInsets): Padding {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val all =
                insets.getInsets(
                    WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
                )
            return Padding(all.left, all.top, all.right, all.bottom)
        }
        val cutout = insets.displayCutout
        return Padding(
            max(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0),
            max(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0),
            max(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0),
            max(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0),
        )
    }
}
