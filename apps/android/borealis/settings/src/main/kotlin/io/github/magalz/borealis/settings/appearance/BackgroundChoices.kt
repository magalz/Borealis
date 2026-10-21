package io.github.magalz.borealis.settings.appearance

import io.github.magalz.borealis.designsystem.background.BackgroundStyle
import io.github.magalz.borealis.settings.catalog.Settings

/**
 * The background styles that the Appearance setting offers, and how [Settings.BACKGROUND] stores
 * them: by a stable string id, never by the ordinal of the [BackgroundStyle], so that reordering or
 * adding a style cannot change what a user chose.
 */
internal object BackgroundChoices {
    /** Every style, in the order of the Background row. The first is the default. */
    val all: LookList<BackgroundStyle> =
        LookList(
            Settings.BACKGROUND,
            listOf(
                LookChoice(Settings.BACKGROUND_AURORA_CURTAINS, BackgroundStyle.AuroraCurtains),
                LookChoice(Settings.BACKGROUND_SOFT_GLOW, BackgroundStyle.SoftGlow),
                LookChoice(Settings.BACKGROUND_NORTHERN_LIGHTS, BackgroundStyle.NorthernLights),
                LookChoice(Settings.BACKGROUND_LIGHT_MIST, BackgroundStyle.LightMist),
                LookChoice(Settings.BACKGROUND_FLOWING_LIGHT, BackgroundStyle.FlowingLight),
                LookChoice(Settings.BACKGROUND_STARFIELD, BackgroundStyle.Starfield),
                LookChoice(Settings.BACKGROUND_OFF, BackgroundStyle.Off),
            ),
        )
}
