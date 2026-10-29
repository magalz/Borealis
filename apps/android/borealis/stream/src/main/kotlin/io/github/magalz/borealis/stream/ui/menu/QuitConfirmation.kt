package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.components.BorealisButtonStyle
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.copy.QuitCopy
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.host.apps.isDesktop
import io.github.magalz.borealis.stream.R
import io.github.magalz.borealis.stream.ui.StreamDialog
import io.github.magalz.borealis.stream.ui.StreamDialogAction
import io.github.magalz.borealis.stream.ui.common.appLabel

/**
 * What Quit does to the app, as the consequence under the menu row and the body of its popup, a
 * sentence on each line. Desktop has no program to close, so Quit only ends the stream; any other
 * app closes and loses its unsaved progress. The words are [QuitCopy]'s, and Desktop is decided by
 * the same `isDesktop` as in the Library.
 */
@Composable
internal fun quitConsequence(appName: String): String =
    QuitCopy.consequence(appLabel(appName), isDesktop(appName))

/**
 * The Quit popup of the menu, a [StreamDialog] over the menu: the question as the title and what it
 * costs as the body (the consequence of the row, a sentence on each line), then Cancel (focused, so
 * a stray OK changes nothing) and Quit. The gamepad B button and Back call [onCancel], so they
 * close only the popup.
 */
@Composable
internal fun QuitConfirmation(appName: String, onCancel: () -> Unit, onQuit: () -> Unit) {
    StreamDialog(
        title = stringResource(R.string.stream_menu_quit_confirm, appLabel(appName)),
        onDismiss = onCancel,
        actions =
            listOf(
                StreamDialogAction(
                    stringResource(R.string.stream_menu_quit_confirm_cancel),
                    BorealisButtonStyle.Secondary,
                    onCancel,
                ),
                StreamDialogAction(
                    stringResource(R.string.stream_menu_quit_confirm_quit),
                    BorealisButtonStyle.Danger,
                    onQuit,
                ),
            ),
    ) {
        Text(
            quitConsequence(appName),
            color = LocalBorealisColors.current.ink2,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
