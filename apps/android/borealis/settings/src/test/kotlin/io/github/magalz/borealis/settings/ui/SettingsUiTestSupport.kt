@file:OptIn(ExperimentalCoroutinesApi::class)

package io.github.magalz.borealis.settings.ui

import io.github.magalz.borealis.settings.options.FrameRateChoice
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.testing.FakeSettingsOptions
import kotlinx.coroutines.ExperimentalCoroutinesApi

/** Robolectric qualifiers of the devices that the settings screen is laid out for. */
const val PHONE = "w360dp-h640dp-xhdpi"
const val TV = "w960dp-h540dp-land-television-xhdpi"
const val HANDHELD = "w800dp-h480dp-land-hdpi-notouch"

/** The standard resolutions that a recent display and decoder offer. */
val STANDARD_RESOLUTIONS =
    listOf(
        ResolutionChoice("1280x720", 1280, 720, ResolutionKind.STANDARD),
        ResolutionChoice("1920x1080", 1920, 1080, ResolutionKind.STANDARD),
        ResolutionChoice("2560x1440", 2560, 1440, ResolutionKind.STANDARD),
        ResolutionChoice("3840x2160", 3840, 2160, ResolutionKind.STANDARD),
    )

/**
 * A [FakeSettingsOptions] that offers [STANDARD_RESOLUTIONS] and 30, 60, 90 and 120 fps unlocked.
 */
fun fakeOptions(): FakeSettingsOptions =
    FakeSettingsOptions().apply {
        resolutions = STANDARD_RESOLUTIONS
        frameRates = listOf(FrameRateChoice(30, false), FrameRateChoice(60, false))
    }

/** The names of the seven background styles, in the order of the Background row. */
val BACKGROUND_NAMES =
    listOf(
        "Aurora curtains",
        "Soft glow",
        "Northern lights",
        "Light mist",
        "Flowing light",
        "Starfield",
        "Off",
    )
