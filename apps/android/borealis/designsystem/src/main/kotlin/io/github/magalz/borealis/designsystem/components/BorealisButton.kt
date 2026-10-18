package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.focus.BorealisPressIndication
import io.github.magalz.borealis.designsystem.focus.borealisFocusIndication
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalFormFactor

/** How prominent a button is. */
enum class BorealisButtonStyle {
    /** The one main action of a screen or card: accent fill. */
    Primary,

    /** A secondary action: line-strong border, ink text. */
    Secondary,

    /** A destructive action: danger-ink text, no fill. */
    Danger,

    /** A quiet action: accent text. */
    Text,
}

private const val DISABLED_ALPHA = 0.38f
private val PHONE_MIN_HEIGHT = 40.dp
private val TV_MIN_HEIGHT = 48.dp
private val HORIZONTAL_PADDING = 16.dp

/**
 * A button of the given [style], 40dp high at least on a phone and 48dp on a TV or handheld. It is
 * focusable, with the Borealis focus indication.
 */
@Composable
fun BorealisButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: BorealisButtonStyle = BorealisButtonStyle.Primary,
    enabled: Boolean = true,
) {
    ButtonFace(text, style, enabled, modifier, onClick)
}

/**
 * The look of a button. With an [onClick] it is a real button with the focus indication; without,
 * it only looks like one, for a card that is itself the click target (the resume button of the
 * Continue card).
 */
@Composable
internal fun ButtonFace(
    text: String,
    style: BorealisButtonStyle,
    enabled: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?,
) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    val minHeight =
        if (LocalFormFactor.current == FormFactor.Phone) PHONE_MIN_HEIGHT else TV_MIN_HEIGHT
    val fill = if (style == BorealisButtonStyle.Primary) colors.accent else Color.Transparent
    val content =
        when (style) {
            BorealisButtonStyle.Primary -> colors.onAccent
            BorealisButtonStyle.Secondary -> colors.ink
            BorealisButtonStyle.Danger -> colors.dangerInk
            BorealisButtonStyle.Text -> colors.accent
        }
    Box(
        modifier
            .then(if (onClick != null) Modifier.borealisFocusIndication(shape) else Modifier)
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .background(fill, shape)
            .then(
                if (style == BorealisButtonStyle.Secondary) {
                    Modifier.border(1.dp, colors.lineStrong, shape)
                } else {
                    Modifier
                }
            )
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = BorealisPressIndication,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = HORIZONTAL_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = content, style = MaterialTheme.typography.labelLarge)
    }
}
