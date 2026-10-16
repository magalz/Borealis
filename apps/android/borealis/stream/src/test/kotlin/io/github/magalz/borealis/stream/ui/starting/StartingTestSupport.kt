package io.github.magalz.borealis.stream.ui.starting

import androidx.compose.runtime.Composable
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.width
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor

/** Robolectric qualifiers of the devices the stream chrome is laid out for. */
internal const val PHONE = "w360dp-h640dp-xhdpi"
internal const val TV = "w960dp-h540dp-land-television-xhdpi"
internal const val HANDHELD = "w800dp-h480dp-land-hdpi"

/** Sets [content] in the theme `StreamChromeRoot` wraps every screen in. */
internal fun ComposeContentTestRule.setChrome(
    formFactor: FormFactor,
    content: @Composable () -> Unit,
) = setContent { BorealisTheme(formFactor, content) }

/**
 * Like [setChrome], and then switches the window to the keyboard input mode, the way a D-pad or a
 * gamepad key does on a device: clickables are focusable in this mode only.
 */
internal fun ComposeContentTestRule.setChromeInKeyboardMode(
    formFactor: FormFactor,
    content: @Composable () -> Unit,
) {
    var manager: InputModeManager? = null
    setChrome(formFactor) {
        manager = LocalInputModeManager.current
        content()
    }
    runOnIdle { assertThat(checkNotNull(manager).requestInputMode(InputMode.Keyboard)).isTrue() }
    waitForIdle()
}

/** Asserts the node is [expected] wide, give or take a dp for rounding to pixels. */
internal fun SemanticsNodeInteraction.assertWidthNear(expected: Dp) {
    assertThat(getBoundsInRoot().width.value).isWithin(1f).of(expected.value)
}
