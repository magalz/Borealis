package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/** Screen gutter and card padding of a form factor; handheld uses the TV values. */
@Immutable class BorealisSpacing(val gutter: Dp, val cardPadding: Dp)

fun borealisSpacing(formFactor: FormFactor): BorealisSpacing =
    when (formFactor) {
        FormFactor.Phone ->
            BorealisSpacing(BorealisTokens.Space.GutterPhone, BorealisTokens.Space.CardPadding)
        FormFactor.Tv,
        FormFactor.Handheld ->
            BorealisSpacing(BorealisTokens.Space.GutterTv, BorealisTokens.Space.CardPaddingTv)
    }

val LocalBorealisSpacing = staticCompositionLocalOf { borealisSpacing(FormFactor.Phone) }
