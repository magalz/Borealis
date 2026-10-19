package io.github.magalz.borealis.settings.ui

import androidx.annotation.StringRes
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.catalog.Settings

/** One entry of a list whose entries do not depend on the device. */
class FixedChoice(
    val key: String,
    @StringRes val labelRes: Int,
    @StringRes val summaryRes: Int? = null,
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

    /** Background. */
    val BACKGROUND = emptyList<FixedChoice>()

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
