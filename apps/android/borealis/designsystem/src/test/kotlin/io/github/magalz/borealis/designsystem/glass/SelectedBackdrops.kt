package io.github.magalz.borealis.designsystem.glass

import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.ColourVision

/** The brightest backdrop behind a selected label: at the default glass, and at Reduced. */
internal class Laid(val standard: Double, val reduced: Double)

/**
 * The brightest pixel behind the label of a selected item, which is the glass with the tint of the
 * accent laid on it, over every frame of both devices, as `contrast.py accents` measured it (Table
 * W of `docs/ux/accessibility.md`), rounded up. A sheet is a glass panel over the blurred window,
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

/**
 * The same for the accents that have a variant under a colour-blind palette (ADR 0058, decision 5),
 * as `contrast.py accents --palette` measured them at `b327cd39`: the tint of a variant is not the
 * tint of its accent, so the backdrop behind its label is another one. The rose of violet under
 * Red-green is the brightest, 0.080 over Flowing light.
 */
internal val LAID_UNDER_PALETTE: Map<Pair<AccentChoice, ColourVision>, Map<BackgroundStyle, Laid>> =
    mapOf(
        (AccentChoice.Violet to ColourVision.RedGreen) to
            mapOf(
                BackgroundStyle.AuroraCurtains to Laid(0.04735, 0.03015),
                BackgroundStyle.SoftGlow to Laid(0.04298, 0.03027),
                BackgroundStyle.NorthernLights to Laid(0.06626, 0.03589),
                BackgroundStyle.LightMist to Laid(0.05790, 0.02979),
                BackgroundStyle.FlowingLight to Laid(0.08044, 0.03620),
                BackgroundStyle.Starfield to Laid(0.03803, 0.02769),
            ),
        (AccentChoice.Violet to ColourVision.BlueYellow) to
            mapOf(
                BackgroundStyle.AuroraCurtains to Laid(0.04057, 0.02462),
                BackgroundStyle.SoftGlow to Laid(0.03654, 0.02475),
                BackgroundStyle.NorthernLights to Laid(0.05729, 0.02990),
                BackgroundStyle.LightMist to Laid(0.05073, 0.02433),
                BackgroundStyle.FlowingLight to Laid(0.07114, 0.03008),
                BackgroundStyle.Starfield to Laid(0.03172, 0.02241),
            ),
        (AccentChoice.White to ColourVision.BlueYellow) to
            mapOf(
                BackgroundStyle.AuroraCurtains to Laid(0.05606, 0.03699),
                BackgroundStyle.SoftGlow to Laid(0.05114, 0.03710),
                BackgroundStyle.NorthernLights to Laid(0.07770, 0.04344),
                BackgroundStyle.LightMist to Laid(0.06752, 0.03657),
                BackgroundStyle.FlowingLight to Laid(0.09278, 0.04388),
                BackgroundStyle.Starfield to Laid(0.04578, 0.03420),
            ),
    )
