package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

private val tokens = BorealisTokens.Color
private val highContrast = BorealisTokens.HighContrast

/**
 * Every colour token, for what Material's [ColorScheme] has no slot for: the ink steps, the accent
 * family, the status colours and the hairlines. Each role is a parameter that defaults to its
 * token, so a variant of the palette is a [copy] of it that changes some roles and keeps the rest.
 *
 * Three things change roles, and they compose because each is a [copy]: the glass surface replaces
 * [ink3] and [ink4], to keep 4.5:1 over the background that shows through it ([withSecondaryInk]);
 * high contrast replaces the text and the borders ([withHighContrast]); and a colour-blind palette
 * replaces the status roles ([withColourVision]). [borealisColors] gives those of a look.
 */
@Immutable
@Suppress("LongParameterList") // one parameter for each colour token
data class BorealisColors(
    val page: Color = tokens.Page,
    val chrome: Color = tokens.Chrome,
    val surface: Color = tokens.Surface,
    val surfaceRaised: Color = tokens.SurfaceRaised,
    val line: Color = tokens.Line,
    val lineStrong: Color = tokens.LineStrong,
    val hover: Color = tokens.Hover,
    val scrim: Color = tokens.Scrim,
    val ink: Color = tokens.Ink,
    val ink2: Color = tokens.Ink2,
    val ink3: Color = tokens.Ink3,
    val ink4: Color = tokens.Ink4,
    val accent: Color = tokens.Accent,
    val accentSoft: Color = tokens.AccentSoft,
    val accentStrong: Color = tokens.AccentStrong,
    val onAccent: Color = tokens.OnAccent,
    val accentTint: Color = tokens.AccentTint,
    val accentLine: Color = tokens.AccentLine,
    val success: Color = tokens.Success,
    val successInk: Color = tokens.SuccessInk,
    val warning: Color = tokens.Warning,
    val warningInk: Color = tokens.WarningInk,
    val danger: Color = tokens.Danger,
    val dangerInk: Color = tokens.DangerInk,
    val offline: Color = tokens.Offline,
) {
    /** These colours with the roles [ink3] and [ink4] replaced; the rest stays as it is. */
    fun withSecondaryInk(ink3: Color, ink4: Color) = copy(ink3 = ink3, ink4 = ink4)

    /**
     * These colours as high contrast has them: the text at 7:1 or more on the page and the
     * surfaces, the borders opaque and at 3:1 or more, and the offline indicator at 3:1. The page,
     * the surfaces and the roles that it does not name stay as they are. Apply a palette after it:
     * the palettes keep the status text at 7:1 too.
     */
    fun withHighContrast() =
        copy(
            ink = highContrast.Ink,
            ink2 = highContrast.Ink2,
            ink3 = highContrast.Ink3,
            ink4 = highContrast.Ink4,
            accent = highContrast.Accent,
            accentSoft = highContrast.AccentSoft,
            successInk = highContrast.SuccessInk,
            dangerInk = highContrast.DangerInk,
            line = highContrast.Line,
            lineStrong = highContrast.LineStrong,
            accentLine = highContrast.AccentLine,
            offline = highContrast.Offline,
        )

    /** These colours with the status roles of the palette [vision]; [ColourVision.Off] is none. */
    fun withColourVision(vision: ColourVision): BorealisColors =
        when (vision) {
            ColourVision.Off -> this
            ColourVision.RedGreen ->
                with(BorealisTokens.ColourVision.RedGreen) {
                    copy(
                        success = Success,
                        successInk = SuccessInk,
                        warning = Warning,
                        warningInk = WarningInk,
                        danger = Danger,
                        dangerInk = DangerInk,
                        offline = Offline,
                    )
                }
            ColourVision.BlueYellow ->
                with(BorealisTokens.ColourVision.BlueYellow) {
                    copy(
                        success = Success,
                        successInk = SuccessInk,
                        warning = Warning,
                        warningInk = WarningInk,
                        danger = Danger,
                        dangerInk = DangerInk,
                        offline = Offline,
                    )
                }
        }
}

/** The colours that a [look] gives: the high contrast ones when it is on, then its palette. */
fun borealisColors(look: BorealisLook): BorealisColors {
    val colors = if (look.highContrast) BorealisColors().withHighContrast() else BorealisColors()
    return colors.withColourVision(look.colourVision)
}

val LocalBorealisColors = staticCompositionLocalOf { BorealisColors() }

/**
 * The dark Material 3 scheme of Borealis (ADR 0052, 0053).
 *
 * Accent-strong carries no text (token rule), so the primary container is a raised surface with
 * accent-soft text. [ColorScheme.inversePrimary] is the one place accent-strong is text: it sits on
 * the light inverse surface (ink), where it has 4.7:1 and accent-soft would have 1.5:1.
 */
fun borealisColorScheme(): ColorScheme {
    val colors = BorealisColors()
    return darkColorScheme(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.surfaceRaised,
        onPrimaryContainer = colors.accentSoft,
        inversePrimary = colors.accentStrong,
        secondary = colors.accentSoft,
        onSecondary = colors.onAccent,
        secondaryContainer = colors.surfaceRaised,
        onSecondaryContainer = colors.ink,
        tertiary = colors.accentSoft,
        onTertiary = colors.onAccent,
        tertiaryContainer = colors.surfaceRaised,
        onTertiaryContainer = colors.ink,
        background = colors.page,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.surfaceRaised,
        onSurfaceVariant = colors.ink2,
        surfaceTint = colors.accent,
        inverseSurface = colors.ink,
        inverseOnSurface = colors.page,
        error = colors.dangerInk,
        onError = colors.page,
        errorContainer = colors.surfaceRaised,
        onErrorContainer = colors.dangerInk,
        outline = colors.lineStrong,
        outlineVariant = colors.line,
        scrim = colors.scrim,
        surfaceBright = colors.surfaceRaised,
        surfaceDim = colors.page,
        surfaceContainerLowest = colors.page,
        surfaceContainerLow = colors.surface,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surfaceRaised,
        surfaceContainerHighest = colors.surfaceRaised,
    )
}
