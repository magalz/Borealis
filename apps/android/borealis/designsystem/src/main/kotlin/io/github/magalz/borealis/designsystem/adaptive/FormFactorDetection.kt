package io.github.magalz.borealis.designsystem.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** From this width a window is a tablet or handheld; below it, a phone whatever controls it has. */
internal const val WIDE_WINDOW_DP = 600

/**
 * The device class of a window, decided from facts the platform reports (ADR 0052):
 * - **TV** when the leanback feature is present or the UI mode is television. It wins over every
 *   other fact, so a TV with a touch screen or a narrow window is still a TV.
 * - **Handheld** when the window is [WIDE_WINDOW_DP] wide or more and the device reports the
 *   controls of a gamepad console (a D-pad, a trackball or a wheel as its navigation, or no touch
 *   screen at all) or a game controller is connected ([gamepadConnected]).
 * - **Phone** otherwise.
 *
 * Consequences, all intended:
 * - A touch tablet reports no navigation and a touch screen, so it stays a phone layout.
 * - A phone with a clip-on or paired gamepad (Backbone, Kishi) in landscape, 600dp or wider, counts
 *   as a handheld: that is the use the handheld layout is for.
 * - The configuration alone misses such a pad: Android reports a D-pad navigation only for a device
 *   with D-pad key codes, and modern pads (Xbox, DualSense, 8BitDo, Switch Pro) send the D-pad as a
 *   hat axis, so the phone keeps reporting a touch screen and no navigation. That is why the
 *   connected game controller is a fact of its own.
 * - The result follows the window and the connected pads, so it flips when the phone is rotated or
 *   a pad connects or disconnects; the screens recompose with the other layout and keep their
 *   state.
 * - A Chromebook or other non-touch device that is 600dp wide or more counts as a handheld, because
 *   it reports no touch screen. Its layout is the TV/handheld one, which a keyboard can navigate.
 */
fun formFactorOf(
    isTvFeature: Boolean,
    uiModeType: Int,
    widthDp: Int,
    touchscreen: Int,
    navigation: Int,
    gamepadConnected: Boolean,
): FormFactor =
    when {
        isTvFeature || uiModeType == Configuration.UI_MODE_TYPE_TELEVISION -> FormFactor.Tv
        widthDp >= WIDE_WINDOW_DP &&
            (hasGamepadControls(touchscreen, navigation) || gamepadConnected) -> FormFactor.Handheld
        else -> FormFactor.Phone
    }

private fun hasGamepadControls(touchscreen: Int, navigation: Int) =
    touchscreen == Configuration.TOUCHSCREEN_NOTOUCH ||
        navigation == Configuration.NAVIGATION_DPAD ||
        navigation == Configuration.NAVIGATION_TRACKBALL ||
        navigation == Configuration.NAVIGATION_WHEEL

/**
 * The [FormFactor] of this device and window, read again when the configuration or the window size
 * changes. The width is the window's, not the screen's, so a split-screen window is narrow.
 */
@Composable
fun rememberFormFactor(): FormFactor {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val gamepadConnected = rememberGamepadConnected()
    // On the first composition the window may not have a size yet, so the configuration backs it.
    @Suppress("ConfigurationScreenWidthHeight")
    val configurationWidthDp = configuration.screenWidthDp
    return remember(context, configuration, density, windowWidthPx, gamepadConnected) {
        formFactorOf(
            isTvFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            uiModeType = uiModeTypeOf(context),
            widthDp = windowWidthDp(windowWidthPx, density.density, configurationWidthDp),
            touchscreen = configuration.touchscreen,
            navigation = configuration.navigation,
            gamepadConnected = gamepadConnected,
        )
    }
}

/**
 * The width of the window in dp: the container size once the window has one, the configuration's
 * width before that (a window that is not laid out yet reports 0, which would make a wide handheld
 * a phone for its first frame).
 */
internal fun windowWidthDp(containerWidthPx: Int, density: Float, configurationWidthDp: Int): Int =
    if (containerWidthPx > 0) (containerWidthPx / density).toInt() else configurationWidthDp

private fun uiModeTypeOf(context: Context): Int =
    context.getSystemService(UiModeManager::class.java)?.currentModeType
        ?: Configuration.UI_MODE_TYPE_NORMAL
