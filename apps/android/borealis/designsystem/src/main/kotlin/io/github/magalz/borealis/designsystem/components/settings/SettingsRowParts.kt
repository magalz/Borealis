package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
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
private val GLYPH_SIZE = 12.dp
private val GLYPH_STROKE = 1.5.dp

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

/** The chevron at the end of a row that opens something. */
@Composable
internal fun Chevron(color: Color = LocalBorealisColors.current.ink3) {
    Canvas(Modifier.size(GLYPH_SIZE)) {
        val path =
            Path().apply {
                moveTo(size.width * 0.3f, size.height * 0.1f)
                lineTo(size.width * 0.75f, size.height * 0.5f)
                lineTo(size.width * 0.3f, size.height * 0.9f)
            }
        drawPath(
            path,
            color,
            style = Stroke(GLYPH_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

/** The mark of the selected choice of a list. */
@Composable
internal fun CheckMark(color: Color = LocalBorealisColors.current.accent) {
    Canvas(Modifier.size(GLYPH_SIZE)) {
        val path =
            Path().apply {
                moveTo(size.width * 0.1f, size.height * 0.55f)
                lineTo(size.width * 0.4f, size.height * 0.85f)
                lineTo(size.width * 0.95f, size.height * 0.2f)
            }
        drawPath(
            path,
            color,
            style = Stroke(GLYPH_STROKE.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
