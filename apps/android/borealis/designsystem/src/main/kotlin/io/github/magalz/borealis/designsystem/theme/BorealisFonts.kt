package io.github.magalz.borealis.designsystem.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * Every font family of the app: IBM Plex Sans for UI text and IBM Plex Mono for data, codes and PIN
 * digits (SIL OFL 1.1; the files and licence are in `design/fonts/ibm-plex`). Each family has the
 * regular, medium and semibold weights of the `font.weight` tokens.
 */
object BorealisFonts {
    private val regular = BorealisTokens.FontWeight.Regular
    private val medium = BorealisTokens.FontWeight.Medium
    private val semibold = BorealisTokens.FontWeight.Semibold

    val Sans: FontFamily =
        FontFamily(
            Font(R.font.ibm_plex_sans_regular, regular),
            Font(R.font.ibm_plex_sans_medium, medium),
            Font(R.font.ibm_plex_sans_semibold, semibold),
        )

    val Mono: FontFamily =
        FontFamily(
            Font(R.font.ibm_plex_mono_regular, regular),
            Font(R.font.ibm_plex_mono_medium, medium),
            Font(R.font.ibm_plex_mono_semibold, semibold),
        )
}
