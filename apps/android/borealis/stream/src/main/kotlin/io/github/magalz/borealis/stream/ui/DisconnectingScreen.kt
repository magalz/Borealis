package io.github.magalz.borealis.stream.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.ui.common.appLabel

/**
 * A disconnect under way: the connection ends and the app keeps running on the PC. The screen stays
 * quiet over the background of the screens outside the game, as the Quitting screen does, until the
 * activity finishes.
 */
@Composable
internal fun DisconnectingScreen(appName: String) {
    LeavingScreen(stringResource(R.string.stream_disconnecting, appLabel(appName)))
}
