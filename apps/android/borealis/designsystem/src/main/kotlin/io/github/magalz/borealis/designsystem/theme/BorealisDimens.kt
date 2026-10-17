package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.unit.Dp
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

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
    fun dialogWidth(width: DialogWidth, windowWidth: Dp): Dp = Dp.Unspecified

    /**
     * The width of a choice or action sheet that is a dialog (a TV or a handheld) in a window
     * [windowWidth] wide. A TV takes the compact dialog width. A handheld takes a fraction of the
     * window, between the compact and standard dialog widths, so a small screen does not get a
     * narrow column and a large one does not get a stretched one.
     */
    fun sheetWidth(formFactor: FormFactor, windowWidth: Dp): Dp = Dp.Unspecified
}
