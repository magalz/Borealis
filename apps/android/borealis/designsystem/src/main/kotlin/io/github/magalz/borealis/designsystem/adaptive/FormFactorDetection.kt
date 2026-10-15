package io.github.magalz.borealis.designsystem.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** From this width a window is a tablet or handheld; below it, a phone whatever controls it has. */
internal const val WIDE_WINDOW_DP = 600

/**
 * The device class of a window, decided from facts the platform reports (ADR 0052):
 * - **TV** when the leanback feature is present or the UI mode is television. It wins over every
 *   other fact, so a TV with a touch screen or a narrow window is still a TV.
 * - **Handheld** when the window is [WIDE_WINDOW_DP] wide or more and the device has the controls
 *   of a gamepad console: a D-pad, a trackball or a wheel as its navigation, or no touch screen at
 *   all. A touch tablet reports no navigation and a touch screen, so it stays a phone; a phone in
 *   portrait is narrower than 600dp, so a paired controller does not turn it into a handheld.
 * - **Phone** otherwise.
 */
fun formFactorOf(
    isTvFeature: Boolean,
    uiModeType: Int,
    widthDp: Int,
    touchscreen: Int,
    navigation: Int,
): FormFactor =
    when {
        isTvFeature || uiModeType == Configuration.UI_MODE_TYPE_TELEVISION -> FormFactor.Tv
        widthDp >= WIDE_WINDOW_DP && hasGamepadControls(touchscreen, navigation) ->
            FormFactor.Handheld
        else -> FormFactor.Phone
    }

private fun hasGamepadControls(touchscreen: Int, navigation: Int) =
    touchscreen == Configuration.TOUCHSCREEN_NOTOUCH ||
        navigation == Configuration.NAVIGATION_DPAD ||
        navigation == Configuration.NAVIGATION_TRACKBALL ||
        navigation == Configuration.NAVIGATION_WHEEL

/** The [FormFactor] of this device and window, read again when the configuration changes. */
@Composable
fun rememberFormFactor(): FormFactor {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        formFactorOf(
            isTvFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            uiModeType = uiModeTypeOf(context),
            widthDp = configuration.screenWidthDp,
            touchscreen = configuration.touchscreen,
            navigation = configuration.navigation,
        )
    }
}

private fun uiModeTypeOf(context: Context): Int =
    context.getSystemService(UiModeManager::class.java)?.currentModeType
        ?: Configuration.UI_MODE_TYPE_NORMAL
