package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.isUnspecified
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * How the codes are drawn: in [family], at [ratio] of the size of the text, never below [floor].
 */
internal class CodesFont(val family: FontFamily, val ratio: Float, val floor: TextUnit)

/**
 * The text styles Material's [Typography] has no slot for, and the [codes] of the font of the look.
 * The codes are the PIN, addresses, chips (the key shortcut chips among them) and the performance
 * overlay (ADR 0058, decision 2). The keys of the on-screen keyboard are in the text font.
 */
@Immutable
class BorealisType
internal constructor(
    val kicker: TextStyle,
    val pin: TextStyle,
    val rail: TextStyle,
    private val codesFont: CodesFont,
    private val spacing: TextSpacing,
) {
    /**
     * [style], a style of the type scale, drawn in the font of the codes. Its size is that of
     * [style] times the ratio of the scale of the codes to the scale of the text, so that the codes
     * have the x-height of the text around them, and never goes below the minimum of the form
     * factor. The rest of [style] (weight, spacing, colour) stays.
     */
    fun codes(style: TextStyle): TextStyle {
        if (style.fontSize.isUnspecified) return style.copy(fontFamily = codesFont.family)
        val size = style.fontSize * codesFont.ratio
        val floor = codesFont.floor
        return style.copy(
            fontFamily = codesFont.family,
            fontSize = if (size < floor) floor else size,
        )
    }

    /**
     * [style] with at least the letter spacing and the line height of the Text spacing of the look:
     * for a style that a component builds itself, from the size and the family of another, so that
     * it does not drop what the type scale gave. At Normal it is [style] itself.
     */
    fun spaced(style: TextStyle): TextStyle = style.spaced(spacing)
}

private val KICKER_SPACING = 0.07.em

/** The least size of text on [formFactor]: the kicker of a phone, the minimum of the others. */
private fun minimumSize(formFactor: FormFactor): TextUnit =
    when (formFactor) {
        FormFactor.Phone -> BorealisTokens.Size.Phone.Kicker
        FormFactor.Tv -> BorealisTokens.Size.Tv.Minimum
        FormFactor.Handheld -> BorealisTokens.Size.Handheld.Minimum
    }

/** [value] in em of [size], or `null` when it has none or [size] does not allow the conversion. */
internal fun emOf(value: TextUnit, size: TextUnit): Float? =
    when {
        value.isUnspecified -> null
        value.type == TextUnitType.Em -> value.value
        size.isSpecified && size.type == TextUnitType.Sp -> value.value / size.value
        else -> null
    }

/**
 * This style with at least the letter spacing and the line height of [spacing]. A level is a
 * minimum in em of the font size of the style, so each value is compared in em: a style that has
 * more of its own keeps it, whatever its unit, and a style that has less (or none) gets the level.
 * [TextSpacing.Normal] gives the style back as it is.
 */
internal fun TextStyle.spaced(spacing: TextSpacing): TextStyle {
    if (spacing == TextSpacing.Normal) return this
    val ownLetter = emOf(letterSpacing, fontSize)
    val ownLine = emOf(lineHeight, fontSize)
    return copy(
        letterSpacing =
            if (ownLetter != null && ownLetter >= spacing.letterEm) letterSpacing
            else spacing.letterEm.em,
        lineHeight =
            if (ownLine != null && ownLine >= spacing.lineHeightEm) lineHeight
            else spacing.lineHeightEm.em,
    )
}

private fun Typography.spaced(spacing: TextSpacing): Typography =
    if (spacing == TextSpacing.Normal) this
    else
        copy(
            displayLarge = displayLarge.spaced(spacing),
            displayMedium = displayMedium.spaced(spacing),
            displaySmall = displaySmall.spaced(spacing),
            headlineLarge = headlineLarge.spaced(spacing),
            headlineMedium = headlineMedium.spaced(spacing),
            headlineSmall = headlineSmall.spaced(spacing),
            titleLarge = titleLarge.spaced(spacing),
            titleMedium = titleMedium.spaced(spacing),
            titleSmall = titleSmall.spaced(spacing),
            bodyLarge = bodyLarge.spaced(spacing),
            bodyMedium = bodyMedium.spaced(spacing),
            bodySmall = bodySmall.spaced(spacing),
            labelLarge = labelLarge.spaced(spacing),
            labelMedium = labelMedium.spaced(spacing),
            labelSmall = labelSmall.spaced(spacing),
        )

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
 * The Material type scale of a form factor, in [font] and at [spacing] (ADR 0058, decisions 2 and
 * 4). A handheld has the title and the rail of the TV and the body and the minimum of its own: a TV
 * is read at 3 m, a handheld in the hands. Each size is scaled to the x-height of the font and
 * never goes below the minimum of the form factor. Every style has at least the letter spacing and
 * the line height of [spacing].
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
    return scale
        .copy(
            displayLarge = scale.displayLarge.copy(fontFamily = fonts.text),
            displayMedium = scale.displayMedium.copy(fontFamily = fonts.text),
            displaySmall = scale.displaySmall.copy(fontFamily = fonts.text),
            headlineLarge = scale.headlineLarge.copy(fontFamily = fonts.text),
            headlineMedium = scale.headlineMedium.copy(fontFamily = fonts.text),
            headlineSmall = scale.headlineSmall.copy(fontFamily = fonts.text),
        )
        .spaced(spacing)
}

/**
 * The kicker is the minimum text of a form factor: 11sp on a phone, 12sp on a handheld and 13sp on
 * a TV, scaled to the x-height of [font] and never below that minimum. The PIN is in the font of
 * the codes. Every style has at least the letter spacing and the line height of [spacing].
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
                .copy(letterSpacing = KICKER_SPACING, color = BorealisTokens.Color.Ink4)
                .spaced(spacing),
        pin =
            sizing
                .codes(BorealisTokens.Size.Phone.Pin, BorealisTokens.FontWeight.Semibold)
                .spaced(spacing),
        rail =
            sizing
                .text(BorealisTokens.Size.Tv.Rail, BorealisTokens.FontWeight.Medium)
                .spaced(spacing),
        codesFont = CodesFont(fonts.codes, fonts.codesScale / fonts.textScale, floor),
        spacing = spacing,
    )
}

val LocalBorealisType = staticCompositionLocalOf { borealisType(FormFactor.Phone) }
