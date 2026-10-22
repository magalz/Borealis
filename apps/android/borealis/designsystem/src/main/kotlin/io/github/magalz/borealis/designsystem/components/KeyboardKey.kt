package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.focus.optionsClickable
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/** How wide a key of the keyboard is: a character, a modifier or an action, or the space bar. */
enum class KeyStyle(internal val maxWidth: Dp) {
    Normal(NORMAL_KEY_WIDTH),
    Wide(72.dp),
    Space(240.dp);

    /**
     * The share of a row that a key of this style takes when the row is too narrow for the keys at
     * their full width: 1 for a normal key, 72/44 for a wide one and 240/44 for the space bar.
     */
    val weight: Float
        get() = maxWidth / NORMAL_KEY_WIDTH
}

private val NORMAL_KEY_WIDTH = 44.dp
private val PHONE_HEIGHT = 32.dp
private val TV_HEIGHT = 40.dp

/**
 * The least that a caption shrinks to with the default look. At a larger text size the floor is the
 * same size on the screen, not the same number of sp, so a caption is never smaller than the same
 * key shows it with the default look. A Text spacing that widens the letters lowers the floor by
 * the same factor, so that the caption still fits its key: a caption of one word is never broken.
 */
private val MIN_CAPTION_SIZE = 8.sp

/**
 * One key of the in-stream keyboard: a [label] on a raised surface, 32dp high on a phone and 40dp
 * on a TV or handheld (higher when the caption wraps to two lines), and at most as wide as its
 * [style]. A [latched] key (a modifier that waits for the next key) has the accent tint, line and
 * ink, and is announced as locked. A tap or OK runs [onClick]; a long press, holding OK, X and Menu
 * run [onLongClick] (the alternate character of the key). The key is a focus stop with the Borealis
 * focus indication and no scale, so a dense row keeps its layout; the focused key is drawn over its
 * neighbours.
 *
 * A key is as wide as its style allows. A row that has to fit a narrow screen gives each key
 * `Modifier.weight(style.weight, fill = false)` (see [KeyStyle.weight]): the keys shrink together
 * and keep their proportions, so the space bar stays wide next to the letters.
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
    val look = LocalBorealisLook.current
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
            .heightIn(min = height),
        contentAlignment = Alignment.Center,
    ) {
        val textStyle =
            if (style == KeyStyle.Normal) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.bodySmall
            }
        // A caption with a space ("Fn 123") wraps to a second line before it shrinks, and the key
        // grows to hold it; a word is never broken, so it stays on one line and shrinks.
        val wraps = ' ' in label
        Text(
            label,
            color =
                when {
                    latched -> colors.accentSoft
                    style == KeyStyle.Normal -> colors.ink
                    else -> colors.ink2
                },
            textAlign = TextAlign.Center,
            softWrap = wraps,
            maxLines = if (wraps) 2 else 1,
            overflow = TextOverflow.Clip,
            style = textStyle,
            // A key is as wide as its style allows, and a row shrinks it on a narrow screen. A
            // caption that does not fit its key wraps where it can, then shrinks with the key.
            autoSize =
                TextAutoSize.StepBased(
                    minFontSize =
                        MIN_CAPTION_SIZE / look.textSize.scale / (1 + look.textSpacing.letterEm),
                    maxFontSize = textStyle.fontSize,
                ),
        )
    }
}
