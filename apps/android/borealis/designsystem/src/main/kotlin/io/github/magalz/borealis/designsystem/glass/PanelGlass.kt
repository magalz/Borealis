package io.github.magalz.borealis.designsystem.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.github.magalz.borealis.designsystem.theme.BorealisColors

/**
 * What a panel inside a [GlassHost] draws where the host blurs its window: the glass [tint] (0 to
 * 1) of its surface colour, and the text [colors] that keep their contrast on that glass.
 */
@Immutable class PanelGlass(val tint: Float, val colors: BorealisColors)

@Composable fun rememberPanelGlass(): PanelGlass? = null
