package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/** How wide a key of the keyboard is: a character, a modifier or an action, or the space bar. */
enum class KeyStyle(internal val maxWidth: Dp) {
    Normal(44.dp),
    Wide(72.dp),
    Space(240.dp),
}

private val PHONE_HEIGHT = 32.dp
private val TV_HEIGHT = 40.dp

/**
 * One key of the in-stream keyboard: a [label] on a raised surface, 32dp high on a phone and 40dp
 * on a TV or handheld, and at most as wide as its [style]. A [latched] key (a modifier that waits
 * for the next key) has the accent tint, line and ink, and is announced as locked. A tap or OK runs
 * [onClick]; a long press, holding OK, X and Menu run [onLongClick] (the alternate character of the
 * key). The key is a focus stop with the Borealis focus indication and no scale, so a dense row
 * keeps its layout; the focused key is drawn over its neighbours.
 *
 * A key is as wide as its style allows. A row that has to fit a narrow screen gives each key
 * `Modifier.weight(1f, fill = false)` and the keys shrink together.
 */
@Composable
fun KeyboardKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KeyStyle = KeyStyle.Normal,
    latched: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.small
    val height = if (LocalFormFactor.current == FormFactor.Phone) PHONE_HEIGHT else TV_HEIGHT
    val locked = stringResource(R.string.borealis_key_locked)
    Box(
        modifier
            .liftedWhenFocused()
            .borealisFocusIndication(shape)
            .background(if (latched) colors.accentTint else colors.surfaceRaised, shape)
            .border(1.dp, if (latched) colors.accentLine else colors.line, shape)
            .clip(shape)
            .optionsClickable(onClick, onLongClick, Role.Button)
            .semantics { if (latched) stateDescription = locked }
            .widthIn(max = style.maxWidth)
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color =
                when {
                    latched -> colors.accent
                    style == KeyStyle.Normal -> colors.ink
                    else -> colors.ink2
                },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            style =
                if (style == KeyStyle.Normal) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
        )
    }
}
