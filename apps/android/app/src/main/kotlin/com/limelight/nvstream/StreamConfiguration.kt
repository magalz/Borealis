package com.limelight.nvstream

import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.jni.MoonBridge

class StreamConfiguration private constructor() {
    // Set default attributes
    var app: NvApp? = NvApp("Steam")
        private set

    var width = DEFAULT_WIDTH
        private set

    var height = DEFAULT_HEIGHT
        private set

    var refreshRate = DEFAULT_REFRESH_RATE
        private set

    var launchRefreshRate = DEFAULT_REFRESH_RATE
        private set

    var clientRefreshRateX100 = 0
        private set

    var bitrate = DEFAULT_BITRATE
        private set

    var sops = true
        private set

    var adaptiveResolutionEnabled = false
        private set

    var playLocalAudio = false
        private set

    var maxPacketSize = DEFAULT_MAX_PACKET_SIZE
        private set

    var remote = STREAM_CFG_AUTO
        private set

    var audioConfiguration: MoonBridge.AudioConfiguration? = MoonBridge.AUDIO_CONFIGURATION_STEREO
        private set

    var supportedVideoFormats = MoonBridge.VIDEO_FORMAT_H264
        private set

    var attachedGamepadMask = 0
        private set

    var colorRange = 0
        private set

    var colorSpace = 0
        private set

    var persistGamepadsAfterDisconnect = false
        private set

    // BOREALIS: the user chose Resume, Continue or Try again for the app that runs, so a start
    // that finds it running joins it without asking (host.launch)
    var resumeChosen = false
        private set

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 12 backlog).
    @Suppress("TooManyFunctions")
    class Builder {
        private val config = StreamConfiguration()

        fun setApp(app: NvApp?): Builder = apply { config.app = app }

        fun setRemoteConfiguration(remote: Int): Builder = apply { config.remote = remote }

        fun setResolution(width: Int, height: Int): Builder = apply {
            config.width = width
            config.height = height
        }

        fun setRefreshRate(refreshRate: Int): Builder = apply { config.refreshRate = refreshRate }

        fun setLaunchRefreshRate(refreshRate: Int): Builder = apply {
            config.launchRefreshRate = refreshRate
        }

        fun setBitrate(bitrate: Int): Builder = apply { config.bitrate = bitrate }

        fun setEnableSops(enable: Boolean): Builder = apply { config.sops = enable }

        fun enableAdaptiveResolution(enable: Boolean): Builder = apply {
            config.adaptiveResolutionEnabled = enable
        }

        fun enableLocalAudioPlayback(enable: Boolean): Builder = apply {
            config.playLocalAudio = enable
        }

        fun setMaxPacketSize(maxPacketSize: Int): Builder = apply {
            config.maxPacketSize = maxPacketSize
        }

        fun setAttachedGamepadMask(attachedGamepadMask: Int): Builder = apply {
            config.attachedGamepadMask = attachedGamepadMask
        }

        fun setAttachedGamepadMaskByCount(gamepadCount: Int): Builder = apply {
            config.attachedGamepadMask = 0
            for (i in 0 until MAX_GAMEPADS) {
                if (gamepadCount > i) {
                    config.attachedGamepadMask = config.attachedGamepadMask or (1 shl i)
                }
            }
        }

        fun setPersistGamepadsAfterDisconnect(value: Boolean): Builder = apply {
            config.persistGamepadsAfterDisconnect = value
        }

        fun setClientRefreshRateX100(refreshRateX100: Int): Builder = apply {
            config.clientRefreshRateX100 = refreshRateX100
        }

        fun setAudioConfiguration(audioConfig: MoonBridge.AudioConfiguration?): Builder = apply {
            config.audioConfiguration = audioConfig
        }

        fun setSupportedVideoFormats(supportedVideoFormats: Int): Builder = apply {
            config.supportedVideoFormats = supportedVideoFormats
        }

        fun setColorRange(colorRange: Int): Builder = apply { config.colorRange = colorRange }

        fun setColorSpace(colorSpace: Int): Builder = apply { config.colorSpace = colorSpace }

        fun setResumeChosen(resumeChosen: Boolean): Builder = apply {
            config.resumeChosen = resumeChosen
        }

        fun build(): StreamConfiguration = config
    }

    companion object {
        const val INVALID_APP_ID = 0

        const val STREAM_CFG_LOCAL = 0
        const val STREAM_CFG_REMOTE = 1
        const val STREAM_CFG_AUTO = 2

        private const val DEFAULT_WIDTH = 1280
        private const val DEFAULT_HEIGHT = 720
        private const val DEFAULT_REFRESH_RATE = 60
        private const val DEFAULT_BITRATE = 10000
        private const val DEFAULT_MAX_PACKET_SIZE = 1024
        private const val MAX_GAMEPADS = 4
    }
}
