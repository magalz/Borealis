package io.github.magalz.borealis.designsystem.theme

/**
 * The font scale from which [TextSpacing.Wider] is drawn as [TextSpacing.Wide]: 2.0, the largest
 * that Android offers (WCAG 1.4.4, 200 %).
 *
 * It was measured with the 200 % fit tests of the screens (every font, Wide and Wider, 462 runs at
 * each of the scales 1.15 to 2.0, the Text size at its default):
 * - **Wide** fails nowhere, from 1.15 to 2.0.
 * - **Wider** has no failure that is more than rounding: 44 runs over the ladder run past their box
 *   by 0.5 to 2.6 px, on a line whose last space hangs, and none is cut or ends in an ellipsis. The
 *   fit check allows a pixel, so these are failures to it: 5 at 2.0, and the 200 % tests of the
 *   screens run at 2.0 with that check.
 *
 * Before the Pairing screen of a TV, the footer of the rail and the time of an Audit entry were
 * fixed, Wide failed from 1.7 and Wider from 1.15 (see ADR 0058, decision 4).
 */
private const val WIDER_LIMIT_FONT_SCALE = 2f

/**
 * The Text spacing that the text is drawn with: [chosen], or Wide where [chosen] is Wider and the
 * [fontScale] has reached 2.0 (ADR 0058, decision 4). Normal and Wide are always drawn as they are.
 *
 * The scale is the one that the text is drawn at: the font scale of the system times the Text size
 * of the look, which is what `LocalDensity.current.fontScale` holds under [BorealisTheme]. What
 * overflows is the text at the size that is drawn, so the Text size counts as much as the system
 * does: Largest text over a system font of 1.3 is 1.69, and Wider is drawn as it is. What was
 * chosen is never changed, so it comes back when the scale falls again.
 */
fun effectiveTextSpacing(chosen: TextSpacing, fontScale: Float): TextSpacing =
    if (chosen == TextSpacing.Wider && fontScale >= WIDER_LIMIT_FONT_SCALE) {
        TextSpacing.Wide
    } else {
        chosen
    }
