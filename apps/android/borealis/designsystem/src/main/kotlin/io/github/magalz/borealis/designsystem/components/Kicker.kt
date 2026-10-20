package io.github.magalz.borealis.designsystem.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.LocalBorealisType

/**
 * A section label: capitals in the kicker style (small, spaced), in the ink-4 colour of the palette
 * it is in, so a label on glass has the glass ink-4.
 */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier,
        color = LocalBorealisColors.current.ink4,
        style = LocalBorealisType.current.kicker,
    )
}
