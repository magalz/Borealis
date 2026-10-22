package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isUnspecified
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * The text styles Material's [Typography] has no slot for, and the [codes] of the font of the look.
 * The codes are the PIN, addresses, keyboard keys, chips and the performance overlay (ADR 0058,
 * decision 2).
 */
@Immutable
class BorealisType
internal constructor(
    val kicker: TextStyle,
    val pin: TextStyle,
    val rail: TextStyle,
    private val codesFamily: FontFamily,
    private val codesRatio: Float,
    private val floor: TextUnit,
) {
    /**
     * [style], a style of the type scale, drawn in the font of the codes. Its size is that of
     * [style] times the ratio of the scale of the codes to the scale of the text, so that the codes
     * have the x-height of the text around them, and never goes below the minimum of the form
     * factor. The rest of [style] (weight, spacing, colour) stays.
     */
    fun codes(style: TextStyle): TextStyle {
        if (style.fontSize.isUnspecified) return style.copy(fontFamily = codesFamily)
        val size = style.fontSize * codesRatio
        return style.copy(fontFamily = codesFamily, fontSize = if (size < floor) floor else size)
    }
}

private val KICKER_SPACING = 0.07.em

/** The least size of text on [formFactor]: the kicker of a phone, the minimum of the others. */
private fun minimumSize(formFactor: FormFactor): TextUnit =
    when (formFactor) {
        FormFactor.Phone -> BorealisTokens.Size.Phone.Kicker
        FormFactor.Tv -> BorealisTokens.Size.Tv.Minimum
        FormFactor.Handheld -> BorealisTokens.Size.Handheld.Minimum
    }

/**
 * The styles of [fonts] on a form factor whose least size is [floor]: a size of a token is scaled
 * to the x-height of IBM Plex Sans and never goes below [floor]. A scale below 1 would otherwise
 * take the smallest text under its minimum.
 */
private class Sizing(private val fonts: BorealisFontSet, private val floor: TextUnit) {
    fun text(size: TextUnit, weight: FontWeight = BorealisTokens.FontWeight.Regular) =
        TextStyle(
            fontFamily = fonts.text,
            fontSize = scaled(size, fonts.textScale),
            fontWeight = weight,
        )

    fun codes(size: TextUnit, weight: FontWeight) =
        TextStyle(
            fontFamily = fonts.codes,
            fontSize = scaled(size, fonts.codesScale),
            fontWeight = weight,
        )

    private fun scaled(size: TextUnit, scale: Float): TextUnit {
        val scaled = size * scale
        return if (scaled < floor) floor else scaled
    }
}

/**
 * The Material type scale of a form factor, in [font] (ADR 0058, decision 2). A handheld has the
 * title and the rail of the TV and the body and the minimum of its own: a TV is read at 3 m, a
 * handheld in the hands. Each size is scaled to the x-height of the font and never goes below the
 * minimum of the form factor.
 */
fun borealisTypography(
    formFactor: FormFactor,
    font: FontChoice = FontChoice.Plex,
    spacing: TextSpacing = TextSpacing.Normal,
): Typography {
    val fonts = BorealisFonts.of(font)
    val sizing = Sizing(fonts, minimumSize(formFactor))
    val base = Typography()
    val semibold = BorealisTokens.FontWeight.Semibold
    val medium = BorealisTokens.FontWeight.Medium
    val scale =
        when (formFactor) {
            FormFactor.Phone -> {
                val phone = BorealisTokens.Size.Phone
                base.copy(
                    titleLarge = sizing.text(phone.Title, semibold),
                    titleMedium = sizing.text(phone.Body, semibold),
                    titleSmall = sizing.text(phone.Secondary, semibold),
                    bodyLarge = sizing.text(phone.Body),
                    bodyMedium = sizing.text(phone.Body),
                    bodySmall = sizing.text(phone.Secondary),
                    labelLarge = sizing.text(phone.Body, medium),
                    labelMedium = sizing.text(phone.Secondary, medium),
                    labelSmall =
                        sizing.text(phone.Kicker, semibold).copy(letterSpacing = KICKER_SPACING),
                )
            }
            FormFactor.Tv,
            FormFactor.Handheld -> {
                val tv = BorealisTokens.Size.Tv
                val handheld = BorealisTokens.Size.Handheld
                val onTv = formFactor == FormFactor.Tv
                val body = if (onTv) tv.Body else handheld.Body
                val minimum = if (onTv) tv.Minimum else handheld.Minimum
                base.copy(
                    titleLarge = sizing.text(tv.Title, semibold),
                    titleMedium = sizing.text(body, semibold),
                    titleSmall = sizing.text(tv.Rail, medium),
                    bodyLarge = sizing.text(body),
                    bodyMedium = sizing.text(body),
                    bodySmall = sizing.text(tv.Rail),
                    labelLarge = sizing.text(body, medium),
                    labelMedium = sizing.text(tv.Rail, medium),
                    labelSmall =
                        sizing.text(minimum, semibold).copy(letterSpacing = KICKER_SPACING),
                )
            }
        }
    // The display and headline slots keep Material's sizes, which are far above any minimum.
    return scale.copy(
        displayLarge = scale.displayLarge.copy(fontFamily = fonts.text),
        displayMedium = scale.displayMedium.copy(fontFamily = fonts.text),
        displaySmall = scale.displaySmall.copy(fontFamily = fonts.text),
        headlineLarge = scale.headlineLarge.copy(fontFamily = fonts.text),
        headlineMedium = scale.headlineMedium.copy(fontFamily = fonts.text),
        headlineSmall = scale.headlineSmall.copy(fontFamily = fonts.text),
    )
}

/**
 * The kicker is the minimum text of a form factor: 11sp on a phone, 12sp on a handheld and 13sp on
 * a TV, scaled to the x-height of [font] and never below that minimum. The PIN is in the font of
 * the codes.
 */
fun borealisType(
    formFactor: FormFactor,
    font: FontChoice = FontChoice.Plex,
    spacing: TextSpacing = TextSpacing.Normal,
): BorealisType {
    val fonts = BorealisFonts.of(font)
    val floor = minimumSize(formFactor)
    val sizing = Sizing(fonts, floor)
    return BorealisType(
        kicker =
            sizing
                .text(floor, BorealisTokens.FontWeight.Semibold)
                .copy(letterSpacing = KICKER_SPACING, color = BorealisTokens.Color.Ink4),
        pin = sizing.codes(BorealisTokens.Size.Phone.Pin, BorealisTokens.FontWeight.Semibold),
        rail = sizing.text(BorealisTokens.Size.Tv.Rail, BorealisTokens.FontWeight.Medium),
        codesFamily = fonts.codes,
        codesRatio = fonts.codesScale / fonts.textScale,
        floor = floor,
    )
}

val LocalBorealisType = staticCompositionLocalOf { borealisType(FormFactor.Phone) }

/** STUB, written with the tests: [style] with the letter spacing and line height of [spacing]. */
internal fun TextStyle.spaced(spacing: TextSpacing): TextStyle = this
