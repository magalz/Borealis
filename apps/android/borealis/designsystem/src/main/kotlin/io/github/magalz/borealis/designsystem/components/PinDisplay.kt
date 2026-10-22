package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType

private val CELL_WIDTH = 56.dp
private val CELL_HEIGHT = 64.dp
private val CELL_GAP = 10.dp

/**
 * The pairing PIN, one digit per cell in the PIN style (monospaced, large). A cell is at least 56dp
 * by 64dp and grows with its digit, so that a font with taller or wider digits, or the largest
 * text, does not cut it. A screen reader announces the digits one by one, "4, 8, 1, 7", rather than
 * as a number.
 */
@Composable
fun PinDisplay(pin: String, modifier: Modifier = Modifier) {
    val colors = LocalBorealisColors.current
    val shape = MaterialTheme.shapes.medium
    val spelled = remember(pin) { pin.toList().joinToString(", ") }
    Row(
        modifier.clearAndSetSemantics { contentDescription = spelled },
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        for (digit in pin) {
            Box(
                Modifier.widthIn(min = CELL_WIDTH)
                    .heightIn(min = CELL_HEIGHT)
                    .background(colors.surfaceRaised, shape)
                    .border(1.dp, colors.line, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(digit.toString(), style = LocalBorealisType.current.pin, color = colors.ink)
            }
        }
    }
}
