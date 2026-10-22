package io.github.magalz.borealis.designsystem.glass

import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.AccentChoice

/** The brightest backdrop behind a selected label: at the default glass, and at Reduced. */
internal class Laid(val standard: Double, val reduced: Double)

/**
 * The brightest pixel behind the label of a selected item, which is the glass with the tint of the
 * accent laid on it, over every frame of both devices, as `contrast.py accents` measured it (Table
 * V of `docs/ux/accessibility.md`), rounded up. A sheet is a glass panel over the blurred window,
 * so the label of its selected option has the same backdrop.
 */
internal val LAID: Map<AccentChoice, Map<BackgroundStyle, Laid>> =
    mapOf(
        AccentChoice.Violet to
            mapOf(
                BackgroundStyle.AuroraCurtains to Laid(0.03958, 0.02392),
                BackgroundStyle.SoftGlow to Laid(0.03557, 0.02404),
                BackgroundStyle.NorthernLights to Laid(0.05661, 0.02911),
                BackgroundStyle.LightMist to Laid(0.04954, 0.02363),
                BackgroundStyle.FlowingLight to Laid(0.07008, 0.02934),
                BackgroundStyle.Starfield to Laid(0.03089, 0.02175),
            ),
        AccentChoice.Teal to
            mapOf(
                BackgroundStyle.AuroraCurtains to Laid(0.04789, 0.03112),
                BackgroundStyle.SoftGlow to Laid(0.04330, 0.03113),
                BackgroundStyle.NorthernLights to Laid(0.07008, 0.03685),
                BackgroundStyle.LightMist to Laid(0.05797, 0.03071),
                BackgroundStyle.FlowingLight to Laid(0.08280, 0.03748),
                BackgroundStyle.Starfield to Laid(0.03888, 0.02864),
            ),
        AccentChoice.White to
            mapOf(
                BackgroundStyle.AuroraCurtains to Laid(0.05605, 0.03699),
                BackgroundStyle.SoftGlow to Laid(0.05114, 0.03709),
                BackgroundStyle.NorthernLights to Laid(0.07771, 0.04344),
                BackgroundStyle.LightMist to Laid(0.06752, 0.03657),
                BackgroundStyle.FlowingLight to Laid(0.09278, 0.04387),
                BackgroundStyle.Starfield to Laid(0.04578, 0.03420),
            ),
    )
