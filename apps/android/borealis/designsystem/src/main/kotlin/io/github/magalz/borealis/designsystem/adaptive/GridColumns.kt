package io.github.magalz.borealis.designsystem.adaptive

import io.github.magalz.borealis.designsystem.theme.FormFactor

private const val APP_COLUMNS_TV = 5
private const val APP_COLUMNS_WIDE = 4
private const val APP_COLUMNS_NARROW_PHONE = 3
private const val HOST_COLUMNS_TV = 3
private const val HOST_COLUMNS_WIDE = 2
private const val HOST_COLUMNS_NARROW_PHONE = 1

/**
 * Columns of the app grid: 5 on a TV (ten covers on two rows at 960dp) and 4 on a handheld (the
 * gamepad layout of the proposal). A phone has 3, and 4 from [WIDE_WINDOW_DP] so a tablet keeps the
 * cover size.
 *
 * Focused items draw an outline about 5dp outside themselves (a scaled tile a little more), so the
 * grid needs `contentPadding` and spacing of at least 10dp or the outline is clipped and overlaps.
 */
fun appGridColumns(formFactor: FormFactor, widthDp: Int): Int =
    when (formFactor) {
        FormFactor.Tv -> APP_COLUMNS_TV
        FormFactor.Handheld -> APP_COLUMNS_WIDE
        FormFactor.Phone ->
            if (widthDp >= WIDE_WINDOW_DP) APP_COLUMNS_WIDE else APP_COLUMNS_NARROW_PHONE
    }

/**
 * Columns of the host grid: 3 on a TV (the capture shows three cards beside the rail), 2 on a
 * handheld, and on a phone 1, or 2 from [WIDE_WINDOW_DP].
 *
 * Focused items draw an outline about 5dp outside themselves, so the grid needs `contentPadding`
 * and spacing of at least 10dp or the outline is clipped and overlaps.
 */
fun hostGridColumns(formFactor: FormFactor, widthDp: Int): Int =
    when (formFactor) {
        FormFactor.Tv -> HOST_COLUMNS_TV
        FormFactor.Handheld -> HOST_COLUMNS_WIDE
        FormFactor.Phone ->
            if (widthDp >= WIDE_WINDOW_DP) HOST_COLUMNS_WIDE else HOST_COLUMNS_NARROW_PHONE
    }
