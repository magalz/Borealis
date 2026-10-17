package io.github.magalz.borealis.settings.catalog

import io.github.magalz.borealis.settings.DefaultBitrate

/**
 * The settings catalog: every key, stored type and default in one place (ADR 0055).
 *
 * It holds the 38 stream settings that the Phase 2 screen ports from the upstream screen (the
 * language list and the small icon mode are dropped), the two logging switches that live in
 * DataStore, and the new performance overlay choice. The defaults are those of the inherited
 * `PreferenceConfiguration`; a parity test keeps them in step.
 */
object Settings {
    // Stream

    /** Resolution as `WxH`, for example `1280x720`. */
    val RESOLUTION = StringSetting("list_resolution", default = "1280x720")

    /** Frame rate in frames per second, as a string, for example `60`. */
    val FRAME_RATE = StringSetting("list_fps", default = "60")

    /**
     * Bitrate in kbps. Absent means automatic: the value computed by [DefaultBitrate] from the
     * resolution and the frame rate. Clear it to return to automatic.
     */
    val BITRATE_KBPS = OptionalIntSetting("seekbar_bitrate_kbps")

    /** Frame pacing: `latency`, `balanced`, `cap-fps` or `smoothness`. */
    val FRAME_PACING = StringSetting("frame_pacing", default = "latency")

    /** Stretch the video to the whole screen. */
    val STRETCH_VIDEO = BooleanSetting("checkbox_stretch_video", default = false)

    // Audio

    /** Audio channels: `2` (stereo), `51` or `71`. */
    val AUDIO_CONFIG = StringSetting("list_audio_config", default = "2")

    /** Enable audio effects. */
    val AUDIO_FX = BooleanSetting("checkbox_enable_audiofx", default = false)

    // Gamepad

    /** Analog stick dead zone, 0 to 20 percent. */
    val DEADZONE = IntSetting("seekbar_deadzone", default = 7)

    /** Support multiple controllers. */
    val MULTI_CONTROLLER = BooleanSetting("checkbox_multi_controller", default = true)

    /** Use the built-in USB driver for Xbox controllers. */
    val USB_DRIVER = BooleanSetting("checkbox_usb_driver", default = true)

    /** Use the USB driver for all controllers. Depends on [USB_DRIVER]. */
    val USB_BIND_ALL = BooleanSetting("checkbox_usb_bind_all", default = false)

    /** Emulate a mouse with the gamepad. */
    val MOUSE_EMULATION = BooleanSetting("checkbox_mouse_emulation", default = true)

    /**
     * Analog stick that scrolls while the mouse is emulated: `right`, `left` or `none`. Depends on
     * [MOUSE_EMULATION].
     */
    val ANALOG_SCROLLING = StringSetting("analog_scrolling", default = "right")

    /** Vibrate the device when the controller cannot. */
    val VIBRATE_FALLBACK = BooleanSetting("checkbox_vibrate_fallback", default = false)

    /** Strength of the fallback vibration, 0 to 200 percent. Depends on [VIBRATE_FALLBACK]. */
    val VIBRATE_FALLBACK_STRENGTH = IntSetting("seekbar_vibrate_fallback_strength", default = 100)

    /** Swap the A/B and X/Y buttons. */
    val FLIP_FACE_BUTTONS = BooleanSetting("checkbox_flip_face_buttons", default = false)

    /** Use the controller touchpad as a mouse. */
    val GAMEPAD_TOUCHPAD_AS_MOUSE =
        BooleanSetting("checkbox_gamepad_touchpad_as_mouse", default = false)

    /**
     * Send the controller motion sensors. The catalog default is on, but `PreferenceConfiguration`
     * writes off on Android 12 exactly (an OS bug); the defaults parity test covers that case.
     */
    val GAMEPAD_MOTION_SENSORS = BooleanSetting("checkbox_gamepad_motion_sensors", default = true)

    /** Use the device motion sensors when the controller has none. */
    val GAMEPAD_MOTION_FALLBACK =
        BooleanSetting("checkbox_gamepad_motion_fallback", default = false)

    // Input

    /** Touch acts as a trackpad (on) or as a direct touch (off). */
    val TOUCHSCREEN_TRACKPAD = BooleanSetting("checkbox_touchscreen_trackpad", default = true)

    /** Send the mouse back and forward buttons. */
    val MOUSE_NAV_BUTTONS = BooleanSetting("checkbox_mouse_nav_buttons", default = false)

    /** Remote desktop mouse mode (absolute mouse). */
    val ABSOLUTE_MOUSE_MODE = BooleanSetting("checkbox_absolute_mouse_mode", default = false)

    // On-screen controls

    /** Show the on-screen controls. */
    val SHOW_ONSCREEN_CONTROLS = BooleanSetting("checkbox_show_onscreen_controls", default = false)

    /** Vibrate with the on-screen controls. Depends on [SHOW_ONSCREEN_CONTROLS]. */
    val VIBRATE_OSC = BooleanSetting("checkbox_vibrate_osc", default = true)

    /** Show only L3 and R3. Depends on [SHOW_ONSCREEN_CONTROLS]. */
    val ONLY_L3_R3 = BooleanSetting("checkbox_only_show_L3R3", default = false)

    /** Show the Guide button. Depends on [SHOW_ONSCREEN_CONTROLS]. */
    val SHOW_GUIDE_BUTTON = BooleanSetting("checkbox_show_guide_button", default = true)

    /** Opacity of the on-screen controls, 0 to 100 percent. Depends on [SHOW_ONSCREEN_CONTROLS]. */
    val OSC_OPACITY = IntSetting("seekbar_osc_opacity", default = 90)

    // Host

    /** Optimise game settings on the host (SOPS). */
    val ENABLE_SOPS = BooleanSetting("checkbox_enable_sops", default = true)

    /** Play the audio on the PC. */
    val HOST_AUDIO = BooleanSetting("checkbox_host_audio", default = false)

    // UI

    /** Allow picture-in-picture. */
    val ENABLE_PIP = BooleanSetting("checkbox_enable_pip", default = false)

    // Advanced

    /** Unlock all frame rates: the frame rate list is not filtered by the display. */
    val UNLOCK_FPS = BooleanSetting("checkbox_unlock_fps", default = false)

    /** Reduce the display refresh rate. */
    val REDUCE_REFRESH_RATE = BooleanSetting("checkbox_reduce_refresh_rate", default = false)

    /** Disable connection warnings. */
    val DISABLE_WARNINGS = BooleanSetting("checkbox_disable_warnings", default = false)

    /** Codec: `auto`, `forceav1`, `forceh265` or `neverh265`. */
    val VIDEO_FORMAT = StringSetting("video_format", default = "auto")

    /** Stream in HDR. */
    val ENABLE_HDR = BooleanSetting("checkbox_enable_hdr", default = false)

    /** Use the full colour range. */
    val FULL_RANGE = BooleanSetting("checkbox_full_range", default = false)

    /**
     * The old performance overlay switch. Java no longer reads it: it stays in the catalog only for
     * `LegacyMigrations`. The Phase 2 screen and `StreamSessionFactory` use [PERF_OVERLAY]; a
     * stored `true` here is migrated once to Detailed and the switch is never written again.
     */
    val PERF_OVERLAY_LEGACY = BooleanSetting("checkbox_enable_perf_overlay", default = false)

    /** Show a latency summary after a stream. */
    val POST_STREAM_TOAST = BooleanSetting("checkbox_enable_post_stream_toast", default = false)

    // Borealis

    /**
     * The performance overlay: [PERF_OVERLAY_OFF], [PERF_OVERLAY_COMPACT] or
     * [PERF_OVERLAY_DETAILED]. This is the key that `StreamSessionFactory` reads. The legacy switch
     * [PERF_OVERLAY_LEGACY] is only migrated once, by the storage task (`true` becomes Detailed),
     * and is never written again.
     */
    val PERF_OVERLAY = StringSetting("borealis_perf_overlay", default = "off")

    /** Value of [PERF_OVERLAY] with the overlay hidden. */
    const val PERF_OVERLAY_OFF = "off"

    /** Value of [PERF_OVERLAY] with the one-line overlay. */
    const val PERF_OVERLAY_COMPACT = "compact"

    /** Value of [PERF_OVERLAY] with the full overlay. */
    const val PERF_OVERLAY_DETAILED = "detailed"

    /** Verbose logging. Stored in DataStore. */
    val VERBOSE_LOGGING =
        BooleanSetting("borealis_verbose_logging", default = false, store = SettingStore.DATASTORE)

    /** Mask addresses in logs and diagnostics. Stored in DataStore. */
    val MASK_ADDRESSES =
        BooleanSetting("borealis_mask_addresses", default = false, store = SettingStore.DATASTORE)

    /** Every setting of the catalog, in the order of the settings screen. */
    val all: List<Setting<*>> =
        listOf(
            RESOLUTION,
            FRAME_RATE,
            BITRATE_KBPS,
            FRAME_PACING,
            STRETCH_VIDEO,
            AUDIO_CONFIG,
            AUDIO_FX,
            DEADZONE,
            MULTI_CONTROLLER,
            USB_DRIVER,
            USB_BIND_ALL,
            MOUSE_EMULATION,
            ANALOG_SCROLLING,
            VIBRATE_FALLBACK,
            VIBRATE_FALLBACK_STRENGTH,
            FLIP_FACE_BUTTONS,
            GAMEPAD_TOUCHPAD_AS_MOUSE,
            GAMEPAD_MOTION_SENSORS,
            GAMEPAD_MOTION_FALLBACK,
            TOUCHSCREEN_TRACKPAD,
            MOUSE_NAV_BUTTONS,
            ABSOLUTE_MOUSE_MODE,
            SHOW_ONSCREEN_CONTROLS,
            VIBRATE_OSC,
            ONLY_L3_R3,
            SHOW_GUIDE_BUTTON,
            OSC_OPACITY,
            ENABLE_SOPS,
            HOST_AUDIO,
            ENABLE_PIP,
            UNLOCK_FPS,
            REDUCE_REFRESH_RATE,
            DISABLE_WARNINGS,
            VIDEO_FORMAT,
            ENABLE_HDR,
            FULL_RANGE,
            PERF_OVERLAY_LEGACY,
            POST_STREAM_TOAST,
            PERF_OVERLAY,
            VERBOSE_LOGGING,
            MASK_ADDRESSES,
        )
}
