package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import io.github.magalz.borealis.stream.ui.menu.MENU_HANDLE_TOP_OFFSET_DP
import io.github.magalz.borealis.stream.ui.menu.MenuHandle
import io.github.magalz.borealis.stream.ui.overlay.PerfOverlay

private val OVERLAY_MARGIN = 10.dp
private val HINT_TOP_OFFSET = 44.dp

/**
 * What the chrome draws over the stream: the performance overlay, the pull handle on a device with
 * a touchscreen, the first-run hint, and the panel that is open (the in-game menu or the keyboard).
 * With nothing open and the overlay off it draws nothing but the handle.
 */
@Composable
fun StreamingLayer(
    appName: String,
    hostName: String,
    phase: StreamPhase.Streaming,
    perf: PerfSnapshot?,
    intents: StreamIntents,
) {
    val formFactor = LocalFormFactor.current
    Box(Modifier.fillMaxSize()) {
        PerfOverlay(
            phase.overlay,
            perf,
            phase.info,
            Modifier.align(Alignment.TopStart)
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
            Panel.MENU -> Unit
            Panel.KEYBOARD -> StreamKeyboard(docked = formFactor == FormFactor.Phone, intents)
        }
        MenuHint(
            phase.showMenuHint,
            phase.panel != Panel.NONE,
            intents::dismissMenuHint,
            Modifier.align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(top = HINT_TOP_OFFSET),
        )
    }
}
