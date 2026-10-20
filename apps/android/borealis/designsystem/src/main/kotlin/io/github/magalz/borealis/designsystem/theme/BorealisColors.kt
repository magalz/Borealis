package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * Every colour token, for what Material's [ColorScheme] has no slot for: the ink steps, the accent
 * family, the status colours and the hairlines.
 *
 * [ink3] and [ink4] are the two roles that a glass surface replaces, to keep 4.5:1 over the
 * background that shows through it ([withSecondaryInk]).
 */
@Immutable
class BorealisColors(
    val ink3: Color = BorealisTokens.Color.Ink3,
    val ink4: Color = BorealisTokens.Color.Ink4,
) {
    private val tokens = BorealisTokens.Color

    val page = tokens.Page
    val chrome = tokens.Chrome
    val surface = tokens.Surface
    val surfaceRaised = tokens.SurfaceRaised
    val line = tokens.Line
    val lineStrong = tokens.LineStrong
    val hover = tokens.Hover
    val scrim = tokens.Scrim
    val ink = tokens.Ink
    val ink2 = tokens.Ink2
    val accent = tokens.Accent
    val accentSoft = tokens.AccentSoft
    val accentStrong = tokens.AccentStrong
    val onAccent = tokens.OnAccent
    val accentTint = tokens.AccentTint
    val accentLine = tokens.AccentLine
    val success = tokens.Success
    val successInk = tokens.SuccessInk
    val warning = tokens.Warning
    val warningInk = tokens.WarningInk
    val danger = tokens.Danger
    val dangerInk = tokens.DangerInk
    val offline = tokens.Offline

    /** These colours with the roles [ink3] and [ink4] replaced; the rest stays as it is. */
    fun withSecondaryInk(ink3: Color, ink4: Color) = BorealisColors(ink3, ink4)
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
