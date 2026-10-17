package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.Kicker

private val HEADER_PADDING_HORIZONTAL = 16.dp
private val HEADER_PADDING_TOP = 20.dp
private val HEADER_PADDING_BOTTOM = 8.dp

/**
 * The section header of a group in the phone list of settings: the [title] in capitals in the
 * kicker style, announced as a heading. On a TV or handheld the groups are the left list of the
 * [SettingsTwoPane] and have no header.
 */
@Composable
fun SettingsGroupHeader(title: String, modifier: Modifier = Modifier) {
    Kicker(
        title,
        modifier
            .padding(
                start = HEADER_PADDING_HORIZONTAL,
                top = HEADER_PADDING_TOP,
                end = HEADER_PADDING_HORIZONTAL,
                bottom = HEADER_PADDING_BOTTOM,
            )
            .semantics { heading() },
    )
}
