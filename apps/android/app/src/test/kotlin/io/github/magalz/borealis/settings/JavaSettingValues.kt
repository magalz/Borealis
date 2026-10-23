package io.github.magalz.borealis.settings

import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.settings.catalog.Setting
import io.github.magalz.borealis.settings.catalog.Settings

private const val STEREO_CHANNELS = 2
private const val SURROUND_51_CHANNELS = 6

/** The Java field that holds each catalog setting, as a catalog-typed value. */
internal fun javaValues(c: PreferenceConfiguration): Map<Setting<*>, Any> =
    mapOf(
        Settings.RESOLUTION to "${c.width}x${c.height}",
        Settings.FRAME_RATE to c.fps.toString(),
        Settings.FRAME_PACING to framePacing(c),
        Settings.STRETCH_VIDEO to c.stretchVideo,
        Settings.AUDIO_CONFIG to audioConfig(c),
        Settings.AUDIO_FX to c.enableAudioFx,
        Settings.DEADZONE to c.deadzonePercentage,
        Settings.MULTI_CONTROLLER to c.multiController,
        Settings.USB_DRIVER to c.usbDriver,
        Settings.USB_BIND_ALL to c.bindAllUsb,
        Settings.MOUSE_EMULATION to c.mouseEmulation,
        Settings.ANALOG_SCROLLING to analogScrolling(c),
        Settings.GAMEPAD_MOUSE_SPEED to c.gamepadMouseSpeed,
        Settings.VIBRATE_FALLBACK to c.vibrateFallbackToDevice,
        Settings.VIBRATE_FALLBACK_STRENGTH to c.vibrateFallbackToDeviceStrength,
        Settings.FLIP_FACE_BUTTONS to c.flipFaceButtons,
        Settings.GAMEPAD_TOUCHPAD_AS_MOUSE to c.gamepadTouchpadAsMouse,
        Settings.GAMEPAD_MOTION_SENSORS to c.gamepadMotionSensors,
        Settings.GAMEPAD_MOTION_FALLBACK to c.gamepadMotionSensorsFallbackToDevice,
        Settings.TOUCHSCREEN_TRACKPAD to c.touchscreenTrackpad,
        Settings.MOUSE_NAV_BUTTONS to c.mouseNavButtons,
        Settings.ABSOLUTE_MOUSE_MODE to c.absoluteMouseMode,
        Settings.SHOW_ONSCREEN_CONTROLS to c.onscreenController,
        Settings.VIBRATE_OSC to c.vibrateOsc,
        Settings.ONLY_L3_R3 to c.onlyL3R3,
        Settings.SHOW_GUIDE_BUTTON to c.showGuideButton,
        Settings.OSC_OPACITY to c.oscOpacity,
        Settings.ENABLE_SOPS to c.enableSops,
        Settings.HOST_AUDIO to c.playHostAudio,
        Settings.ENABLE_PIP to c.enablePip,
        Settings.UNLOCK_FPS to c.unlockFps,
        Settings.REDUCE_REFRESH_RATE to c.reduceRefreshRate,
        Settings.DISABLE_WARNINGS to c.disableWarnings,
        Settings.VIDEO_FORMAT to videoFormat(c),
        Settings.ENABLE_HDR to c.enableHdr,
        Settings.FULL_RANGE to c.fullRange,
        Settings.POST_STREAM_TOAST to c.enableLatencyToast,
    )

private fun framePacing(c: PreferenceConfiguration): String =
    when (c.framePacing) {
        PreferenceConfiguration.FRAME_PACING_MIN_LATENCY -> "latency"
        PreferenceConfiguration.FRAME_PACING_BALANCED -> "balanced"
        PreferenceConfiguration.FRAME_PACING_CAP_FPS -> "cap-fps"
        else -> "smoothness"
    }

private fun audioConfig(c: PreferenceConfiguration): String =
    when (checkNotNull(c.audioConfiguration).channelCount) {
        STEREO_CHANNELS -> "2"
        SURROUND_51_CHANNELS -> "51"
        else -> "71"
    }

private fun analogScrolling(c: PreferenceConfiguration): String =
    when (c.analogStickForScrolling) {
        PreferenceConfiguration.AnalogStickForScrolling.RIGHT -> "right"
        PreferenceConfiguration.AnalogStickForScrolling.LEFT -> "left"
        else -> "none"
    }

private fun videoFormat(c: PreferenceConfiguration): String =
    when (c.videoFormat) {
        PreferenceConfiguration.FormatOption.AUTO -> "auto"
        PreferenceConfiguration.FormatOption.FORCE_AV1 -> "forceav1"
        PreferenceConfiguration.FormatOption.FORCE_HEVC -> "forceh265"
        else -> "neverh265"
    }
