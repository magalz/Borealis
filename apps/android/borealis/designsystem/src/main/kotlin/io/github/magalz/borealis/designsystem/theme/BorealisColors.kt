package io.github.magalz.borealis.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens

/**
 * Every colour token, for what Material's [ColorScheme] has no slot for: the ink steps, the accent
 * family, the status colours and the hairlines.
 */
@Immutable
class BorealisColors {
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
    val ink3 = tokens.Ink3
    val ink4 = tokens.Ink4
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
}

val LocalBorealisColors = staticCompositionLocalOf { BorealisColors() }

/** The dark Material 3 scheme of Borealis (ADR 0052, 0053). */
fun borealisColorScheme(): ColorScheme = TODO("not implemented")
