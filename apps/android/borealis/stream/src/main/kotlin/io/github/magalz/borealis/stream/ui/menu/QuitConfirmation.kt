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
import io.github.magalz.borealis.stream.R

private val PADDING_HORIZONTAL = 16.dp
private val PADDING_VERTICAL = 8.dp
private val GAP = 12.dp

/**
 * The confirmation of Quit, in the panel: what it costs, Cancel (focused, so a stray OK changes
 * nothing) and Quit.
 */
@Composable
internal fun QuitConfirmation(appName: String, onCancel: () -> Unit, onQuit: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Column(
        Modifier.padding(PADDING_HORIZONTAL, PADDING_VERTICAL),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            stringResource(R.string.stream_menu_quit_confirm, appName),
            Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            color = LocalBorealisColors.current.ink2,
            style = MaterialTheme.typography.bodyMedium,
        )
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
