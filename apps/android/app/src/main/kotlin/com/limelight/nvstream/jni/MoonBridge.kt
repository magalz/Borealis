package com.limelight.nvstream.jni

import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import io.github.magalz.borealis.logging.NativeLogForwarder

/**
 * The bridge to the native `moonlight-core` library. The C code finds every native by the JNI name
 * `Java_com_limelight_nvstream_jni_MoonBridge_<name>` and every callback by a `GetStaticMethodID`
 * lookup with a name and a descriptor, so the name, the package and the JVM signature of every
 * member stay as they were in Java; `MoonBridgeJniContractTest` reads the C sources and checks
 * them.
 *
 * Every non-private member is `@JvmStatic`, `@JvmField` or `const`: a Robolectric shadow replaces the
 * static initializer of this class, and an instance member (or a companion) would dereference the
 * `INSTANCE` that the initializer never set. The private state needs no annotation: it is a static
 * field that only this class reads.
 */
// Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
@Suppress("TooManyFunctions")
object MoonBridge {
    /* See documentation in Limelight.h for information about these functions and constants */

    @JvmField
    val AUDIO_CONFIGURATION_STEREO = AudioConfiguration(channelCount = 2, channelMask = 0x3)

    @JvmField
    val AUDIO_CONFIGURATION_51_SURROUND = AudioConfiguration(channelCount = 6, channelMask = 0x3F)

    @JvmField
    val AUDIO_CONFIGURATION_71_SURROUND = AudioConfiguration(channelCount = 8, channelMask = 0x63F)

    const val VIDEO_FORMAT_H264 = 0x0001
    const val VIDEO_FORMAT_H265 = 0x0100
    const val VIDEO_FORMAT_H265_MAIN10 = 0x0200
    const val VIDEO_FORMAT_AV1_MAIN8 = 0x1000
    const val VIDEO_FORMAT_AV1_MAIN10 = 0x2000

    const val VIDEO_FORMAT_MASK_H264 = 0x000F
    const val VIDEO_FORMAT_MASK_H265 = 0x0F00
    const val VIDEO_FORMAT_MASK_AV1 = 0xF000
    const val VIDEO_FORMAT_MASK_10BIT = 0x2200

    const val BUFFER_TYPE_SPS = 1
    const val BUFFER_TYPE_PPS = 2
    const val BUFFER_TYPE_VPS = 3

    const val FRAME_TYPE_IDR = 1

    const val COLORSPACE_REC_601 = 0
    const val COLORSPACE_REC_709 = 1
    const val COLORSPACE_REC_2020 = 2

    const val COLOR_RANGE_LIMITED = 0
    const val COLOR_RANGE_FULL = 1

    const val CAPABILITY_DIRECT_SUBMIT = 1
    const val CAPABILITY_REFERENCE_FRAME_INVALIDATION_AVC = 2
    const val CAPABILITY_REFERENCE_FRAME_INVALIDATION_HEVC = 4
    const val CAPABILITY_REFERENCE_FRAME_INVALIDATION_AV1 = 0x40

    const val DR_OK = 0
    const val DR_NEED_IDR = -1

    const val CONN_STATUS_OKAY = 0
    const val CONN_STATUS_POOR = 1

    const val ML_ERROR_GRACEFUL_TERMINATION = 0
    const val ML_ERROR_NO_VIDEO_TRAFFIC = -100
    const val ML_ERROR_NO_VIDEO_FRAME = -101
    const val ML_ERROR_UNEXPECTED_EARLY_TERMINATION = -102
    const val ML_ERROR_PROTECTED_CONTENT = -103
    const val ML_ERROR_FRAME_CONVERSION = -104

    const val ML_PORT_FLAG_TCP_47984 = 0x0001
    const val ML_PORT_FLAG_TCP_47989 = 0x0002

    const val ML_TEST_RESULT_INCONCLUSIVE = 0xFFFFFFFF.toInt()

    const val SS_KBE_FLAG_NON_NORMALIZED: Byte = 0x01

    const val LI_ERR_UNSUPPORTED = -5501

    const val LI_TOUCH_EVENT_HOVER: Byte = 0x00
    const val LI_TOUCH_EVENT_DOWN: Byte = 0x01
    const val LI_TOUCH_EVENT_UP: Byte = 0x02
    const val LI_TOUCH_EVENT_MOVE: Byte = 0x03
    const val LI_TOUCH_EVENT_CANCEL: Byte = 0x04
    const val LI_TOUCH_EVENT_BUTTON_ONLY: Byte = 0x05
    const val LI_TOUCH_EVENT_HOVER_LEAVE: Byte = 0x06
    const val LI_TOUCH_EVENT_CANCEL_ALL: Byte = 0x07

    const val LI_TOOL_TYPE_UNKNOWN: Byte = 0x00
    const val LI_TOOL_TYPE_PEN: Byte = 0x01
    const val LI_TOOL_TYPE_ERASER: Byte = 0x02

    const val LI_PEN_BUTTON_PRIMARY: Byte = 0x01
    const val LI_PEN_BUTTON_SECONDARY: Byte = 0x02

    const val LI_TILT_UNKNOWN: Byte = 0xFF.toByte()
    const val LI_ROT_UNKNOWN: Short = 0xFFFF.toShort()

    const val LI_CTYPE_UNKNOWN: Byte = 0x00
    const val LI_CTYPE_XBOX: Byte = 0x01
    const val LI_CTYPE_PS: Byte = 0x02
    const val LI_CTYPE_NINTENDO: Byte = 0x03
    const val LI_CTYPE_STEAM: Byte = 0x04

    const val LI_CCAP_ANALOG_TRIGGERS: Short = 0x01
    const val LI_CCAP_RUMBLE: Short = 0x02
    const val LI_CCAP_TRIGGER_RUMBLE: Short = 0x04
    const val LI_CCAP_TOUCHPAD: Short = 0x08
    const val LI_CCAP_ACCEL: Short = 0x10
    const val LI_CCAP_GYRO: Short = 0x20
    const val LI_CCAP_BATTERY_STATE: Short = 0x40
    const val LI_CCAP_RGB_LED: Short = 0x80

    const val LI_MOTION_TYPE_ACCEL: Byte = 0x01
    const val LI_MOTION_TYPE_GYRO: Byte = 0x02

    const val LI_BATTERY_STATE_UNKNOWN: Byte = 0x00
    const val LI_BATTERY_STATE_NOT_PRESENT: Byte = 0x01
    const val LI_BATTERY_STATE_DISCHARGING: Byte = 0x02
    const val LI_BATTERY_STATE_CHARGING: Byte = 0x03
    const val LI_BATTERY_STATE_NOT_CHARGING: Byte = 0x04 // Connected to power but not charging
    const val LI_BATTERY_STATE_FULL: Byte = 0x05

    const val LI_BATTERY_PERCENTAGE_UNKNOWN: Byte = 0xFF.toByte()

    // The bit layout of the packed audio configuration and of the slice count (Limelight.h)
    private const val MAGIC_BYTE = 0xCA
    private const val BYTE_MASK = 0xFF
    private const val SHORT_MASK = 0xFFFF
    private const val BYTE_BITS = 8
    private const val SHORT_BITS = 16
    private const val SLICES_SHIFT = 24

    private var audioRenderer: AudioRenderer? = null
    private var videoRenderer: VideoDecoderRenderer? = null
    private var connectionListener: NvConnectionListener? = null

    init {
        System.loadLibrary("moonlight-core")
        init()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("FunctionNaming")
    @JvmStatic
    fun CAPABILITY_SLICES_PER_FRAME(slices: Byte): Int = slices.toInt() shl SLICES_SHIFT

    class AudioConfiguration {
        @JvmField val channelCount: Int

        @JvmField val channelMask: Int

        constructor(channelCount: Int, channelMask: Int) {
            this.channelCount = channelCount
            this.channelMask = channelMask
        }

        // Creates an AudioConfiguration from the integer value returned by moonlight-common-c
        // See CHANNEL_COUNT_FROM_AUDIO_CONFIGURATION() and CHANNEL_MASK_FROM_AUDIO_CONFIGURATION()
        // in Limelight.h. It is internal, not private (the object calls it), so it is public in the
        // class file.
        internal constructor(audioConfiguration: Int) {
            // Check the magic byte before decoding to make sure we got something that's actually
            // a MAKE_AUDIO_CONFIGURATION()-based value and not something else like an older version
            // hardcoded AUDIO_CONFIGURATION value from an earlier version of moonlight-common-c.
            require((audioConfiguration and BYTE_MASK) == MAGIC_BYTE) {
                "Audio configuration has invalid magic byte!"
            }

            channelCount = (audioConfiguration shr BYTE_BITS) and BYTE_MASK
            channelMask = (audioConfiguration shr SHORT_BITS) and SHORT_MASK
        }

        // See SURROUNDAUDIOINFO_FROM_AUDIO_CONFIGURATION() in Limelight.h
        val surroundAudioInfo: Int
            get() = (channelMask shl SHORT_BITS) or channelCount

        override fun equals(other: Any?): Boolean {
            if (other is AudioConfiguration) {
                return this.toInt() == other.toInt()
            }

            return false
        }

        override fun hashCode(): Int = toInt()

        // Returns the integer value expected by moonlight-common-c
        // See MAKE_AUDIO_CONFIGURATION() in Limelight.h
        fun toInt(): Int =
            (channelMask shl SHORT_BITS) or (channelCount shl BYTE_BITS) or MAGIC_BYTE
    }

    @JvmStatic
    fun bridgeDrSetup(videoFormat: Int, width: Int, height: Int, redrawRate: Int): Int =
        videoRenderer?.setup(videoFormat, width, height, redrawRate) ?: -1

    @JvmStatic
    fun bridgeDrStart() {
        videoRenderer?.start()
    }

    @JvmStatic
    fun bridgeDrStop() {
        videoRenderer?.stop()
    }

    @JvmStatic
    fun bridgeDrCleanup() {
        videoRenderer?.cleanup()
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongParameterList")
    @JvmStatic
    fun bridgeDrSubmitDecodeUnit(
        decodeUnitData: ByteArray?,
        decodeUnitLength: Int,
        decodeUnitType: Int,
        frameNumber: Int,
        frameType: Int,
        frameHostProcessingLatency: Char,
        receiveTimeUs: Long,
        enqueueTimeUs: Long,
    ): Int =
        videoRenderer?.submitDecodeUnit(
            decodeUnitData,
            decodeUnitLength,
            decodeUnitType,
            frameNumber,
            frameType,
            frameHostProcessingLatency,
            receiveTimeUs,
            enqueueTimeUs,
        ) ?: DR_OK

    @JvmStatic
    fun bridgeArInit(audioConfiguration: Int, sampleRate: Int, samplesPerFrame: Int): Int =
        audioRenderer?.setup(AudioConfiguration(audioConfiguration), sampleRate, samplesPerFrame)
            ?: -1

    @JvmStatic
    fun bridgeArStart() {
        audioRenderer?.start()
    }

    @JvmStatic
    fun bridgeArStop() {
        audioRenderer?.stop()
    }

    @JvmStatic
    fun bridgeArCleanup() {
        audioRenderer?.cleanup()
    }

    @JvmStatic
    fun bridgeArPlaySample(pcmData: ShortArray?) {
        audioRenderer?.playDecodedAudio(pcmData)
    }

    @JvmStatic
    fun bridgeClStageStarting(stage: Int) {
        connectionListener?.stageStarting(getStageName(stage))
    }

    @JvmStatic
    fun bridgeClStageComplete(stage: Int) {
        connectionListener?.stageComplete(getStageName(stage))
    }

    // The listener takes a non-null stage name, and the native lookup always answers one.
    @JvmStatic
    fun bridgeClStageFailed(stage: Int, errorCode: Int) {
        connectionListener?.stageFailed(
            checkNotNull(getStageName(stage)),
            getPortFlagsFromStage(stage),
            errorCode,
        )
    }

    @JvmStatic
    fun bridgeClConnectionStarted() {
        connectionListener?.connectionStarted()
    }

    @JvmStatic
    fun bridgeClConnectionTerminated(errorCode: Int) {
        connectionListener?.connectionTerminated(errorCode)
    }

    @JvmStatic
    fun bridgeClRumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        connectionListener?.rumble(controllerNumber, lowFreqMotor, highFreqMotor)
    }

    @JvmStatic
    fun bridgeClConnectionStatusUpdate(connectionStatus: Int) {
        connectionListener?.connectionStatusUpdate(connectionStatus)
    }

    @JvmStatic
    fun bridgeClSetHdrMode(enabled: Boolean, hdrMetadata: ByteArray?) {
        connectionListener?.setHdrMode(enabled, hdrMetadata)
    }

    @JvmStatic
    fun bridgeClRumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        connectionListener?.rumbleTriggers(controllerNumber, leftTrigger, rightTrigger)
    }

    @JvmStatic
    fun bridgeClSetMotionEventState(controllerNumber: Short, eventType: Byte, sampleRateHz: Short) {
        connectionListener?.setMotionEventState(controllerNumber, eventType, sampleRateHz)
    }

    @JvmStatic
    fun bridgeClSetControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        connectionListener?.setControllerLED(controllerNumber, r, g, b)
    }

    // BOREALIS: called from borealis_log.c for each moonlight-common-c log line; false makes the native side use logcat
    // The native side never passes null; a null throws, and the native side clears the exception and
    // falls back to logcat too.
    @JvmStatic
    fun bridgeClLogMessage(message: ByteArray?): Boolean =
        NativeLogForwarder.forward(checkNotNull(message))

    @JvmStatic
    fun setupBridge(
        videoRenderer: VideoDecoderRenderer?,
        audioRenderer: AudioRenderer?,
        connectionListener: NvConnectionListener?,
    ) {
        MoonBridge.videoRenderer = videoRenderer
        MoonBridge.audioRenderer = audioRenderer
        MoonBridge.connectionListener = connectionListener
    }

    @JvmStatic
    fun cleanupBridge() {
        MoonBridge.videoRenderer = null
        MoonBridge.audioRenderer = null
        MoonBridge.connectionListener = null
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongParameterList")
    @JvmStatic
    external fun startConnection(
        address: String?,
        appVersion: String?,
        gfeVersion: String?,
        rtspSessionUrl: String?,
        serverCodecModeSupport: Int,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        packetSize: Int,
        streamingRemotely: Int,
        audioConfiguration: Int,
        supportedVideoFormats: Int,
        clientRefreshRateX100: Int,
        riAesKey: ByteArray?,
        riAesIv: ByteArray?,
        videoCapabilities: Int,
        colorSpace: Int,
        colorRange: Int,
    ): Int

    @JvmStatic external fun stopConnection()

    @JvmStatic external fun interruptConnection()

    @JvmStatic external fun sendMouseMove(deltaX: Short, deltaY: Short)

    @JvmStatic
    external fun sendMousePosition(
        x: Short,
        y: Short,
        referenceWidth: Short,
        referenceHeight: Short,
    )

    @JvmStatic
    external fun sendMouseMoveAsMousePosition(
        deltaX: Short,
        deltaY: Short,
        referenceWidth: Short,
        referenceHeight: Short,
    )

    @JvmStatic external fun sendMouseButton(buttonEvent: Byte, mouseButton: Byte)

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongParameterList")
    @JvmStatic
    external fun sendMultiControllerInput(
        controllerNumber: Short,
        activeGamepadMask: Short,
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    )

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongParameterList")
    @JvmStatic
    external fun sendTouchEvent(
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
        contactAreaMajor: Float,
        contactAreaMinor: Float,
        rotation: Short,
    ): Int

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongParameterList")
    @JvmStatic
    external fun sendPenEvent(
        eventType: Byte,
        toolType: Byte,
        penButtons: Byte,
        x: Float,
        y: Float,
        pressure: Float,
        contactAreaMajor: Float,
        contactAreaMinor: Float,
        rotation: Short,
        tilt: Byte,
    ): Int

    @JvmStatic
    external fun sendControllerArrivalEvent(
        controllerNumber: Byte,
        activeGamepadMask: Short,
        type: Byte,
        supportedButtonFlags: Int,
        capabilities: Short,
    ): Int

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 14 backlog).
    @Suppress("LongParameterList")
    @JvmStatic
    external fun sendControllerTouchEvent(
        controllerNumber: Byte,
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
    ): Int

    @JvmStatic
    external fun sendControllerMotionEvent(
        controllerNumber: Byte,
        motionType: Byte,
        x: Float,
        y: Float,
        z: Float,
    ): Int

    @JvmStatic
    external fun sendControllerBatteryEvent(
        controllerNumber: Byte,
        batteryState: Byte,
        batteryPercentage: Byte,
    ): Int

    @JvmStatic
    external fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte)

    @JvmStatic external fun sendMouseHighResScroll(scrollAmount: Short)

    @JvmStatic external fun sendMouseHighResHScroll(scrollAmount: Short)

    // BOREALIS: standard UTF-8 bytes, not a String: GetStringUTFChars gives modified UTF-8
    @JvmStatic external fun sendUtf8Text(text: ByteArray?)

    // BOREALIS: asks the host for a new keyframe
    @JvmStatic external fun requestIdrFrame()

    @JvmStatic external fun getStageName(stage: Int): String?

    @JvmStatic external fun findExternalAddressIP4(stunHostName: String?, stunPort: Int): String?

    @JvmStatic external fun getPendingAudioDuration(): Int

    @JvmStatic
    external fun testClientConnectivity(
        testServerHostName: String?,
        referencePort: Int,
        testFlags: Int,
    ): Int

    @JvmStatic external fun getPortFlagsFromStage(stage: Int): Int

    @JvmStatic external fun getPortFlagsFromTerminationErrorCode(errorCode: Int): Int

    @JvmStatic external fun stringifyPortFlags(portFlags: Int, separator: String?): String?

    // The RTT is in the top 32 bits, and the RTT variance is in the bottom 32 bits
    @JvmStatic external fun getEstimatedRttInfo(): Long

    @JvmStatic external fun getLaunchUrlQueryParameters(): String?

    @JvmStatic external fun guessControllerType(vendorId: Int, productId: Int): Byte

    @JvmStatic external fun guessControllerHasPaddles(vendorId: Int, productId: Int): Boolean

    @JvmStatic external fun guessControllerHasShareButton(vendorId: Int, productId: Int): Boolean

    @JvmStatic external fun init()
}
