package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.AuroraLoader
import io.github.magalz.borealis.designsystem.components.BackdropInk
import io.github.magalz.borealis.designsystem.components.BackdropText
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.ui.common.appLabel

private val LOADER_SIZE = 48.dp
private val CONTENT_GAP = 16.dp

/**
 * A quit under way (UC-004-T5): the screen shows from the confirmation, because the host ends the
 * stream when it gets the cancel and answers it seconds later. It stays quiet, with a progress
 * indicator over the background of the screens outside the game, until the activity finishes. Back
 * leaves it.
 */
@Composable
internal fun QuittingScreen(appName: String) {
    LeavingScreen(stringResource(R.string.stream_quitting, appLabel(appName)))
}

/**
 * What the screens of a quit and a disconnect share: [text] under the Borealis loader, which stands
 * still when animations are removed. The text stands on the background, so it has its halo. The
 * page is the background's, not this screen's.
 */
@Composable
internal fun LeavingScreen(text: String) {
    Column(
        Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(LocalBorealisSpacing.current.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterVertically),
    ) {
        AuroraLoader(size = LOADER_SIZE)
        BackdropText(
            text,
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.titleMedium,
            ink = BackdropInk.Ink3,
            textAlign = TextAlign.Center,
        )
    }
}
