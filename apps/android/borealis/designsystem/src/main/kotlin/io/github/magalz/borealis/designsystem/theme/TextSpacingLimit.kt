package io.github.magalz.borealis.designsystem.theme

/**
 * The font scale from which [TextSpacing.Wider] is drawn as [TextSpacing.Wide]: the lowest of the
 * measured scales (1.5, 1.7, 1.8 and 2.0, with the Text size at its default) at which a fit test of
 * the screens fails at Wider. Below it Wider fits, bar the Pairing screen of a TV (see
 * [effectiveTextSpacing]).
 */
private const val WIDER_LIMIT_FONT_SCALE = 1.5f

/**
 * The font scale from which [TextSpacing.Wide] and [TextSpacing.Wider] are drawn as
 * [TextSpacing.Normal]: the lowest measured scale at which a fit test of the screens fails at Wide.
 */
private const val WIDE_LIMIT_FONT_SCALE = 1.7f

/**
 * The Text spacing that the text is drawn with: [chosen], or the largest level that the [fontScale]
 * allows when that is smaller (ADR 0058, decision 4). A text drawn at a large font scale has no
 * room left for the letter, word and line spacing of the wider levels, and the screens would cut
 * it: Wider is drawn as Wide from 1.5, and Wide and Wider as Normal from 1.7. Normal is always
 * drawn as it is.
 *
 * The scale is the one that the text is drawn at: the font scale of the system times the Text size
 * of the look, which is what `LocalDensity.current.fontScale` holds under [BorealisTheme]. The text
 * that overflows is the text at the size that is drawn, so the Text size counts as much as the
 * system does. Where the user chose a smaller font, the level that was chosen comes back, because
 * what was chosen is never changed.
 *
 * The thresholds are the lowest scale of the measured ladder at which a fit test fails at each
 * level, tried at 1.5, 1.7, 1.8 and 2.0 in every font. Wider also fails in the Pairing screen of a
 * TV from a scale of 1.3, and once at 1.15; those are layout faults of their own and the limit does
 * not cover them. Android offers a font scale of 2.0 at most, and Normal fits there.
 */
fun effectiveTextSpacing(chosen: TextSpacing, fontScale: Float): TextSpacing {
    val allowed =
        when {
            fontScale >= WIDE_LIMIT_FONT_SCALE -> TextSpacing.Normal
            fontScale >= WIDER_LIMIT_FONT_SCALE -> TextSpacing.Wide
            else -> TextSpacing.Wider
        }
    return minOf(chosen, allowed)
}
