package io.github.magalz.borealis.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.magalz.borealis.designsystem.R
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * The fonts of one [FontChoice]: the family of the [text] and the family of the [codes] (the PIN,
 * addresses, chips, the key shortcut chips among them, and the performance overlay), and for each
 * the scale that gives it the x-height of IBM Plex Sans. The keys of the on-screen keyboard use the
 * [text] family, not the codes. A size drawn in the font is the size of the token times its scale
 * ([textScale] or [codesScale]), so that the fonts look equally large.
 */
@Immutable
class BorealisFontSet(
    val text: FontFamily,
    val textScale: Float,
    val codes: FontFamily,
    val codesScale: Float,
)

/**
 * Every font of the app, for the choice of the look (ADR 0058, decision 2). The files and licences
 * are in `design/fonts/<font>`. A weight that a font does not bundle is taken by Compose from the
 * nearest one: Medium is Regular in Andika, Luciole and B612, and SemiBold is Bold in the last two.
 * Andika 7.000 ships a Medium, and the app does not bundle it, to save about 0.4 MB.
 *
 * The scales are the drawn x-height of IBM Plex Sans (0.516 em) over that of the font, measured by
 * the contrast tool (docs/ux/accessibility.md, "The fonts and their scale"). The codes use the
 * monospaced companion of the font where it has one, and IBM Plex Mono, which is the baseline of
 * the scale, where it has not.
 */
object BorealisFonts {
    private val regular = BorealisTokens.FontWeight.Regular
    private val medium = BorealisTokens.FontWeight.Medium
    private val semibold = BorealisTokens.FontWeight.Semibold
    private val bold = FontWeight.Bold

    private val plexSans =
        FontFamily(
            Font(R.font.ibm_plex_sans_regular, regular),
            Font(R.font.ibm_plex_sans_medium, medium),
            Font(R.font.ibm_plex_sans_semibold, semibold),
        )

    private val plexMono =
        FontFamily(
            Font(R.font.ibm_plex_mono_regular, regular),
            Font(R.font.ibm_plex_mono_medium, medium),
            Font(R.font.ibm_plex_mono_semibold, semibold),
        )

    private val plex = BorealisFontSet(plexSans, 1f, plexMono, 1f)

    private val atkinson =
        BorealisFontSet(
            text =
                FontFamily(
                    Font(R.font.atkinson_next_regular, regular),
                    Font(R.font.atkinson_next_medium, medium),
                    Font(R.font.atkinson_next_semibold, semibold),
                ),
            textScale = 1.040f,
            codes =
                FontFamily(
                    Font(R.font.atkinson_mono_regular, regular),
                    Font(R.font.atkinson_mono_medium, medium),
                    Font(R.font.atkinson_mono_semibold, semibold),
                ),
            codesScale = 1.040f,
        )

    private val luciole =
        BorealisFontSet(
            text =
                FontFamily(Font(R.font.luciole_regular, regular), Font(R.font.luciole_bold, bold)),
            textScale = 0.947f,
            codes = plexMono,
            codesScale = 1f,
        )

    private val lexend =
        BorealisFontSet(
            text =
                FontFamily(
                    Font(R.font.lexend_regular, regular),
                    Font(R.font.lexend_medium, medium),
                    Font(R.font.lexend_semibold, semibold),
                ),
            textScale = 0.983f,
            codes = plexMono,
            codesScale = 1f,
        )

    // Andika 7.000 ships a Medium too; the app bundles Regular and SemiBold only, to save about 0.4
    // MB.
    private val andika =
        BorealisFontSet(
            text =
                FontFamily(
                    Font(R.font.andika_regular, regular),
                    Font(R.font.andika_semibold, semibold),
                ),
            textScale = 1.034f,
            codes = plexMono,
            codesScale = 1f,
        )

    private val b612 =
        BorealisFontSet(
            text = FontFamily(Font(R.font.b612_regular, regular), Font(R.font.b612_bold, bold)),
            textScale = 0.923f,
            codes =
                FontFamily(
                    Font(R.font.b612_mono_regular, regular),
                    Font(R.font.b612_mono_bold, bold),
                ),
            codesScale = 0.921f,
        )

    private val system = BorealisFontSet(FontFamily.Default, 1f, plexMono, 1f)

    /** The families and scales of [choice]. */
    fun of(choice: FontChoice): BorealisFontSet =
        when (choice) {
            FontChoice.Plex -> plex
            FontChoice.AtkinsonNext -> atkinson
            FontChoice.Luciole -> luciole
            FontChoice.Lexend -> lexend
            FontChoice.Andika -> andika
            FontChoice.B612 -> b612
            FontChoice.System -> system
        }
}
