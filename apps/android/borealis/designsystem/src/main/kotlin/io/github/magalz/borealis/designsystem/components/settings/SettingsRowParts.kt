package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.components.liftedWhenFocused
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

internal const val DISABLED_ALPHA = 0.38f
private val PHONE_MIN_HEIGHT = 48.dp
private val TV_MIN_HEIGHT = 52.dp
private val PADDING_HORIZONTAL = 16.dp
private val PADDING_VERTICAL = 6.dp
/** The size of the chevron of a row and of the check mark of a choice. */
internal val ROW_ICON_SIZE = 20.dp

/**
 * The key of the group of a [SettingsTwoPane] that the rows are drawn for, or null outside one. The
 * pane updates its rows in place when the group changes, which is much cheaper than composing them
 * again, but the Material 3 Switch slides its thumb when `checked` changes. A switch row is made
 * again with the group, so that its thumb starts where it belongs and does not slide across the
 * rows of another group.
 */
internal val LocalSettingsGroup = compositionLocalOf<Any?> { null }

/** What a row of settings draws before its interaction: focus lift, outline and clipping. */
@Composable
internal fun Modifier.settingsRowSurface(): Modifier {
    val shape = MaterialTheme.shapes.medium
    return liftedWhenFocused().borealisFocusIndication(shape).clip(shape)
}

/** The size and padding of a row of settings; put it after the row's interaction. */
@Composable
internal fun Modifier.settingsRowSize(): Modifier {
    val minHeight =
        if (LocalFormFactor.current == FormFactor.Phone) PHONE_MIN_HEIGHT else TV_MIN_HEIGHT
    return fillMaxWidth()
        .heightIn(min = minHeight)
        .padding(horizontal = PADDING_HORIZONTAL, vertical = PADDING_VERTICAL)
}

/**
 * The title, the [summary] and the [note] of a row of settings. The note says why the row is
 * disabled, so it stays at full strength; it shows only while the row is disabled.
 */
@Composable
internal fun SettingsRowText(
    title: String,
    summary: String?,
    note: String?,
    enabled: Boolean,
    tone: SettingsRowTone,
    modifier: Modifier = Modifier,
) {
    val colors = LocalBorealisColors.current
    val dim = if (enabled) 1f else DISABLED_ALPHA
    Column(modifier) {
        Text(
            title,
            modifier = Modifier.alpha(dim),
            color = if (tone == SettingsRowTone.Danger) colors.dangerInk else colors.ink,
            style = MaterialTheme.typography.labelLarge,
        )
        if (summary != null) {
            Text(
                summary,
                modifier = Modifier.alpha(dim),
                color = colors.ink3,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!enabled && note != null) {
            Text(note, color = colors.ink3, style = MaterialTheme.typography.bodySmall)
        }
    }
}
