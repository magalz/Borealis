package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/** Screen gutter and card padding of a form factor; handheld uses the TV values. */
@Immutable class BorealisSpacing(val gutter: Dp, val cardPadding: Dp)

/** The padding inside a card in Dense (ADR 0058, decision 7), on a TV and a handheld. */
private val DENSE_CARD_PADDING_TV = 12.dp

/** The padding inside a card in Dense, on a phone. */
private val DENSE_CARD_PADDING_PHONE = 10.dp

/**
 * The spacing of [formFactor] at [density]: the gutter does not change with the density, and the
 * padding inside a card is smaller in Dense.
 */
fun borealisSpacing(
    formFactor: FormFactor,
    density: CardDensity = CardDensity.Comfortable,
): BorealisSpacing {
    val dense = density == CardDensity.Dense
    return when (formFactor) {
        FormFactor.Phone ->
            BorealisSpacing(
                BorealisTokens.Space.GutterPhone,
                if (dense) DENSE_CARD_PADDING_PHONE else BorealisTokens.Space.CardPadding,
            )
        FormFactor.Tv,
        FormFactor.Handheld ->
            BorealisSpacing(
                BorealisTokens.Space.GutterTv,
                if (dense) DENSE_CARD_PADDING_TV else BorealisTokens.Space.CardPaddingTv,
            )
    }
}

val LocalBorealisSpacing = staticCompositionLocalOf { borealisSpacing(FormFactor.Phone) }
