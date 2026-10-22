package io.github.magalz.borealis.designsystem.adaptive

import io.github.magalz.borealis.designsystem.theme.CardDensity
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSpacing

private const val APP_COLUMNS_TV = 5
private const val APP_COLUMNS_WIDE = 4
private const val APP_COLUMNS_NARROW_PHONE = 3
private const val HOST_COLUMNS_TV = 3
private const val HOST_COLUMNS_WIDE = 2
private const val HOST_COLUMNS_NARROW_PHONE = 1

/**
 * The columns that a denser layout adds to every device: one in Compact and Dense, none in
 * Comfortable. None at Wider either, whatever the density: the words of a card are spaced half an
 * em apart there, and the card needs the width of the column that Comfortable keeps (in the device
 * round, host names broke inside a word, an address wrapped before its last digit and a chip took
 * two lines). Dense keeps its other changes: the padding of a card, the cover of the Continue card
 * and the height of a card on a TV. [textSpacing] is the level that the viewer chose.
 */
private fun extraColumns(density: CardDensity, textSpacing: TextSpacing): Int =
    if (density == CardDensity.Comfortable || textSpacing == TextSpacing.Wider) 0 else 1

/**
 * Columns of the app grid: 5 on a TV (ten covers on two rows at 960dp) and 4 on a handheld (the
 * gamepad layout of the proposal). A phone has 3, and 4 from [WIDE_WINDOW_DP] so a tablet keeps the
 * cover size. Compact and Dense add one column to each (ADR 0058, decision 7), except at Wider.
 *
 * Focused items draw an outline about 5dp outside themselves, 7dp with the thicker outline, and a
 * scaled tile a little more (2 percent of its size on each side), so the grid needs
 * `contentPadding` of at least 12dp and spacing of at least 10dp or the outline is clipped and
 * overlaps. The padding of the grids is the gutter of the screen in every density, which is more
 * than that.
 */
fun appGridColumns(
    formFactor: FormFactor,
    widthDp: Int,
    density: CardDensity,
    textSpacing: TextSpacing,
): Int =
    extraColumns(density, textSpacing) +
        when (formFactor) {
            FormFactor.Tv -> APP_COLUMNS_TV
            FormFactor.Handheld -> APP_COLUMNS_WIDE
            FormFactor.Phone ->
                if (widthDp >= WIDE_WINDOW_DP) APP_COLUMNS_WIDE else APP_COLUMNS_NARROW_PHONE
        }

/**
 * Columns of the host grid: 3 on a TV (the capture shows three cards beside the rail), 2 on a
 * handheld, and on a phone 1, or 2 from [WIDE_WINDOW_DP]. Compact and Dense add one column to each
 * (ADR 0058, decision 7), except at Wider.
 *
 * Focused items draw an outline about 5dp outside themselves, 7dp with the thicker outline, so the
 * grid needs `contentPadding` of at least 8dp and spacing of at least 10dp or the outline is
 * clipped and overlaps.
 */
fun hostGridColumns(
    formFactor: FormFactor,
    widthDp: Int,
    density: CardDensity,
    textSpacing: TextSpacing,
): Int =
    extraColumns(density, textSpacing) +
        when (formFactor) {
            FormFactor.Tv -> HOST_COLUMNS_TV
            FormFactor.Handheld -> HOST_COLUMNS_WIDE
            FormFactor.Phone ->
                if (widthDp >= WIDE_WINDOW_DP) HOST_COLUMNS_WIDE else HOST_COLUMNS_NARROW_PHONE
        }
