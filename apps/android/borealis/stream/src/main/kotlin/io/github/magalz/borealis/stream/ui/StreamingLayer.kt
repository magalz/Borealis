package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PerfSnapshot
import io.github.magalz.borealis.stream.StreamIntents
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.ui.hint.MenuHint
import io.github.magalz.borealis.stream.ui.menu.InGameMenu
import io.github.magalz.borealis.stream.ui.menu.MENU_HANDLE_TOP_OFFSET_DP
import io.github.magalz.borealis.stream.ui.menu.MenuHandle
import io.github.magalz.borealis.stream.ui.overlay.PerfOverlay

private val OVERLAY_MARGIN = 10.dp

// The handle is 88dp wide with the area that takes the touch, centred: 44dp each side, and 4dp.
private val OVERLAY_HANDLE_GAP = 48.dp
private val HINT_TOP_OFFSET = 44.dp

/**
 * What the chrome draws over the stream: the performance overlay, the pull handle on a device with
 * a touchscreen, the first-run hint, and the panel that is open (the in-game menu or the keyboard).
 * With nothing open and the overlay off it draws nothing but the handle.
 *
 * [videoGlass] is the menu glass option of the viewer (ADR 0061): true draws the in-game menu and
 * its Quit popup over a live blurred copy of the video where the device and the accessibility
 * settings allow it, false draws them solid.
 */
@Composable
fun StreamingLayer(
    appName: String,
    hostName: String,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot?,
    intents: StreamIntents,
    videoGlass: Boolean = true,
) {
    val formFactor = LocalFormFactor.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        PerfOverlay(
            phase.overlay,
            perf,
            phase.info,
            // It ends before the handle, in the middle of the top edge, and wraps inside that.
            Modifier.align(Alignment.TopStart)
                .widthIn(max = maxWidth / 2 - OVERLAY_HANDLE_GAP)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(OVERLAY_MARGIN),
        )
        when (phase.panel) {
            Panel.NONE ->
                if (formFactor != FormFactor.Tv) {
                    MenuHandle(
                        intents::openMenu,
                        Modifier.align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(top = MENU_HANDLE_TOP_OFFSET_DP.dp),
                    )
                }
            Panel.MENU -> InGameMenu(appName, hostName, phase, perf, intents, videoGlass)
            Panel.KEYBOARD -> StreamKeyboard(docked = phase.dockedKeyboard, intents)
        }
        MenuHint(
            phase.showMenuHint,
            phase.panel != Panel.NONE,
            intents::dismissMenuHint,
            Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = HINT_TOP_OFFSET),
            mouseNavButtons = phase.mouseNavButtons,
        )
    }
}
