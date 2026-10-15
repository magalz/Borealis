package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/** The text styles Material's [Typography] has no slot for. */
@Immutable class BorealisType(val kicker: TextStyle, val pin: TextStyle, val rail: TextStyle)

private val KICKER_SPACING = 0.07.em

private fun text(
    size: TextUnit,
    weight: FontWeight = BorealisTokens.FontWeight.Regular,
    family: FontFamily = BorealisFonts.Sans,
) = TextStyle(fontFamily = family, fontSize = size, fontWeight = weight)

/** The Material type scale of a form factor; handheld uses the TV scale. */
fun borealisTypography(formFactor: FormFactor): Typography {
    val base = Typography()
    val semibold = BorealisTokens.FontWeight.Semibold
    val medium = BorealisTokens.FontWeight.Medium
    val scale =
        when (formFactor) {
            FormFactor.Phone -> {
                val phone = BorealisTokens.Size.Phone
                base.copy(
                    titleLarge = text(phone.Title, semibold),
                    titleMedium = text(phone.Body, semibold),
                    titleSmall = text(phone.Secondary, semibold),
                    bodyLarge = text(phone.Body),
                    bodyMedium = text(phone.Body),
                    bodySmall = text(phone.Secondary),
                    labelLarge = text(phone.Body, medium),
                    labelMedium = text(phone.Secondary, medium),
                    labelSmall = text(phone.Kicker, semibold).copy(letterSpacing = KICKER_SPACING),
                )
            }
            FormFactor.Tv,
            FormFactor.Handheld -> {
                val tv = BorealisTokens.Size.Tv
                base.copy(
                    titleLarge = text(tv.Title, semibold),
                    titleMedium = text(tv.Body, semibold),
                    titleSmall = text(tv.Rail, medium),
                    bodyLarge = text(tv.Body),
                    bodyMedium = text(tv.Body),
                    bodySmall = text(tv.Rail),
                    labelLarge = text(tv.Body, medium),
                    labelMedium = text(tv.Rail, medium),
                    labelSmall = text(tv.Minimum, semibold).copy(letterSpacing = KICKER_SPACING),
                )
            }
        }
    // The display and headline slots keep Material's sizes, which are far above any minimum.
    return scale.copy(
        displayLarge = scale.displayLarge.copy(fontFamily = BorealisFonts.Sans),
        displayMedium = scale.displayMedium.copy(fontFamily = BorealisFonts.Sans),
        displaySmall = scale.displaySmall.copy(fontFamily = BorealisFonts.Sans),
        headlineLarge = scale.headlineLarge.copy(fontFamily = BorealisFonts.Sans),
        headlineMedium = scale.headlineMedium.copy(fontFamily = BorealisFonts.Sans),
        headlineSmall = scale.headlineSmall.copy(fontFamily = BorealisFonts.Sans),
    )
}

fun borealisType(formFactor: FormFactor): BorealisType {
    val kickerSize =
        if (formFactor == FormFactor.Phone) {
            BorealisTokens.Size.Phone.Kicker
        } else {
            BorealisTokens.Size.Tv.Minimum
        }
    return BorealisType(
        kicker =
            text(kickerSize, BorealisTokens.FontWeight.Semibold)
                .copy(letterSpacing = KICKER_SPACING, color = BorealisTokens.Color.Ink4),
        pin =
            text(
                BorealisTokens.Size.Phone.Pin,
                BorealisTokens.FontWeight.Semibold,
                BorealisFonts.Mono,
            ),
        rail = text(BorealisTokens.Size.Tv.Rail, BorealisTokens.FontWeight.Medium),
    )
}

val LocalBorealisType = staticCompositionLocalOf { borealisType(FormFactor.Phone) }
