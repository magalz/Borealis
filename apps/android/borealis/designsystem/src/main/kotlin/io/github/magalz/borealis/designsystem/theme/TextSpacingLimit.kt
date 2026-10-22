package io.github.magalz.borealis.designsystem.theme

/**
 * The font scale from which [TextSpacing.Wider] is drawn as [TextSpacing.Wide]. It is the lowest of
 * the scales of the ladder 1.5, 1.7, 1.8 and 2.0 at which a 200 % fit test fails at Wider (every
 * font, Text size at its default): 9 runs at 1.5, 8 at 1.7, 19 at 1.8 and 16 at 2.0.
 */
private const val WIDER_LIMIT_FONT_SCALE = 1.5f

/**
 * The font scale from which [TextSpacing.Wide] and [TextSpacing.Wider] are drawn as
 * [TextSpacing.Normal]. It is the lowest of the same ladder at which a 200 % fit test fails at
 * Wide: 1 run at 1.7, 1 at 1.8 and 8 at 2.0. Normal passes at every scale of the ladder.
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
 * of the look, which is what `LocalDensity.current.fontScale` holds under [BorealisTheme]. What
 * overflows is the text at the size that is drawn, so the Text size counts as much as the system
 * does. What was chosen is never changed, so it comes back when the scale falls again.
 *
 * Below 1.5 the limit does nothing, and Wider is not clean there: the Pairing screen of a TV fails
 * from 1.3 (4 of 7 fonts at 1.3, all 7 at 1.4), and one run of the Settings fails at 1.15. They are
 * faults of those layouts, found by the same fit tests, and the limit does not hide them.
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
