package io.github.magalz.borealis.stream.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.magalz.borealis.stream.R

/**
 * The app as the stream screens name it: its name, or a neutral label when the name is not known (a
 * resume of a running app that the Library has no name for).
 */
@Composable
internal fun appLabel(appName: String): String = appName.ifBlank {
    stringResource(R.string.stream_app_unknown)
}
