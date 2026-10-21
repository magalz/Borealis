package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.magalz.borealis.designsystem.glass.GlassTextColors
import io.github.magalz.borealis.designsystem.glass.glassSurface
import io.github.magalz.borealis.designsystem.theme.BorealisDimens

/**
 * A glass card for the rows of settings on a phone: its [content], in a column, on the glass
 * surface of the card radius, with the glass text colours. The card has
 * [BorealisDimens.listPadding] inside, which holds the focus outline of the first and the last row:
 * the glass clips what is outside it. Where the glass is solid the card is the `surface` colour.
 */
@Composable
fun SettingsCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GlassTextColors {
        Column(
            modifier.glassSurface(MaterialTheme.shapes.large).padding(BorealisDimens.listPadding),
            content = content,
        )
    }
}
