package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.testing.setChromeInKeyboardMode

/**
 * A streaming phase with the defaults of a phone that has not shown the hint. A test that needs the
 * hint, a pointer speed or a docked keyboard uses `copy`.
 */
internal fun aStreaming(
    panel: Panel = Panel.NONE,
    overlay: OverlayMode = OverlayMode.OFF,
    touchMode: TouchMode? = TouchMode.TRACKPAD,
    gamepadMouse: Boolean? = null,
    info: SessionInfo = aSessionInfo,
) =
    StreamPhase.Streaming(
        panel = panel,
        overlay = overlay,
        touchMode = touchMode,
        gamepadMouse = gamepadMouse,
        info = info,
        showMenuHint = false,
        pointerSpeed = PointerSpeed.NORMAL,
        dockedKeyboard = false,
    )

/**
 * The streaming layer over a form factor, the way `StreamChromeRoot` draws it. [phase] and [perf]
 * can be changed by a test; [handleBounds] collects what the layer reports for the menu handle.
 */
internal class LayerFixture(
    private val rule: ComposeContentTestRule,
    private val formFactor: FormFactor,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot? = null,
    private val keyboardMode: Boolean = false,
    private val appName: String = "Desktop",
) {
    val intents = RecordingIntents()
    var phase by mutableStateOf(phase)
    var perf by mutableStateOf(perf)
    val handleBounds = mutableListOf<Rect?>()

    /** The keys a handler outside the layer saw go down: the ones the layer did not consume. */
    val keysSeenOutside = mutableListOf<Key>()

    fun show() {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalMenuHandleBounds provides { handleBounds += it }) {
                Box(
                    Modifier.onKeyEvent {
                        if (it.type == KeyEventType.KeyDown) keysSeenOutside += it.key
                        false
                    }
                ) {
                    StreamingLayer(appName, "Gaming PC", phase, perf, intents)
                }
            }
        }
        if (keyboardMode) {
            rule.setChromeInKeyboardMode(formFactor, content)
        } else {
            rule.setChrome(formFactor, content)
        }
    }
}
