package com.limelight.preferences

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.preference.PreferenceManager
import android.view.Display
import androidx.core.content.edit
import com.limelight.nvstream.jni.MoonBridge
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class PreferenceConfiguration {
    enum class FormatOption {
        AUTO,
        FORCE_AV1,
        FORCE_HEVC,
        FORCE_H264,
    }

    enum class AnalogStickForScrolling {
        NONE,
        RIGHT,
        LEFT,
    }

    @JvmField var width = 0
    @JvmField var height = 0
    @JvmField var fps = 0
    @JvmField var bitrate = 0
    @JvmField var videoFormat: FormatOption? = null
    @JvmField var deadzonePercentage = 0
    @JvmField var gamepadMouseSpeed = 0
    @JvmField var oscOpacity = 0
    @JvmField var stretchVideo = false
    @JvmField var enableSops = false
    @JvmField var playHostAudio = false
    @JvmField var disableWarnings = false
    @JvmField var multiController = false
    @JvmField var usbDriver = false
    @JvmField var flipFaceButtons = false
    @JvmField var onscreenController = false
    @JvmField var onlyL3R3 = false
    @JvmField var showGuideButton = false
    @JvmField var enableHdr = false
    @JvmField var enablePip = false
    @JvmField var enableLatencyToast = false
    @JvmField var bindAllUsb = false
    @JvmField var mouseEmulation = false
    @JvmField var analogStickForScrolling: AnalogStickForScrolling? = null
    @JvmField var mouseNavButtons = false
    @JvmField var unlockFps = false
    @JvmField var vibrateOsc = false
    @JvmField var vibrateFallbackToDevice = false
    @JvmField var vibrateFallbackToDeviceStrength = 0
    @JvmField var touchscreenTrackpad = false
    @JvmField var audioConfiguration: MoonBridge.AudioConfiguration? = null
    @JvmField var framePacing = 0
    @JvmField var absoluteMouseMode = false
    @JvmField var enableAudioFx = false
    @JvmField var reduceRefreshRate = false
    @JvmField var fullRange = false
    @JvmField var gamepadMotionSensors = false
    @JvmField var gamepadTouchpadAsMouse = false
    @JvmField var gamepadMotionSensorsFallbackToDevice = false

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("TooManyFunctions")
    companion object {
        private const val LEGACY_RES_FPS_PREF_STRING = "list_resolution_fps"
        private const val LEGACY_ENABLE_51_SURROUND_PREF_STRING = "checkbox_51_surround"

        private const val RESOLUTION_PREF_STRING = "list_resolution"
        private const val FPS_PREF_STRING = "list_fps"
        private const val BITRATE_PREF_STRING = "seekbar_bitrate_kbps"
        private const val BITRATE_PREF_OLD_STRING = "seekbar_bitrate"
        private const val STRETCH_PREF_STRING = "checkbox_stretch_video"
        private const val SOPS_PREF_STRING = "checkbox_enable_sops"
        private const val DISABLE_TOASTS_PREF_STRING = "checkbox_disable_warnings"
        private const val HOST_AUDIO_PREF_STRING = "checkbox_host_audio"
        private const val DEADZONE_PREF_STRING = "seekbar_deadzone"
        private const val GAMEPAD_MOUSE_SPEED_PREF_STRING = "seekbar_gamepad_mouse_speed"
        private const val OSC_OPACITY_PREF_STRING = "seekbar_osc_opacity"
        private const val MULTI_CONTROLLER_PREF_STRING = "checkbox_multi_controller"
        private const val AUDIO_CONFIG_PREF_STRING = "list_audio_config"
        private const val USB_DRIVER_PREF_SRING = "checkbox_usb_driver"
        private const val VIDEO_FORMAT_PREF_STRING = "video_format"
        private const val ONSCREEN_CONTROLLER_PREF_STRING = "checkbox_show_onscreen_controls"
        private const val ONLY_L3_R3_PREF_STRING = "checkbox_only_show_L3R3"
        private const val SHOW_GUIDE_BUTTON_PREF_STRING = "checkbox_show_guide_button"
        private const val LEGACY_DISABLE_FRAME_DROP_PREF_STRING = "checkbox_disable_frame_drop"
        private const val ENABLE_HDR_PREF_STRING = "checkbox_enable_hdr"
        private const val ENABLE_PIP_PREF_STRING = "checkbox_enable_pip"
        private const val BIND_ALL_USB_STRING = "checkbox_usb_bind_all"
        private const val MOUSE_EMULATION_STRING = "checkbox_mouse_emulation"
        private const val ANALOG_SCROLLING_PREF_STRING = "analog_scrolling"
        private const val MOUSE_NAV_BUTTONS_STRING = "checkbox_mouse_nav_buttons"
        private const val UNLOCK_FPS_STRING = "checkbox_unlock_fps"
        private const val VIBRATE_OSC_PREF_STRING = "checkbox_vibrate_osc"
        private const val VIBRATE_FALLBACK_PREF_STRING = "checkbox_vibrate_fallback"
        private const val VIBRATE_FALLBACK_STRENGTH_PREF_STRING =
            "seekbar_vibrate_fallback_strength"
        private const val FLIP_FACE_BUTTONS_PREF_STRING = "checkbox_flip_face_buttons"
        private const val TOUCHSCREEN_TRACKPAD_PREF_STRING = "checkbox_touchscreen_trackpad"
        private const val LATENCY_TOAST_PREF_STRING = "checkbox_enable_post_stream_toast"
        private const val FRAME_PACING_PREF_STRING = "frame_pacing"
        private const val ABSOLUTE_MOUSE_MODE_PREF_STRING = "checkbox_absolute_mouse_mode"
        private const val ENABLE_AUDIO_FX_PREF_STRING = "checkbox_enable_audiofx"
        private const val REDUCE_REFRESH_RATE_PREF_STRING = "checkbox_reduce_refresh_rate"
        private const val FULL_RANGE_PREF_STRING = "checkbox_full_range"
        private const val GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING =
            "checkbox_gamepad_touchpad_as_mouse"
        private const val GAMEPAD_MOTION_SENSORS_PREF_STRING = "checkbox_gamepad_motion_sensors"
        private const val GAMEPAD_MOTION_FALLBACK_PREF_STRING = "checkbox_gamepad_motion_fallback"

        private const val DEFAULT_RESOLUTION = "1280x720"
        private const val DEFAULT_FPS = "60"
        private const val DEFAULT_STRETCH = false
        private const val DEFAULT_SOPS = true
        private const val DEFAULT_DISABLE_TOASTS = false
        private const val DEFAULT_HOST_AUDIO = false
        private const val DEFAULT_DEADZONE = 7
        private const val DEFAULT_GAMEPAD_MOUSE_SPEED = 50
        private const val DEFAULT_OPACITY = 90
        private const val DEFAULT_MULTI_CONTROLLER = true
        private const val DEFAULT_USB_DRIVER = true
        private const val DEFAULT_VIDEO_FORMAT = "auto"

        private const val ONSCREEN_CONTROLLER_DEFAULT = false
        private const val ONLY_L3_R3_DEFAULT = false
        private const val SHOW_GUIDE_BUTTON_DEFAULT = true
        private const val DEFAULT_ENABLE_HDR = false
        private const val DEFAULT_ENABLE_PIP = false
        private const val DEFAULT_BIND_ALL_USB = false
        private const val DEFAULT_MOUSE_EMULATION = true
        private const val DEFAULT_ANALOG_STICK_FOR_SCROLLING = "right"
        private const val DEFAULT_MOUSE_NAV_BUTTONS = false
        private const val DEFAULT_UNLOCK_FPS = false
        private const val DEFAULT_VIBRATE_OSC = true
        private const val DEFAULT_VIBRATE_FALLBACK = false
        private const val DEFAULT_VIBRATE_FALLBACK_STRENGTH = 100
        private const val DEFAULT_FLIP_FACE_BUTTONS = false
        private const val DEFAULT_TOUCHSCREEN_TRACKPAD = true
        private const val DEFAULT_AUDIO_CONFIG = "2" // Stereo
        private const val DEFAULT_LATENCY_TOAST = false
        private const val DEFAULT_FRAME_PACING = "latency"
        private const val DEFAULT_ABSOLUTE_MOUSE_MODE = false
        private const val DEFAULT_ENABLE_AUDIO_FX = false
        private const val DEFAULT_REDUCE_REFRESH_RATE = false
        private const val DEFAULT_FULL_RANGE = false
        private const val DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE = false
        private const val DEFAULT_GAMEPAD_MOTION_SENSORS = true
        private const val DEFAULT_GAMEPAD_MOTION_FALLBACK = false

        const val FRAME_PACING_MIN_LATENCY = 0
        const val FRAME_PACING_BALANCED = 1
        const val FRAME_PACING_CAP_FPS = 2
        const val FRAME_PACING_MAX_SMOOTHNESS = 3

        const val RES_360P = "640x360"
        const val RES_480P = "854x480"
        const val RES_720P = "1280x720"
        const val RES_1080P = "1920x1080"
        const val RES_1440P = "2560x1440"
        const val RES_4K = "3840x2160"
        const val RES_NATIVE = "Native"

        // The numbers of getDefaultBitrate and isSquarishScreen, as in the Moonlight Qt code.
        private const val SQUARISH_RATIO = 1.3f
        private const val MAX_LINEAR_FPS = 60
        private const val REFERENCE_FPS = 60f
        private const val BITRATE_FPS_UNIT = 30f
        private const val KBPS_PER_MBPS = 1000

        private val X_SEPARATOR = Pattern.compile("x")

        private val STANDARD_SIZES =
            listOf(
                640 to 360,
                854 to 480,
                1280 to 720,
                1920 to 1080,
                2560 to 1440,
                3840 to 2160,
            )

        private val RESOLUTION_BY_HEIGHT =
            mapOf(
                360 to RES_360P,
                480 to RES_480P,
                1080 to RES_1080P,
                1440 to RES_1440P,
                2160 to RES_4K,
            )

        private val DEFAULT_LEGACY_MODE = Triple(1280, 720, 60)

        private val LEGACY_RESOLUTION_FPS =
            mapOf(
                "360p30" to Triple(640, 360, 30),
                "360p60" to Triple(640, 360, 60),
                "720p30" to Triple(1280, 720, 30),
                "720p60" to Triple(1280, 720, 60),
                "1080p30" to Triple(1920, 1080, 30),
                "1080p60" to Triple(1920, 1080, 60),
                "4K30" to Triple(3840, 2160, 30),
                "4K60" to Triple(3840, 2160, 60),
            )

        // Open question: collect some empirical data to see if these defaults make sense.
        // We're just using the values that the Shield used, as we have for years.
        private val PIXEL_VALS =
            intArrayOf(640 * 360, 854 * 480, 1280 * 720, 1920 * 1080, 2560 * 1440, 3840 * 2160, -1)
        private val FACTOR_VALS = intArrayOf(1, 2, 5, 10, 20, 40, -1)

        @Suppress("DEPRECATION") // Inherited setting, via the deprecated android.preference API.
        private fun defaultPreferences(context: Context): SharedPreferences =
            PreferenceManager.getDefaultSharedPreferences(context)

        // These strings are read with a default that is never null, so the platform never answers
        // null. Java would have failed with a null pointer exception on one that did.
        private fun SharedPreferences.getRequiredString(key: String, default: String): String =
            checkNotNull(getString(key, default)) { "$key has no value and no default" }

        @JvmStatic
        fun isNativeResolution(width: Int, height: Int): Boolean =
            // It's not a native resolution if it matches an existing resolution option
            (width to height) !in STANDARD_SIZES

        // If we have a screen that has semi-square dimensions, we may want to change our behavior
        // to allow any orientation and vertical+horizontal resolutions.
        @JvmStatic
        fun isSquarishScreen(width: Int, height: Int): Boolean {
            val longDim = max(width, height).toFloat()
            val shortDim = min(width, height).toFloat()

            // We just put the arbitrary cutoff for a square-ish screen at 1.3
            return longDim / shortDim < SQUARISH_RATIO
        }

        @JvmStatic
        fun isSquarishScreen(display: Display): Boolean {
            val width = display.mode.physicalWidth
            val height = display.mode.physicalHeight

            return isSquarishScreen(width, height)
        }

        private fun convertFromLegacyResolutionString(resString: String): String =
            when {
                resString.equals("360p", ignoreCase = true) -> RES_360P
                resString.equals("480p", ignoreCase = true) -> RES_480P
                resString.equals("720p", ignoreCase = true) -> RES_720P
                resString.equals("1080p", ignoreCase = true) -> RES_1080P
                resString.equals("1440p", ignoreCase = true) -> RES_1440P
                resString.equals("4K", ignoreCase = true) -> RES_4K
                // Should be unreachable
                else -> RES_720P
            }

        // Java's String.split drops the trailing empty strings, and Kotlin's does not.
        private fun getWidthFromResolutionString(resString: String): Int =
            X_SEPARATOR.split(resString)[0].toInt()

        private fun getHeightFromResolutionString(resString: String): Int =
            X_SEPARATOR.split(resString)[1].toInt()

        private fun getResolutionString(height: Int): String =
            // An unknown height gives 720p, like the 720p case.
            RESOLUTION_BY_HEIGHT[height] ?: RES_720P

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
        @JvmStatic
        @Suppress("LoopWithTooManyJumpStatements")
        fun getDefaultBitrate(resString: String, fpsString: String): Int {
            val width = getWidthFromResolutionString(resString)
            val height = getHeightFromResolutionString(resString)
            val fps = fpsString.toInt()

            // This logic is shamelessly stolen from Moonlight Qt:
            // https://github.com/moonlight-stream/moonlight-qt/blob/master/app/settings/streamingpreferences.cpp

            // Don't scale bitrate linearly beyond 60 FPS. It's definitely not a linear
            // bitrate increase for frame rate once we get to values that high.
            val frameRateFactor: Double =
                (if (fps <= MAX_LINEAR_FPS) fps.toDouble()
                else sqrt((fps / REFERENCE_FPS).toDouble()) * REFERENCE_FPS) / BITRATE_FPS_UNIT

            // Calculate the resolution factor by linear interpolation of the resolution table
            var resolutionFactor: Float
            val pixels = width * height
            var i = 0
            while (true) {
                if (pixels == PIXEL_VALS[i]) {
                    // We can bail immediately for exact matches
                    resolutionFactor = FACTOR_VALS[i].toFloat()
                    break
                } else if (pixels < PIXEL_VALS[i]) {
                    if (i == 0) {
                        // Never go below the lowest resolution entry
                        resolutionFactor = FACTOR_VALS[i].toFloat()
                    } else {
                        // Interpolate between the entry greater than the chosen resolution (i) and
                        // the entry less than the chosen resolution (i-1)
                        resolutionFactor =
                            ((pixels - PIXEL_VALS[i - 1]).toFloat() /
                                (PIXEL_VALS[i] - PIXEL_VALS[i - 1])) *
                                (FACTOR_VALS[i] - FACTOR_VALS[i - 1]) + FACTOR_VALS[i - 1]
                    }
                    break
                } else if (PIXEL_VALS[i] == -1) {
                    // Never go above the highest resolution entry
                    resolutionFactor = FACTOR_VALS[i - 1].toFloat()
                    break
                }
                i++
            }

            return Math.round(resolutionFactor * frameRateFactor).toInt() * KBPS_PER_MBPS
        }

        @JvmStatic
        fun getDefaultBitrate(context: Context): Int {
            val prefs = defaultPreferences(context)
            return getDefaultBitrate(
                prefs.getRequiredString(RESOLUTION_PREF_STRING, DEFAULT_RESOLUTION),
                prefs.getRequiredString(FPS_PREF_STRING, DEFAULT_FPS),
            )
        }

        private fun getVideoFormatValue(context: Context): FormatOption {
            val prefs = defaultPreferences(context)

            val str: String =
                prefs.getRequiredString(VIDEO_FORMAT_PREF_STRING, DEFAULT_VIDEO_FORMAT)
            return when (str) {
                "auto" -> FormatOption.AUTO
                "forceav1" -> FormatOption.FORCE_AV1
                "forceh265" -> FormatOption.FORCE_HEVC
                "neverh265" -> FormatOption.FORCE_H264
                // Should never get here
                else -> FormatOption.AUTO
            }
        }

        private fun getFramePacingValue(context: Context): Int {
            val prefs = defaultPreferences(context)

            // Migrate legacy never drop frames option to the new location
            if (prefs.contains(LEGACY_DISABLE_FRAME_DROP_PREF_STRING)) {
                val legacyNeverDropFrames =
                    prefs.getBoolean(LEGACY_DISABLE_FRAME_DROP_PREF_STRING, false)
                prefs.edit {
                    remove(LEGACY_DISABLE_FRAME_DROP_PREF_STRING)
                    putString(
                        FRAME_PACING_PREF_STRING,
                        if (legacyNeverDropFrames) "balanced" else "latency",
                    )
                }
            }

            val str: String =
                prefs.getRequiredString(FRAME_PACING_PREF_STRING, DEFAULT_FRAME_PACING)
            return when (str) {
                "latency" -> FRAME_PACING_MIN_LATENCY
                "balanced" -> FRAME_PACING_BALANCED
                "cap-fps" -> FRAME_PACING_CAP_FPS
                "smoothness" -> FRAME_PACING_MAX_SMOOTHNESS
                // Should never get here
                else -> FRAME_PACING_MIN_LATENCY
            }
        }

        private fun getAnalogStickForScrollingValue(context: Context): AnalogStickForScrolling {
            val prefs = defaultPreferences(context)

            val str: String =
                prefs.getRequiredString(
                    ANALOG_SCROLLING_PREF_STRING,
                    DEFAULT_ANALOG_STICK_FOR_SCROLLING,
                )
            return when (str) {
                "right" -> AnalogStickForScrolling.RIGHT
                "left" -> AnalogStickForScrolling.LEFT
                else -> AnalogStickForScrolling.NONE
            }
        }

        @JvmStatic
        fun resetStreamingSettings(context: Context) {
            // We consider resolution, FPS, bitrate, HDR, and video format as "streaming
            // settings" here
            val prefs = defaultPreferences(context)
            prefs.edit {
                remove(BITRATE_PREF_STRING)
                remove(BITRATE_PREF_OLD_STRING)
                remove(LEGACY_RES_FPS_PREF_STRING)
                remove(RESOLUTION_PREF_STRING)
                remove(FPS_PREF_STRING)
                remove(VIDEO_FORMAT_PREF_STRING)
                remove(ENABLE_HDR_PREF_STRING)
                remove(UNLOCK_FPS_STRING)
                remove(FULL_RANGE_PREF_STRING)
            }
        }

        @JvmStatic
        fun isShieldAtvFirmwareWithBrokenHdr(): Boolean =
            // This particular Shield TV firmware crashes when using HDR
            // https://www.nvidia.com/en-us/geforce/forums/notifications/comment/155192/
            Build.MANUFACTURER.equals("NVIDIA", ignoreCase = true) &&
                Build.FINGERPRINT.contains("PPR1.180610.011/4079208_2235.1395")

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
        @JvmStatic
        @Suppress("LongMethod")
        fun readPreferences(context: Context): PreferenceConfiguration {
            val prefs = defaultPreferences(context)
            val config = PreferenceConfiguration()

            // Migrate legacy preferences to the new locations
            if (prefs.contains(LEGACY_ENABLE_51_SURROUND_PREF_STRING)) {
                if (prefs.getBoolean(LEGACY_ENABLE_51_SURROUND_PREF_STRING, false)) {
                    prefs.edit {
                        remove(LEGACY_ENABLE_51_SURROUND_PREF_STRING)
                        putString(AUDIO_CONFIG_PREF_STRING, "51")
                    }
                }
            }

            val str = prefs.getString(LEGACY_RES_FPS_PREF_STRING, null)
            if (str != null) {
                // Should never get an unknown string: it means 720p at 60 FPS
                val (legacyWidth, legacyHeight, legacyFps) =
                    LEGACY_RESOLUTION_FPS[str] ?: DEFAULT_LEGACY_MODE
                config.width = legacyWidth
                config.height = legacyHeight
                config.fps = legacyFps

                prefs.edit {
                    remove(LEGACY_RES_FPS_PREF_STRING)
                    putString(RESOLUTION_PREF_STRING, getResolutionString(config.height))
                    putString(FPS_PREF_STRING, config.fps.toString())
                }
            } else {
                // Use the new preference location
                var resStr: String =
                    prefs.getRequiredString(RESOLUTION_PREF_STRING, DEFAULT_RESOLUTION)

                // Convert legacy resolution strings to the new style
                if (!resStr.contains("x")) {
                    resStr = convertFromLegacyResolutionString(resStr)
                    prefs.edit { putString(RESOLUTION_PREF_STRING, resStr) }
                }

                config.width = getWidthFromResolutionString(resStr)
                config.height = getHeightFromResolutionString(resStr)
                config.fps = prefs.getRequiredString(FPS_PREF_STRING, DEFAULT_FPS).toInt()
            }

            if (
                !prefs.contains(GAMEPAD_MOTION_SENSORS_PREF_STRING) &&
                    Build.VERSION.SDK_INT == Build.VERSION_CODES.S
            ) {
                // Android 12 has a nasty bug that causes crashes when the app touches the
                // InputDevice's associated InputDeviceSensorManager (just calling
                // getSensorManager() is enough). As a workaround, we will override the default
                // value for the gamepad motion sensor option to disabled on Android 12 to reduce
                // the impact of this bug.
                // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
                prefs.edit { putBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, false) }
            }

            // This must happen after the preferences migration to ensure the preferences are
            // populated
            config.bitrate =
                prefs.getInt(
                    BITRATE_PREF_STRING,
                    prefs.getInt(BITRATE_PREF_OLD_STRING, 0) * KBPS_PER_MBPS,
                )
            if (config.bitrate == 0) {
                config.bitrate = getDefaultBitrate(context)
            }

            val audioConfig: String =
                prefs.getRequiredString(AUDIO_CONFIG_PREF_STRING, DEFAULT_AUDIO_CONFIG)
            config.audioConfiguration =
                when (audioConfig) {
                    "71" -> MoonBridge.AUDIO_CONFIGURATION_71_SURROUND
                    "51" -> MoonBridge.AUDIO_CONFIGURATION_51_SURROUND
                    else /* "2" */ -> MoonBridge.AUDIO_CONFIGURATION_STEREO
                }

            config.videoFormat = getVideoFormatValue(context)
            config.framePacing = getFramePacingValue(context)

            config.analogStickForScrolling = getAnalogStickForScrollingValue(context)

            config.deadzonePercentage = prefs.getInt(DEADZONE_PREF_STRING, DEFAULT_DEADZONE)

            config.gamepadMouseSpeed =
                prefs.getInt(GAMEPAD_MOUSE_SPEED_PREF_STRING, DEFAULT_GAMEPAD_MOUSE_SPEED)

            config.oscOpacity = prefs.getInt(OSC_OPACITY_PREF_STRING, DEFAULT_OPACITY)

            // Checkbox preferences
            config.disableWarnings =
                prefs.getBoolean(DISABLE_TOASTS_PREF_STRING, DEFAULT_DISABLE_TOASTS)
            config.enableSops = prefs.getBoolean(SOPS_PREF_STRING, DEFAULT_SOPS)
            config.stretchVideo = prefs.getBoolean(STRETCH_PREF_STRING, DEFAULT_STRETCH)
            config.playHostAudio = prefs.getBoolean(HOST_AUDIO_PREF_STRING, DEFAULT_HOST_AUDIO)
            config.multiController =
                prefs.getBoolean(MULTI_CONTROLLER_PREF_STRING, DEFAULT_MULTI_CONTROLLER)
            config.usbDriver = prefs.getBoolean(USB_DRIVER_PREF_SRING, DEFAULT_USB_DRIVER)
            config.onscreenController =
                prefs.getBoolean(ONSCREEN_CONTROLLER_PREF_STRING, ONSCREEN_CONTROLLER_DEFAULT)
            config.onlyL3R3 = prefs.getBoolean(ONLY_L3_R3_PREF_STRING, ONLY_L3_R3_DEFAULT)
            config.showGuideButton =
                prefs.getBoolean(SHOW_GUIDE_BUTTON_PREF_STRING, SHOW_GUIDE_BUTTON_DEFAULT)
            config.enableHdr =
                prefs.getBoolean(ENABLE_HDR_PREF_STRING, DEFAULT_ENABLE_HDR) &&
                    !isShieldAtvFirmwareWithBrokenHdr()
            config.enablePip = prefs.getBoolean(ENABLE_PIP_PREF_STRING, DEFAULT_ENABLE_PIP)
            config.bindAllUsb = prefs.getBoolean(BIND_ALL_USB_STRING, DEFAULT_BIND_ALL_USB)
            config.mouseEmulation =
                prefs.getBoolean(MOUSE_EMULATION_STRING, DEFAULT_MOUSE_EMULATION)
            config.mouseNavButtons =
                prefs.getBoolean(MOUSE_NAV_BUTTONS_STRING, DEFAULT_MOUSE_NAV_BUTTONS)
            config.unlockFps = prefs.getBoolean(UNLOCK_FPS_STRING, DEFAULT_UNLOCK_FPS)
            config.vibrateOsc = prefs.getBoolean(VIBRATE_OSC_PREF_STRING, DEFAULT_VIBRATE_OSC)
            config.vibrateFallbackToDevice =
                prefs.getBoolean(VIBRATE_FALLBACK_PREF_STRING, DEFAULT_VIBRATE_FALLBACK)
            config.vibrateFallbackToDeviceStrength =
                prefs.getInt(
                    VIBRATE_FALLBACK_STRENGTH_PREF_STRING,
                    DEFAULT_VIBRATE_FALLBACK_STRENGTH,
                )
            config.flipFaceButtons =
                prefs.getBoolean(FLIP_FACE_BUTTONS_PREF_STRING, DEFAULT_FLIP_FACE_BUTTONS)
            config.touchscreenTrackpad =
                prefs.getBoolean(TOUCHSCREEN_TRACKPAD_PREF_STRING, DEFAULT_TOUCHSCREEN_TRACKPAD)
            config.enableLatencyToast =
                prefs.getBoolean(LATENCY_TOAST_PREF_STRING, DEFAULT_LATENCY_TOAST)
            config.absoluteMouseMode =
                prefs.getBoolean(ABSOLUTE_MOUSE_MODE_PREF_STRING, DEFAULT_ABSOLUTE_MOUSE_MODE)
            config.enableAudioFx =
                prefs.getBoolean(ENABLE_AUDIO_FX_PREF_STRING, DEFAULT_ENABLE_AUDIO_FX)
            config.reduceRefreshRate =
                prefs.getBoolean(REDUCE_REFRESH_RATE_PREF_STRING, DEFAULT_REDUCE_REFRESH_RATE)
            config.fullRange = prefs.getBoolean(FULL_RANGE_PREF_STRING, DEFAULT_FULL_RANGE)
            config.gamepadTouchpadAsMouse =
                prefs.getBoolean(
                    GAMEPAD_TOUCHPAD_AS_MOUSE_PREF_STRING,
                    DEFAULT_GAMEPAD_TOUCHPAD_AS_MOUSE,
                )
            config.gamepadMotionSensors =
                prefs.getBoolean(GAMEPAD_MOTION_SENSORS_PREF_STRING, DEFAULT_GAMEPAD_MOTION_SENSORS)
            config.gamepadMotionSensorsFallbackToDevice =
                prefs.getBoolean(
                    GAMEPAD_MOTION_FALLBACK_PREF_STRING,
                    DEFAULT_GAMEPAD_MOTION_FALLBACK,
                )

            return config
        }
    }
}
