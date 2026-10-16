package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.SessionInfo
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.ui.starting.RecordingIntents
import io.github.magalz.borealis.stream.ui.starting.setChrome
import io.github.magalz.borealis.stream.ui.starting.setChromeInKeyboardMode

/** A streaming phase with the defaults of a phone that has not shown the hint. */
internal fun aStreaming(
    panel: Panel = Panel.NONE,
    overlay: OverlayMode = OverlayMode.OFF,
    touchMode: TouchMode? = TouchMode.TRACKPAD,
    gamepadMouse: Boolean? = null,
    info: SessionInfo = aSessionInfo,
    showMenuHint: Boolean = false,
) = StreamPhase.Streaming(panel, overlay, touchMode, gamepadMouse, info, showMenuHint)

/**
 * The streaming layer over a form factor, the way `StreamChromeRoot` draws it. [phase] and [perf]
 * can be changed by a test; [handleBounds] collects what the layer reports for the menu handle.
 */
internal class LayerFixture(
    private val rule: ComposeContentTestRule,
    private val formFactor: FormFactor,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot? = null,
    val intents: RecordingIntents = RecordingIntents(),
    private val keyboardMode: Boolean = false,
) {
    var phase by mutableStateOf(phase)
    var perf by mutableStateOf(perf)
    val handleBounds = mutableListOf<Rect?>()

    fun show() {
        val content: @Composable () -> Unit = {
            CompositionLocalProvider(LocalMenuHandleBounds provides { handleBounds += it }) {
                StreamingLayer("Desktop", "Gaming PC", phase, perf, intents)
            }
        }
        if (keyboardMode) {
            rule.setChromeInKeyboardMode(formFactor, content)
        } else {
            rule.setChrome(formFactor, content)
        }
    }
}
