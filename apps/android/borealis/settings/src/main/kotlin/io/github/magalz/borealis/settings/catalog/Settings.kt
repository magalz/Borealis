package io.github.magalz.borealis.settings.catalog

import io.github.magalz.borealis.settings.DefaultBitrate

/**
 * The settings catalog: every key, stored type and default in one place (ADR 0055).
 *
 * It holds the 38 stream settings that the Phase 2 screen ports from the upstream screen (the
 * language list and the small icon mode are dropped), the two logging switches that live in
 * DataStore, the new performance overlay choice, the gamepad mouse speed, the background choice and
 * the font, accent colour, background intensity, card density and glass choices of the Appearance
 * group, and the choices of the Accessibility group, which live in DataStore too. The defaults of
 * the ported settings are those of the inherited `PreferenceConfiguration`; a parity test keeps
 * them in step.
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

    /**
     * Speed of the pointer of the gamepad mouse, 10 to 200 percent. Depends on [MOUSE_EMULATION].
     */
    val GAMEPAD_MOUSE_SPEED = IntSetting("seekbar_gamepad_mouse_speed", default = 50)

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
     * The old performance overlay switch. Only `LegacyMigrations` reads it: it stays in the catalog
     * for that migration. The Phase 2 screen and `StreamSessionFactory` use [PERF_OVERLAY]; a
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

    /**
     * The font of the app: the id of one of the fonts below, never the ordinal of a font. A stored
     * value that no font has stands for the default, IBM Plex Sans. It starts in DataStore, as ADR
     * 0011 sets for new settings.
     */
    val FONT = StringSetting("borealis_font", default = FONT_PLEX, store = SettingStore.DATASTORE)

    /** Value of [FONT] for IBM Plex Sans, the default. */
    const val FONT_PLEX = "plex"

    /** Value of [FONT] for Atkinson Hyperlegible Next. */
    const val FONT_ATKINSON_NEXT = "atkinson_next"

    /** Value of [FONT] for Luciole. */
    const val FONT_LUCIOLE = "luciole"

    /** Value of [FONT] for Lexend. */
    const val FONT_LEXEND = "lexend"

    /** Value of [FONT] for Andika. */
    const val FONT_ANDIKA = "andika"

    /** Value of [FONT] for B612. */
    const val FONT_B612 = "b612"

    /** Value of [FONT] for the font of the device. */
    const val FONT_SYSTEM = "system"

    /**
     * The accent colour of the app: [ACCENT_VIOLET], [ACCENT_TEAL] or [ACCENT_WHITE]. A stored
     * value that no choice has stands for the default.
     */
    val ACCENT =
        StringSetting("borealis_accent", default = ACCENT_VIOLET, store = SettingStore.DATASTORE)

    /** Value of [ACCENT] for violet, the default. */
    const val ACCENT_VIOLET = "violet"

    /** Value of [ACCENT] for teal. */
    const val ACCENT_TEAL = "teal"

    /** Value of [ACCENT] for white. */
    const val ACCENT_WHITE = "white"

    /**
     * The animated background behind the Library and Settings: the id of one of the styles below,
     * never the ordinal of a style. A stored value that no style has stands for the default, the
     * Aurora curtains. Unlike [PERF_OVERLAY], it has no legacy SharedPreferences value to migrate,
     * so it starts in DataStore, as ADR 0011 sets for new settings. The Appearance group of the
     * settings screen sets it, and the Library shell follows it.
     */
    val BACKGROUND =
        StringSetting(
            "borealis_background",
            default = BACKGROUND_AURORA_CURTAINS,
            store = SettingStore.DATASTORE,
        )

    /** Value of [BACKGROUND] for the Aurora curtains, the default. */
    const val BACKGROUND_AURORA_CURTAINS = "aurora_curtains"

    /** Value of [BACKGROUND] for the Soft glow. */
    const val BACKGROUND_SOFT_GLOW = "soft_glow"

    /** Value of [BACKGROUND] for the Northern lights clip. */
    const val BACKGROUND_NORTHERN_LIGHTS = "northern_lights"

    /** Value of [BACKGROUND] for the Light mist clip. */
    const val BACKGROUND_LIGHT_MIST = "light_mist"

    /** Value of [BACKGROUND] for the Flowing light clip. */
    const val BACKGROUND_FLOWING_LIGHT = "flowing_light"

    /** Value of [BACKGROUND] for the Starfield clip. */
    const val BACKGROUND_STARFIELD = "starfield"

    /** Value of [BACKGROUND] for the plain page colour. */
    const val BACKGROUND_OFF = "off"

    /**
     * How strongly the animated background shows, in percent: one of the ids `100`, `75`, `50` or
     * `25`. The rest is a veil of the page colour laid over it, so that the glass panels, which
     * assume the background at full strength, stay legible. A stored value that no choice has
     * stands for the default.
     */
    val BACKGROUND_INTENSITY =
        StringSetting(
            "borealis_background_intensity",
            default = "100",
            store = SettingStore.DATASTORE,
        )

    /**
     * The background on the screens outside the game, before and after the stream:
     * [STREAM_BACKGROUND_MOVING], [STREAM_BACKGROUND_STILL] or [STREAM_BACKGROUND_OFF]. It shows
     * the style of [BACKGROUND]. A stored value that no choice has stands for the default. The
     * stream reads it once, when it starts (ADR 0061).
     */
    val STREAM_BACKGROUND =
        StringSetting(
            "borealis_stream_background",
            default = STREAM_BACKGROUND_MOVING,
            store = SettingStore.DATASTORE,
        )

    /** Value of [STREAM_BACKGROUND] for the background as it moves, the default. */
    const val STREAM_BACKGROUND_MOVING = "moving"

    /** Value of [STREAM_BACKGROUND] for one still frame of the background. */
    const val STREAM_BACKGROUND_STILL = "still"

    /** Value of [STREAM_BACKGROUND] for the plain page colour. */
    const val STREAM_BACKGROUND_OFF = "off"

    /**
     * The glass of the in-game menu in a stream: [STREAM_MENU_GLASS_BLUR] or
     * [STREAM_MENU_GLASS_SOLID]. A stored value that no choice has stands for the default. The
     * stream reads it once, when it starts (ADR 0061).
     */
    val STREAM_MENU_GLASS =
        StringSetting(
            "borealis_stream_menu_glass",
            default = STREAM_MENU_GLASS_BLUR,
            store = SettingStore.DATASTORE,
        )

    /** Value of [STREAM_MENU_GLASS] for the live blurred copy of the video, the default. */
    const val STREAM_MENU_GLASS_BLUR = "blur"

    /** Value of [STREAM_MENU_GLASS] for a solid surface. */
    const val STREAM_MENU_GLASS_SOLID = "solid"

    /**
     * How close the cards of a screen sit: [CARD_DENSITY_COMFORTABLE], [CARD_DENSITY_COMPACT] or
     * [CARD_DENSITY_DENSE]. A stored value that no choice has stands for the default.
     */
    val CARD_DENSITY =
        StringSetting(
            "borealis_card_density",
            default = CARD_DENSITY_COMFORTABLE,
            store = SettingStore.DATASTORE,
        )

    /** Value of [CARD_DENSITY] for the layout of today, the default. */
    const val CARD_DENSITY_COMFORTABLE = "comfortable"

    /** Value of [CARD_DENSITY] for more cards in each row. */
    const val CARD_DENSITY_COMPACT = "compact"

    /** Value of [CARD_DENSITY] for more cards in each row, with less padding. */
    const val CARD_DENSITY_DENSE = "dense"

    /**
     * How much the glass panels tint the background, in percent: one of the ids `30`, `40`, `50`,
     * `60`, `70` or `85`. It sets the Standard level of [TRANSPARENCY]. A stored value that no
     * choice has stands for the default (ADR 0057).
     */
    val GLASS_TINT =
        StringSetting("borealis_glass_tint", default = "40", store = SettingStore.DATASTORE)

    /**
     * How much the glass panels blur the background, in dp: one of the ids `8`, `16`, `24`, `32` or
     * `48`. It sets the Standard level of [TRANSPARENCY]. A stored value that no choice has stands
     * for the default.
     */
    val GLASS_BLUR =
        StringSetting("borealis_glass_blur", default = "8", store = SettingStore.DATASTORE)

    /**
     * Reduce motion: [REDUCE_MOTION_FOLLOW_SYSTEM], [REDUCE_MOTION_ON] or [REDUCE_MOTION_OFF]. A
     * stored value that no choice has stands for the default.
     */
    val REDUCE_MOTION =
        StringSetting(
            "borealis_reduce_motion",
            default = REDUCE_MOTION_FOLLOW_SYSTEM,
            store = SettingStore.DATASTORE,
        )

    /** Value of [REDUCE_MOTION] that follows the animation scale of the system, the default. */
    const val REDUCE_MOTION_FOLLOW_SYSTEM = "follow_system"

    /** Value of [REDUCE_MOTION] that removes the animations. */
    const val REDUCE_MOTION_ON = "on"

    /** Value of [REDUCE_MOTION] that keeps the animations, also when the system removes them. */
    const val REDUCE_MOTION_OFF = "off"

    /**
     * Transparency of the glass panels: [TRANSPARENCY_STANDARD], [TRANSPARENCY_REDUCED] or
     * [TRANSPARENCY_OFF]. A stored value that no choice has stands for the default.
     */
    val TRANSPARENCY =
        StringSetting(
            "borealis_transparency",
            default = TRANSPARENCY_STANDARD,
            store = SettingStore.DATASTORE,
        )

    /** Value of [TRANSPARENCY] for the tint and blur of the Appearance group, the default. */
    const val TRANSPARENCY_STANDARD = "standard"

    /** Value of [TRANSPARENCY] for panels that are mostly opaque. */
    const val TRANSPARENCY_REDUCED = "reduced"

    /** Value of [TRANSPARENCY] for solid panels. */
    const val TRANSPARENCY_OFF = "off"

    /** A thicker, lighter focus outline. Stored in DataStore. */
    val THICK_FOCUS =
        BooleanSetting("borealis_thick_focus", default = false, store = SettingStore.DATASTORE)

    /**
     * The text size of the app: [TEXT_SIZE_DEFAULT], [TEXT_SIZE_LARGER] or [TEXT_SIZE_LARGEST]. A
     * stored value that no choice has stands for the default.
     */
    val TEXT_SIZE =
        StringSetting(
            "borealis_text_size",
            default = TEXT_SIZE_DEFAULT,
            store = SettingStore.DATASTORE,
        )

    /** Value of [TEXT_SIZE] for 100 %, the default. */
    const val TEXT_SIZE_DEFAULT = "default"

    /** Value of [TEXT_SIZE] for 115 %. */
    const val TEXT_SIZE_LARGER = "larger"

    /** Value of [TEXT_SIZE] for 130 %. */
    const val TEXT_SIZE_LARGEST = "largest"

    /**
     * The space between the letters, words and lines of the text: [TEXT_SPACING_NORMAL],
     * [TEXT_SPACING_WIDE] or [TEXT_SPACING_WIDER]. A stored value that no choice has stands for the
     * default.
     */
    val TEXT_SPACING =
        StringSetting(
            "borealis_text_spacing",
            default = TEXT_SPACING_NORMAL,
            store = SettingStore.DATASTORE,
        )

    /** Value of [TEXT_SPACING] for the spacing of the font, the default. */
    const val TEXT_SPACING_NORMAL = "normal"

    /** Value of [TEXT_SPACING] for more space between letters, words and lines. */
    const val TEXT_SPACING_WIDE = "wide"

    /** Value of [TEXT_SPACING] for even more space. */
    const val TEXT_SPACING_WIDER = "wider"

    /** High contrast: solid panels and text at 7:1 or more. Stored in DataStore. */
    val HIGH_CONTRAST =
        BooleanSetting("borealis_high_contrast", default = false, store = SettingStore.DATASTORE)

    /**
     * The palette of the status colours: [COLOUR_VISION_OFF], [COLOUR_VISION_RED_GREEN] or
     * [COLOUR_VISION_BLUE_YELLOW]. A stored value that no choice has stands for the default.
     */
    val COLOUR_VISION =
        StringSetting(
            "borealis_colour_vision",
            default = COLOUR_VISION_OFF,
            store = SettingStore.DATASTORE,
        )

    /** Value of [COLOUR_VISION] for the usual palette, the default. */
    const val COLOUR_VISION_OFF = "off"

    /** Value of [COLOUR_VISION] for a palette that keeps red and green apart. */
    const val COLOUR_VISION_RED_GREEN = "red_green"

    /** Value of [COLOUR_VISION] for a palette that keeps blue and yellow apart. */
    const val COLOUR_VISION_BLUE_YELLOW = "blue_yellow"

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
            GAMEPAD_MOUSE_SPEED,
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
            FONT,
            ACCENT,
            BACKGROUND,
            BACKGROUND_INTENSITY,
            STREAM_BACKGROUND,
            STREAM_MENU_GLASS,
            CARD_DENSITY,
            GLASS_TINT,
            GLASS_BLUR,
            REDUCE_MOTION,
            TRANSPARENCY,
            THICK_FOCUS,
            TEXT_SIZE,
            TEXT_SPACING,
            HIGH_CONTRAST,
            COLOUR_VISION,
            VERBOSE_LOGGING,
            MASK_ADDRESSES,
        )
}
