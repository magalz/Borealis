package io.github.magalz.borealis.settings.ui

import androidx.annotation.StringRes
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.appearance.BackgroundChoices
import io.github.magalz.borealis.settings.appearance.LookLists
import io.github.magalz.borealis.settings.catalog.Settings

/**
 * One entry of a list whose entries do not depend on the device. The label is [labelRes], with
 * [labelArg] as its number when it has one ("40 %").
 */
class FixedChoice(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int? = null,
    val labelArg: Int? = null,
)

/** The lists of the settings screen whose entries are the same on every device. */
object FixedChoices {
    /** The stored value of Touch for a trackpad ([Settings.TOUCHSCREEN_TRACKPAD] is on). */
    const val TOUCH_TRACKPAD = "trackpad"

    /** The stored value of Touch for a direct touch ([Settings.TOUCHSCREEN_TRACKPAD] is off). */
    const val TOUCH_DIRECT = "direct"

    /** Frame pacing. */
    val FRAME_PACING =
        listOf(
            FixedChoice("latency", R.string.settings_choice_pacing_latency),
            FixedChoice("balanced", R.string.settings_choice_pacing_balanced),
            FixedChoice("cap-fps", R.string.settings_choice_pacing_balanced_cap),
            FixedChoice(
                "smoothness",
                R.string.settings_choice_pacing_smoothness,
                R.string.settings_choice_pacing_smoothness_summary,
            ),
        )

    /** Codec. */
    val CODEC =
        listOf(
            FixedChoice(
                "auto",
                R.string.settings_choice_codec_auto,
                R.string.settings_choice_codec_auto_summary,
            ),
            FixedChoice(
                "forceav1",
                R.string.settings_choice_codec_av1,
                R.string.settings_choice_codec_av1_summary,
            ),
            FixedChoice("forceh265", R.string.settings_choice_codec_hevc),
            FixedChoice("neverh265", R.string.settings_choice_codec_h264),
        )

    /** Performance overlay. */
    val PERF_OVERLAY =
        listOf(
            FixedChoice(Settings.PERF_OVERLAY_OFF, R.string.settings_choice_overlay_off),
            FixedChoice(Settings.PERF_OVERLAY_COMPACT, R.string.settings_choice_overlay_compact),
            FixedChoice(Settings.PERF_OVERLAY_DETAILED, R.string.settings_choice_overlay_detailed),
        )

    /** Background: the styles of the Appearance setting, with their stable ids. */
    val BACKGROUND = BackgroundChoices.all.choices.map { FixedChoice(it.id, it.value.label) }

    /** Glass tint: 30 to 85 percent, with the percent as the stored id. */
    val GLASS_TINT =
        LookLists.GLASS_TINT.choices.map {
            FixedChoice(it.id, R.string.settings_value_percent, labelArg = it.value)
        }

    /** Glass blur: 8 to 48 dp, with the dp as the stored id. */
    val GLASS_BLUR =
        LookLists.GLASS_BLUR.choices.map {
            FixedChoice(it.id, R.string.settings_value_dp, labelArg = it.value)
        }

    /** Reduce motion. */
    val REDUCE_MOTION =
        listOf(
            FixedChoice(
                Settings.REDUCE_MOTION_FOLLOW_SYSTEM,
                R.string.settings_choice_motion_follow,
            ),
            FixedChoice(Settings.REDUCE_MOTION_ON, R.string.settings_choice_motion_on),
            FixedChoice(Settings.REDUCE_MOTION_OFF, R.string.settings_choice_motion_off),
        )

    /** Transparency. */
    val TRANSPARENCY =
        listOf(
            FixedChoice(
                Settings.TRANSPARENCY_STANDARD,
                R.string.settings_choice_transparency_standard,
            ),
            FixedChoice(
                Settings.TRANSPARENCY_REDUCED,
                R.string.settings_choice_transparency_reduced,
            ),
            FixedChoice(Settings.TRANSPARENCY_OFF, R.string.settings_choice_transparency_off),
        )

    /** Text size: 100, 115 and 130 percent. */
    val TEXT_SIZE =
        listOf(
            FixedChoice(
                Settings.TEXT_SIZE_DEFAULT,
                R.string.settings_value_percent,
                labelArg = 100,
            ),
            FixedChoice(Settings.TEXT_SIZE_LARGER, R.string.settings_value_percent, labelArg = 115),
            FixedChoice(
                Settings.TEXT_SIZE_LARGEST,
                R.string.settings_value_percent,
                labelArg = 130,
            ),
        )

    /** The colour-blind palette. */
    val COLOUR_VISION =
        listOf(
            FixedChoice(Settings.COLOUR_VISION_OFF, R.string.settings_choice_palette_off),
            FixedChoice(
                Settings.COLOUR_VISION_RED_GREEN,
                R.string.settings_choice_palette_red_green,
            ),
            FixedChoice(
                Settings.COLOUR_VISION_BLUE_YELLOW,
                R.string.settings_choice_palette_blue_yellow,
            ),
        )

    /** Touch. */
    val TOUCH =
        listOf(
            FixedChoice(TOUCH_TRACKPAD, R.string.settings_choice_touch_trackpad),
            FixedChoice(TOUCH_DIRECT, R.string.settings_choice_touch_direct),
        )

    /** Analog scrolling. */
    val ANALOG_SCROLLING =
        listOf(
            FixedChoice(
                "none",
                R.string.settings_choice_scroll_none,
                R.string.settings_choice_scroll_none_summary,
            ),
            FixedChoice("right", R.string.settings_choice_scroll_right),
            FixedChoice("left", R.string.settings_choice_scroll_left),
        )

    /** Audio channels. */
    val AUDIO_CHANNELS =
        listOf(
            FixedChoice("2", R.string.settings_choice_audio_stereo),
            FixedChoice("51", R.string.settings_choice_audio_51),
            FixedChoice("71", R.string.settings_choice_audio_71),
        )
}
