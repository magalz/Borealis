package io.github.magalz.borealis.designsystem.testing

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.TextSpacing

/**
 * One run of a fit test: the screens in [font] at [spacing] (ADR 0058, decisions 2 and 4), with the
 * cards of Hosts and Apps at [density] (decision 7). Its name is the name of the run in the test
 * report; the density is named when it is not Comfortable.
 */
class FitCase(
    val font: FontChoice,
    val spacing: TextSpacing,
    val density: CardDensity = CardDensity.Comfortable,
) {
    override fun toString() =
        if (density == CardDensity.Comfortable) "$font-$spacing" else "$font-$spacing-$density"
}

/**
 * The runs of the fit tests at the largest text: every font at Normal and at Wider, the widest
 * level, and IBM Plex, the default, at Wide as well.
 */
fun fitCases(): List<FitCase> =
    FontChoice.entries.map { FitCase(it, TextSpacing.Normal) } +
        FitCase(FontChoice.Plex, TextSpacing.Wide) +
        FontChoice.entries.map { FitCase(it, TextSpacing.Wider) }

/**
 * The runs of the fit tests of the card grids: [fitCases], and the denser layouts (Compact and
 * Dense) in IBM Plex and in Andika, the widest font, at Normal and at Wider. The tests of the other
 * screens have no use for the density, so they keep [fitCases].
 */
fun cardFitCases(): List<FitCase> =
    fitCases() +
        listOf(CardDensity.Compact, CardDensity.Dense).flatMap { density ->
            listOf(FontChoice.Plex, FontChoice.Andika).flatMap { font ->
                listOf(TextSpacing.Normal, TextSpacing.Wider).map { FitCase(font, it, density) }
            }
        }

/**
 * The width of a host card on a TV 960dp wide, where the rail has 220dp: what the grid of Hosts has
 * left of the other 740dp, after its padding and the 12dp gaps, over its columns. A test of the
 * Library keeps it equal to what the screen lays out.
 */
fun tvHostCardWidth(density: CardDensity): Dp {
    val padding = if (density == CardDensity.Dense) DENSE_PADDING else GUTTER
    val columns = if (density == CardDensity.Comfortable) COMFORTABLE_COLUMNS else DENSER_COLUMNS
    return (CONTENT_WIDTH - padding * 2 - GAP * (columns - 1)) / columns
}

private val CONTENT_WIDTH = 740.dp
private val GUTTER = 34.dp
private val DENSE_PADDING = 12.dp
private val GAP = 12.dp
private const val COMFORTABLE_COLUMNS = 3
private const val DENSER_COLUMNS = 4
