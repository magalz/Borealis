package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.BorealisButton
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.focus.initialFocus
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.host.apps.isDesktop
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.ui.common.appLabel

private val PADDING_HORIZONTAL = 16.dp
private val PADDING_VERTICAL = 8.dp
private val GAP = 12.dp
private val TITLE_GAP = 4.dp

/**
 * What Quit does to the app, as the consequence under the menu row and the body of its
 * confirmation, a sentence on each line. Desktop has no program to close, so Quit only ends the
 * stream; any other app closes and loses its unsaved progress.
 */
@Composable
internal fun quitConsequence(appName: String): String =
    if (isDesktop(appName)) {
        stringResource(R.string.stream_menu_quit_consequence_desktop)
    } else {
        stringResource(R.string.stream_menu_quit_consequence, appLabel(appName))
    }

/**
 * The confirmation of Quit, in the panel: the question as the title and what it costs as the body
 * (the consequence of the row, a sentence on each line), then Cancel (focused, so a stray OK
 * changes nothing) and Quit. The title and the body are announced together when it opens.
 */
@Composable
internal fun QuitConfirmation(appName: String, onCancel: () -> Unit, onQuit: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    val colors = LocalBorealisColors.current
    Column(
        Modifier.padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Column(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalArrangement = Arrangement.spacedBy(TITLE_GAP),
        ) {
            Text(
                stringResource(R.string.stream_menu_quit_confirm, appLabel(appName)),
                color = colors.ink,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                quitConsequence(appName),
                color = colors.ink2,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
            BorealisButton(
                stringResource(R.string.stream_menu_quit_confirm_cancel),
                onCancel,
                Modifier.initialFocus(cancelFocus),
                BorealisButtonStyle.Secondary,
            )
            BorealisButton(
                stringResource(R.string.stream_menu_quit_confirm_quit),
                onQuit,
                style = BorealisButtonStyle.Danger,
            )
        }
    }
}
