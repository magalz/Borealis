package io.github.magalz.borealis.settings.ui

import androidx.annotation.Keep
import androidx.annotation.StringRes
import io.github.magalz.borealis.settings.R
import io.github.magalz.borealis.settings.appearance.LookLists
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.catalog.StringSetting
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import kotlinx.serialization.Serializable

/** A group of the settings screen, in the order of UC-009. */
// Navigation Compose serialises it as an argument of Settings; Keep stops R8 from renaming it.
@Keep
@Serializable
enum class SettingsGroupId(@StringRes val titleRes: Int) {
    STREAM(R.string.settings_group_stream),
    VIDEO(R.string.settings_group_video),
    INPUT(R.string.settings_group_input),
    GAMEPAD(R.string.settings_group_gamepad),
    ON_SCREEN_CONTROLS(R.string.settings_group_on_screen_controls),
    AUDIO(R.string.settings_group_audio),
    ADVANCED(R.string.settings_group_advanced),

    /** The visual options of the app. It follows the stream groups and precedes the app groups. */
    APPEARANCE(R.string.settings_group_appearance),

    /**
     * Reduce motion, transparency, focus, text size and spacing, contrast and palette (ADR 0057).
     */
    ACCESSIBILITY(R.string.settings_group_accessibility),

    /** Has no rows here: the Privacy and diagnostics screen provides its content. */
    PRIVACY(R.string.settings_group_privacy),

    /** Has no rows here: the screen draws the About rows itself. */
    ABOUT(R.string.settings_group_about),
}

/** How a row of [SettingsItem] is shown and changed. */
enum class SettingsItemKind {
    /** A switch. */
    SWITCH,

    /** A list that opens a choice sheet. */
    CHOICE,

    /** A slider. */
    SLIDER,

    /** An action that does something instead of storing a value. */
    ACTION,
}

/**
 * The range and steps of a slider, in the stored unit. [step] is what a finger sets and [keyStep]
 * is what a D-pad press moves; [keyStep] is a multiple of [step].
 */
class SliderSpec(val min: Int, val max: Int, val step: Int, val keyStep: Int = step)

/**
 * How a row lets the user pick a value, beyond what its [Setting] implies: a [slider], a list of
 * [fixedChoices], or neither.
 */
class ItemInput(val slider: SliderSpec? = null, val fixedChoices: List<FixedChoice> = emptyList())

private const val BITRATE_MIN_KBPS = 500
private const val BITRATE_MAX_KBPS = 150_000
private const val BITRATE_STEP_KBPS = 500
private const val BITRATE_KEY_STEP_KBPS = 5_000
private const val DEADZONE_MAX = 20
private const val VIBRATION_MAX = 200
private const val OPACITY_MAX = 100
private const val PERCENT_KEY_STEP = 5
private const val MOUSE_SPEED_MIN = 10
private const val MOUSE_SPEED_MAX = 200
private const val MOUSE_SPEED_STEP = 5
private const val MOUSE_SPEED_KEY_STEP = 10
private const val VIBRATION_KEY_STEP = 10

/**
 * Every row of the settings screen, in the order of UC-009: the group it belongs to, how it is
 * changed and the [setting] that it stores ([SettingsItem.RESET_LAYOUT] stores nothing). The titles
 * and summaries are the wording of UC-009.
 *
 * [summaryRes] is the template of the summary: a row reads it through `summaryText()`, never as a
 * plain string, because the one of [TRANSPARENCY] takes the labels of other rows as arguments.
 */
enum class SettingsItem(
    val group: SettingsGroupId,
    val kind: SettingsItemKind,
    val setting: Setting<*>?,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int? = null,
    val input: ItemInput = ItemInput(),
) {
    RESOLUTION(
        SettingsGroupId.STREAM,
        SettingsItemKind.CHOICE,
        Settings.RESOLUTION,
        R.string.settings_resolution_title,
    ),
    FRAME_RATE(
        SettingsGroupId.STREAM,
        SettingsItemKind.CHOICE,
        Settings.FRAME_RATE,
        R.string.settings_frame_rate_title,
    ),
    BITRATE(
        SettingsGroupId.STREAM,
        SettingsItemKind.SLIDER,
        Settings.BITRATE_KBPS,
        R.string.settings_bitrate_title,
        input =
            ItemInput(
                slider =
                    SliderSpec(
                        BITRATE_MIN_KBPS,
                        BITRATE_MAX_KBPS,
                        BITRATE_STEP_KBPS,
                        BITRATE_KEY_STEP_KBPS,
                    )
            ),
    ),
    FRAME_PACING(
        SettingsGroupId.STREAM,
        SettingsItemKind.CHOICE,
        Settings.FRAME_PACING,
        R.string.settings_frame_pacing_title,
        input = ItemInput(fixedChoices = FixedChoices.FRAME_PACING),
    ),
    CODEC(
        SettingsGroupId.VIDEO,
        SettingsItemKind.CHOICE,
        Settings.VIDEO_FORMAT,
        R.string.settings_codec_title,
        R.string.settings_codec_summary,
        input = ItemInput(fixedChoices = FixedChoices.CODEC),
    ),
    HDR(
        SettingsGroupId.VIDEO,
        SettingsItemKind.SWITCH,
        Settings.ENABLE_HDR,
        R.string.settings_hdr_title,
        R.string.settings_hdr_summary,
    ),
    FULL_RANGE(
        SettingsGroupId.VIDEO,
        SettingsItemKind.SWITCH,
        Settings.FULL_RANGE,
        R.string.settings_full_range_title,
        R.string.settings_full_range_summary,
    ),
    PERF_OVERLAY(
        SettingsGroupId.VIDEO,
        SettingsItemKind.CHOICE,
        Settings.PERF_OVERLAY,
        R.string.settings_perf_overlay_title,
        input = ItemInput(fixedChoices = FixedChoices.PERF_OVERLAY),
    ),
    STRETCH_VIDEO(
        SettingsGroupId.VIDEO,
        SettingsItemKind.SWITCH,
        Settings.STRETCH_VIDEO,
        R.string.settings_stretch_video_title,
        R.string.settings_stretch_video_summary,
    ),
    TOUCH(
        SettingsGroupId.INPUT,
        SettingsItemKind.CHOICE,
        Settings.TOUCHSCREEN_TRACKPAD,
        R.string.settings_touch_title,
        input = ItemInput(fixedChoices = FixedChoices.TOUCH),
    ),
    MOUSE_NAV_BUTTONS(
        SettingsGroupId.INPUT,
        SettingsItemKind.SWITCH,
        Settings.MOUSE_NAV_BUTTONS,
        R.string.settings_mouse_nav_buttons_title,
        R.string.settings_mouse_nav_buttons_summary,
    ),
    ABSOLUTE_MOUSE(
        SettingsGroupId.INPUT,
        SettingsItemKind.SWITCH,
        Settings.ABSOLUTE_MOUSE_MODE,
        R.string.settings_absolute_mouse_title,
        R.string.settings_absolute_mouse_summary,
    ),
    GAMEPAD_MOUSE(
        SettingsGroupId.INPUT,
        SettingsItemKind.SWITCH,
        Settings.MOUSE_EMULATION,
        R.string.settings_gamepad_mouse_title,
        R.string.settings_gamepad_mouse_summary,
    ),
    ANALOG_SCROLLING(
        SettingsGroupId.INPUT,
        SettingsItemKind.CHOICE,
        Settings.ANALOG_SCROLLING,
        R.string.settings_analog_scrolling_title,
        input = ItemInput(fixedChoices = FixedChoices.ANALOG_SCROLLING),
    ),
    GAMEPAD_MOUSE_SPEED(
        SettingsGroupId.INPUT,
        SettingsItemKind.SLIDER,
        Settings.GAMEPAD_MOUSE_SPEED,
        R.string.settings_gamepad_mouse_speed_title,
        input =
            ItemInput(
                slider =
                    SliderSpec(
                        MOUSE_SPEED_MIN,
                        MOUSE_SPEED_MAX,
                        MOUSE_SPEED_STEP,
                        MOUSE_SPEED_KEY_STEP,
                    )
            ),
    ),
    DEADZONE(
        SettingsGroupId.INPUT,
        SettingsItemKind.SLIDER,
        Settings.DEADZONE,
        R.string.settings_deadzone_title,
        R.string.settings_deadzone_summary,
        input = ItemInput(slider = SliderSpec(0, DEADZONE_MAX, 1)),
    ),
    MULTI_CONTROLLER(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.MULTI_CONTROLLER,
        R.string.settings_multi_controller_title,
        R.string.settings_multi_controller_summary,
    ),
    USB_DRIVER(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.USB_DRIVER,
        R.string.settings_usb_driver_title,
        R.string.settings_usb_driver_summary,
    ),
    USB_BIND_ALL(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.USB_BIND_ALL,
        R.string.settings_usb_bind_all_title,
        R.string.settings_usb_bind_all_summary,
    ),
    FLIP_FACE_BUTTONS(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.FLIP_FACE_BUTTONS,
        R.string.settings_flip_face_buttons_title,
        R.string.settings_flip_face_buttons_summary,
    ),
    TOUCHPAD_AS_MOUSE(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.GAMEPAD_TOUCHPAD_AS_MOUSE,
        R.string.settings_touchpad_as_mouse_title,
        R.string.settings_touchpad_as_mouse_summary,
    ),
    MOTION_SENSORS(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.GAMEPAD_MOTION_SENSORS,
        R.string.settings_motion_sensors_title,
        R.string.settings_motion_sensors_summary,
    ),
    MOTION_FALLBACK(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.GAMEPAD_MOTION_FALLBACK,
        R.string.settings_motion_fallback_title,
        R.string.settings_motion_fallback_summary,
    ),
    VIBRATE_FALLBACK(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SWITCH,
        Settings.VIBRATE_FALLBACK,
        R.string.settings_vibrate_fallback_title,
    ),
    VIBRATION_STRENGTH(
        SettingsGroupId.GAMEPAD,
        SettingsItemKind.SLIDER,
        Settings.VIBRATE_FALLBACK_STRENGTH,
        R.string.settings_vibration_strength_title,
        input = ItemInput(slider = SliderSpec(0, VIBRATION_MAX, 1, VIBRATION_KEY_STEP)),
    ),
    SHOW_ON_SCREEN_CONTROLS(
        SettingsGroupId.ON_SCREEN_CONTROLS,
        SettingsItemKind.SWITCH,
        Settings.SHOW_ONSCREEN_CONTROLS,
        R.string.settings_show_osc_title,
    ),
    OSC_VIBRATE(
        SettingsGroupId.ON_SCREEN_CONTROLS,
        SettingsItemKind.SWITCH,
        Settings.VIBRATE_OSC,
        R.string.settings_osc_vibrate_title,
    ),
    ONLY_L3_R3(
        SettingsGroupId.ON_SCREEN_CONTROLS,
        SettingsItemKind.SWITCH,
        Settings.ONLY_L3_R3,
        R.string.settings_only_l3_r3_title,
    ),
    GUIDE_BUTTON(
        SettingsGroupId.ON_SCREEN_CONTROLS,
        SettingsItemKind.SWITCH,
        Settings.SHOW_GUIDE_BUTTON,
        R.string.settings_guide_button_title,
    ),
    OSC_OPACITY(
        SettingsGroupId.ON_SCREEN_CONTROLS,
        SettingsItemKind.SLIDER,
        Settings.OSC_OPACITY,
        R.string.settings_osc_opacity_title,
        input = ItemInput(slider = SliderSpec(0, OPACITY_MAX, 1, PERCENT_KEY_STEP)),
    ),
    RESET_LAYOUT(
        SettingsGroupId.ON_SCREEN_CONTROLS,
        SettingsItemKind.ACTION,
        null,
        R.string.settings_reset_layout_title,
    ),
    AUDIO_CHANNELS(
        SettingsGroupId.AUDIO,
        SettingsItemKind.CHOICE,
        Settings.AUDIO_CONFIG,
        R.string.settings_audio_channels_title,
        input = ItemInput(fixedChoices = FixedChoices.AUDIO_CHANNELS),
    ),
    HOST_AUDIO(
        SettingsGroupId.AUDIO,
        SettingsItemKind.SWITCH,
        Settings.HOST_AUDIO,
        R.string.settings_host_audio_title,
    ),
    AUDIO_EFFECTS(
        SettingsGroupId.AUDIO,
        SettingsItemKind.SWITCH,
        Settings.AUDIO_FX,
        R.string.settings_audio_fx_title,
        R.string.settings_audio_fx_summary,
    ),
    OPTIMISE_GAME_SETTINGS(
        SettingsGroupId.ADVANCED,
        SettingsItemKind.SWITCH,
        Settings.ENABLE_SOPS,
        R.string.settings_sops_title,
        R.string.settings_sops_summary,
    ),
    UNLOCK_FRAME_RATES(
        SettingsGroupId.ADVANCED,
        SettingsItemKind.SWITCH,
        Settings.UNLOCK_FPS,
        R.string.settings_unlock_fps_title,
        R.string.settings_unlock_fps_summary,
    ),
    REDUCE_REFRESH_RATE(
        SettingsGroupId.ADVANCED,
        SettingsItemKind.SWITCH,
        Settings.REDUCE_REFRESH_RATE,
        R.string.settings_reduce_refresh_rate_title,
        R.string.settings_reduce_refresh_rate_summary,
    ),
    DISABLE_WARNINGS(
        SettingsGroupId.ADVANCED,
        SettingsItemKind.SWITCH,
        Settings.DISABLE_WARNINGS,
        R.string.settings_disable_warnings_title,
    ),
    LATENCY_SUMMARY(
        SettingsGroupId.ADVANCED,
        SettingsItemKind.SWITCH,
        Settings.POST_STREAM_TOAST,
        R.string.settings_post_stream_toast_title,
    ),
    PICTURE_IN_PICTURE(
        SettingsGroupId.ADVANCED,
        SettingsItemKind.SWITCH,
        Settings.ENABLE_PIP,
        R.string.settings_pip_title,
        R.string.settings_pip_summary,
    ),
    FONT(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.FONT,
        R.string.settings_font_title,
        input = ItemInput(fixedChoices = FixedChoices.FONT),
    ),
    ACCENT(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.ACCENT,
        R.string.settings_accent_title,
        input = ItemInput(fixedChoices = FixedChoices.ACCENT),
    ),
    BACKGROUND(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.BACKGROUND,
        R.string.settings_background_title,
        input = ItemInput(fixedChoices = FixedChoices.BACKGROUND),
    ),
    BACKGROUND_INTENSITY(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.BACKGROUND_INTENSITY,
        R.string.settings_background_intensity_title,
        input = ItemInput(fixedChoices = FixedChoices.BACKGROUND_INTENSITY),
    ),
    STREAM_MENU_GLASS(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.STREAM_MENU_GLASS,
        R.string.settings_stream_menu_glass_title,
        R.string.settings_stream_menu_glass_summary,
        input = ItemInput(fixedChoices = FixedChoices.STREAM_MENU_GLASS),
    ),
    CARD_DENSITY(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.CARD_DENSITY,
        R.string.settings_card_density_title,
        input = ItemInput(fixedChoices = FixedChoices.CARD_DENSITY),
    ),
    GLASS_TINT(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.GLASS_TINT,
        R.string.settings_glass_tint_title,
        R.string.settings_glass_tint_summary,
        input = ItemInput(fixedChoices = FixedChoices.GLASS_TINT),
    ),
    GLASS_BLUR(
        SettingsGroupId.APPEARANCE,
        SettingsItemKind.CHOICE,
        Settings.GLASS_BLUR,
        R.string.settings_glass_blur_title,
        R.string.settings_glass_blur_summary,
        input = ItemInput(fixedChoices = FixedChoices.GLASS_BLUR),
    ),
    REDUCE_MOTION(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.CHOICE,
        Settings.REDUCE_MOTION,
        R.string.settings_reduce_motion_title,
        R.string.settings_reduce_motion_summary,
        input = ItemInput(fixedChoices = FixedChoices.REDUCE_MOTION),
    ),
    TRANSPARENCY(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.CHOICE,
        Settings.TRANSPARENCY,
        R.string.settings_transparency_title,
        R.string.settings_transparency_summary,
        input = ItemInput(fixedChoices = FixedChoices.TRANSPARENCY),
    ),
    THICK_FOCUS(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.SWITCH,
        Settings.THICK_FOCUS,
        R.string.settings_thick_focus_title,
        R.string.settings_thick_focus_summary,
    ),
    TEXT_SIZE(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.CHOICE,
        Settings.TEXT_SIZE,
        R.string.settings_text_size_title,
        R.string.settings_text_size_summary,
        input = ItemInput(fixedChoices = FixedChoices.TEXT_SIZE),
    ),
    TEXT_SPACING(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.CHOICE,
        Settings.TEXT_SPACING,
        R.string.settings_text_spacing_title,
        input = ItemInput(fixedChoices = FixedChoices.TEXT_SPACING),
    ),
    HIGH_CONTRAST(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.SWITCH,
        Settings.HIGH_CONTRAST,
        R.string.settings_high_contrast_title,
        R.string.settings_high_contrast_summary,
    ),
    COLOUR_VISION(
        SettingsGroupId.ACCESSIBILITY,
        SettingsItemKind.CHOICE,
        Settings.COLOUR_VISION,
        R.string.settings_colour_vision_title,
        R.string.settings_colour_vision_summary,
        input = ItemInput(fixedChoices = FixedChoices.COLOUR_VISION),
    );

    companion object {
        /** The row that stores [setting], or `null` when no row does. */
        fun forSetting(setting: Setting<*>): SettingsItem? = entries.firstOrNull {
            it.setting == setting
        }
    }
}

/**
 * The key of the entry that the list of this [SettingsItem.CHOICE] item shows as chosen, for
 * [snapshot]. Touch is a switch in the catalog and a two-entry list here.
 */
fun SettingsItem.selectedChoiceKey(snapshot: SettingsSnapshot): String =
    when (val stored = setting) {
        Settings.TOUCHSCREEN_TRACKPAD ->
            if (snapshot[Settings.TOUCHSCREEN_TRACKPAD]) {
                FixedChoices.TOUCH_TRACKPAD
            } else {
                FixedChoices.TOUCH_DIRECT
            }
        // A stored value that no choice has is drawn as the default, so the list shows that one.
        is StringSetting -> LookLists.of(stored)?.shownId(snapshot[stored]) ?: snapshot[stored]
        else -> error("$this is not a list")
    }
