package io.github.magalz.borealis.designsystem.copy

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.designsystem.R

/**
 * What Quit costs, in one place: the Apps sheet of the Library, the Quit popup of the stream and
 * the dialog about an app that already runs on the host read these lines, so they cannot drift
 * apart. Desktop has no program to close, so Quit only ends the stream and nothing is lost; any
 * other app closes and loses its unsaved progress. A sentence on each line. Whether an app is
 * Desktop is the caller's to say, with `isDesktop` of `:borealis:host`, the one rule.
 */
object QuitCopy {
    /** What Quit does to [app], the name of the app as the screen calls it. */
    @Composable
    fun consequence(app: String, desktop: Boolean): String =
        if (desktop) {
            stringResource(R.string.borealis_quit_consequence_desktop)
        } else {
            stringResource(R.string.borealis_quit_consequence, app)
        }

    /** What quitting the app that runs on the other device costs, in the dialog about it. */
    @Composable
    fun conflictWarning(desktop: Boolean): String =
        stringResource(
            if (desktop) {
                R.string.borealis_quit_conflict_warning_desktop
            } else {
                R.string.borealis_quit_conflict_warning
            }
        )
}
