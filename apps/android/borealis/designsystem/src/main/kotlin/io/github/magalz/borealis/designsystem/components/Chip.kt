package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisShapes
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

private val CHIP_PADDING_HORIZONTAL = 9.dp
private val CHIP_PADDING_VERTICAL = 4.dp
private val TAG_PADDING_HORIZONTAL = 8.dp
private val TAG_PADDING_VERTICAL = 2.dp

/**
 * A small monospace label: a key shortcut, a codec, a value. [selected] gives it the accent tint
 * and line, and the soft accent for its label, which keeps 4.5:1 over the tint on the glass. With
 * an [onClick] it is a button that is focusable with the Borealis focus indication, and it is
 * announced as selected or not; without it, it is a static label that is announced as selected only
 * when it is. A button has the 48dp that a finger needs, around the chip and not in it, so the chip
 * keeps its look; a TV has no touch screen and adds nothing.
 */
@Composable
fun Chip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.small
    val finger = onClick != null && LocalFormFactor.current != FormFactor.Tv
    Text(
        text,
        modifier
            .then(if (finger) Modifier.minimumInteractiveComponentSize() else Modifier)
            .then(if (onClick != null) Modifier.borealisFocusIndication(shape) else Modifier)
            .background(if (selected) colors.accentTint else Color.Transparent, shape)
            .border(1.dp, if (selected) colors.accentLine else colors.lineStrong, shape)
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                            interactionSource = null,
                            indication = BorealisPressIndication,
                            role = Role.Button,
                            onClick = onClick,
                        )
                        .semantics { this.selected = selected }
                } else if (selected) {
                    Modifier.semantics { this.selected = true }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = CHIP_PADDING_HORIZONTAL, vertical = CHIP_PADDING_VERTICAL),
        color = if (selected) colors.accentSoft else colors.ink2,
        style = TextStyle(fontFamily = BorealisFonts.Mono, fontSize = monoMinimumSize()),
    )
}

/**
 * The size of the small monospace text of a chip or of the number of a step: the minimum of a TV
 * (13sp) on a TV, and 12sp, the minimum of a handheld, on a handheld and on a phone.
 */
@Composable
internal fun monoMinimumSize(): TextUnit =
    if (LocalFormFactor.current == FormFactor.Tv) {
        BorealisTokens.Size.Tv.Minimum
    } else {
        BorealisTokens.Size.Handheld.Minimum
    }

/**
 * A pill-shaped label for a capability of a host (HDR, HEVC, Wake on LAN). It only informs: it is
 * not focusable. It is the kicker size of the device: 11sp on a phone, 12sp on a handheld and 13sp
 * on a TV, which have no smaller text.
 */
@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    val shape = LocalBorealisShapes.current.pill
    val size = LocalBorealisType.current.kicker.fontSize
    Text(
        text,
        modifier
            .border(1.dp, colors.lineStrong, shape)
            .padding(horizontal = TAG_PADDING_HORIZONTAL, vertical = TAG_PADDING_VERTICAL),
        color = colors.ink3,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = size),
    )
}
