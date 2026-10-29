package io.github.magalz.borealis.stream.ui.hint

import android.view.InputDevice
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.adaptive.isGamepadConnected
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.stream.R
import kotlinx.coroutines.delay

private const val HINT_DURATION_MS = 6_000L
private val PADDING_HORIZONTAL = 14.dp
private val PADDING_VERTICAL = 6.dp

/**
 * The line that tells a new user how to open the menu on this device, while [show] is true: the
 * chord when the stream has a keyboard or a mouse and no pad, with the Back button of the mouse too
 * when [mouseNavButtons] says that it opens the menu, else the way of the form factor. It goes
 * after six seconds, or when a panel opens, and then calls [onDismiss] once, so the session
 * remembers that it was seen. It takes no input.
 */
@Composable
internal fun MenuHint(
    show: Boolean,
    panelOpen: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    mouseNavButtons: Boolean = false,
) {
    val dismiss by rememberUpdatedState(onDismiss)
    var done by remember { mutableStateOf(false) }
    val active = show && !done
    LaunchedEffect(active, panelOpen) {
        if (!active) return@LaunchedEffect
        if (!panelOpen) delay(HINT_DURATION_MS)
        done = true
        dismiss()
    }
    if (!active || panelOpen) return
    // Read when the line first shows: the line is on for six seconds.
    val keyboardOrMouse = remember { keyboardOrMouseWithoutPad() }
    val colors = LocalBorealisColors.current
    val shape = LocalBorealisShapes.current.pill
    Text(
        stringResource(hintText(LocalFormFactor.current, keyboardOrMouse, mouseNavButtons)),
        modifier
            .semantics { liveRegion = LiveRegionMode.Polite }
            .background(colors.surfaceRaised, shape)
            .border(1.dp, colors.line, shape)
            .padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        color = colors.ink2,
        style = MaterialTheme.typography.bodySmall,
    )
}

private fun hintText(
    formFactor: FormFactor,
    keyboardOrMouse: Boolean,
    mouseNavButtons: Boolean,
) =
    if (keyboardOrMouse) {
        if (mouseNavButtons) R.string.stream_hint_keyboard_mouse else R.string.stream_hint_keyboard
    } else {
        when (formFactor) {
            FormFactor.Phone -> R.string.stream_hint_phone
            FormFactor.Handheld -> R.string.stream_hint_handheld
            FormFactor.Tv -> R.string.stream_hint_tv
        }
    }

/**
 * Whether the stream is played with a physical keyboard or mouse and no game controller: a mouse,
 * or a keyboard with letters, that is no virtual device, and no pad ([isGamepadConnected]; a pad
 * that the app's USB driver has claimed is no Android input device, so it does not count). It wins
 * over the form factor, which only knows the pads. A remote has keys but no letters and a
 * touchscreen is neither.
 */
private fun keyboardOrMouseWithoutPad(): Boolean =
    InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.isKeyboardOrMouse() == true
    } && !isGamepadConnected()

private fun InputDevice.isKeyboardOrMouse(): Boolean =
    !isVirtual &&
        (sources and InputDevice.SOURCE_MOUSE == InputDevice.SOURCE_MOUSE ||
            (sources and InputDevice.SOURCE_KEYBOARD == InputDevice.SOURCE_KEYBOARD &&
                keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC))
