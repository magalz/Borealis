package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisSpacing
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.ui.common.appLabel

private val SPINNER_SIZE = 32.dp
private val CONTENT_GAP = 16.dp

/**
 * A quit under way (UC-004-T5): the host ends the stream as soon as it gets the cancel and answers
 * it seconds later, so the screen stays quiet, with a progress indicator over the black page, until
 * the activity finishes. Back leaves it.
 */
@Composable
internal fun QuittingScreen(appName: String) {
    val colors = LocalBorealisColors.current
    Column(
        Modifier.fillMaxSize()
            .background(colors.page)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(LocalBorealisSpacing.current.gutter),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CONTENT_GAP, Alignment.CenterVertically),
    ) {
        CircularProgressIndicator(
            Modifier.size(SPINNER_SIZE),
            color = colors.ink3,
            strokeWidth = 3.dp,
        )
        Text(
            stringResource(R.string.stream_quitting, appLabel(appName)),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = colors.ink3,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}
