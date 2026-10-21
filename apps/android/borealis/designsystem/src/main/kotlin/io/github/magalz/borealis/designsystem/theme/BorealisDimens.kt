package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import kotlin.math.roundToInt

/**
 * The width of a dialog. Every dialog takes one of these three, so dialogs line up across screens.
 */
enum class DialogWidth(val width: Dp) {
    /** A question and its answer. */
    Compact(BorealisTokens.Size.Dialog.Compact),

    /** A short form or a list. */
    Standard(BorealisTokens.Size.Dialog.Standard),

    /** Content that needs a wide line. */
    Wide(BorealisTokens.Size.Dialog.Wide),
}

/** Layout sizes that screens share, from the design tokens. */
object BorealisDimens {
    /**
     * The widest content of a full screen: 560dp. Screens used to cap at 440, 460, 560 and 720;
     * they all take this one, and a wider window centres the content.
     */
    val contentMaxWidth: Dp = BorealisTokens.Size.ContentMax

    /**
     * The padding at each side of a phone bar: 4dp. The back arrow is a 24dp icon in a 48dp touch
     * target, so with this padding the arrow lines up with the 16dp gutter of a phone.
     */
    val phoneBarPadding: Dp = 4.dp

    /**
     * The space between the back arrow, the title and the actions of a phone bar: 4dp, as a
     * Material 3 top app bar leaves between its navigation icon and its title.
     */
    val phoneBarGap: Dp = 4.dp

    /**
     * The space between two lines of text that belong together, a title and the line under it or a
     * label and its value: 4dp.
     */
    val stackedTextGap: Dp = 4.dp

    /** How far the focus outline reaches outside its element: its offset and its stroke, 5dp. */
    val outlineReach: Dp = BorealisTokens.Focus.Offset + BorealisTokens.Focus.Outline

    /**
     * How far the thicker focus outline (the Thicker focus setting, and high contrast) reaches
     * outside its element: its offset and its stroke, 7dp.
     */
    val thickOutlineReach: Dp = BorealisTokens.Focus.Offset + BorealisTokens.Focus.OutlineThick

    /**
     * The padding around a list of focusable rows, 8dp: a scroll area or a panel clips what is
     * outside it, and the outline of the first and the last row reaches [outlineReach] beyond them,
     * or [thickOutlineReach] when it is the thicker one.
     */
    val listPadding: Dp = 8.dp

    /**
     * The gap between two focusable rows, 10dp: the outlines of neighbours, each [outlineReach]
     * wide, would overlap in a smaller one. The thicker outline, [thickOutlineReach] wide, still
     * does not reach the next row.
     */
    val listGap: Dp = outlineReach * 2

    /**
     * The width of the navigation rail and of the settings group list: 220dp on a TV, 200dp on a
     * handheld. A phone has no rail; it gets the handheld value, for a layout that borrows it.
     */
    fun navWidth(formFactor: FormFactor): Dp =
        when (formFactor) {
            FormFactor.Tv -> BorealisTokens.Size.Nav.Tv
            FormFactor.Handheld,
            FormFactor.Phone -> BorealisTokens.Size.Nav.Handheld
        }

    /**
     * The width of a [width] dialog in a window [windowWidth] wide: the token, but never closer
     * than the dialog margin to either edge, so on a phone it is the window less 2 x 16dp.
     */
    fun dialogWidth(width: DialogWidth, windowWidth: Dp): Dp =
        minOf(width.width, windowWidth - BorealisTokens.Space.DialogMargin * 2)

    /**
     * The width of a choice or action sheet that is a dialog (a TV or a handheld) in a window
     * [windowWidth] wide. A TV takes the compact dialog width. A handheld takes a fraction of the
     * window, between the compact and standard dialog widths, so a small screen does not get a
     * narrow column and a large one does not get a stretched one.
     */
    fun sheetWidth(formFactor: FormFactor, windowWidth: Dp): Dp {
        val compact = DialogWidth.Compact.width
        val width =
            if (formFactor == FormFactor.Handheld) {
                // Whole dp, so the sheet does not land on a fraction of a pixel.
                (windowWidth.value * BorealisTokens.Size.Sheet.HandheldFraction)
                    .roundToInt()
                    .dp
                    .coerceIn(compact, DialogWidth.Standard.width)
            } else {
                compact
            }
        return minOf(width, windowWidth - BorealisTokens.Space.DialogMargin * 2)
    }
}

/** The width of the window, in dp. */
@Composable
internal fun windowWidth(): Dp {
    val density = LocalDensity.current
    return with(density) { LocalWindowInfo.current.containerSize.width.toDp() }
}

/** The width of a choice or action sheet that is a dialog, here: [BorealisDimens.sheetWidth]. */
@Composable
internal fun sheetWidth(): Dp = BorealisDimens.sheetWidth(LocalFormFactor.current, windowWidth())
