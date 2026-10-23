package io.github.magalz.borealis.connection

import android.app.ActivityManager
import com.limelight.nvstream.NvConnectionListener
import com.limelight.nvstream.av.audio.AudioRenderer
import com.limelight.nvstream.av.video.VideoDecoderRenderer
import com.limelight.nvstream.jni.MoonBridge
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.util.ReflectionHelpers

/**
 * One call of `NvConnection` into `MoonBridge`: its name, its arguments as the connection passed
 * them, and whether the calling thread held the `MoonBridge` monitor at that moment.
 */
internal data class BridgeCall(
    val name: String,
    val args: List<Any?> = emptyList(),
    val locked: Boolean = false,
)

/**
 * Stands in for the native `moonlight-core` library, which a unit test does not have. It skips the
 * static initialiser that loads the library, sets the audio configurations the stream configuration
 * reads, records every call that `NvConnection` makes in the order it makes them, and answers like
 * the library does: the launch query parameters, the name of a stage, the result of a call.
 *
 * State lives in this object, which every test of one Robolectric sandbox shares: call [reset]
 * before each test.
 *
 * Use it with `@Config(shadows = [ShadowRecordingBridge::class, ...])`.
 */
@Implements(MoonBridge::class)
internal object ShadowRecordingBridge {
    const val LAUNCH_URL_QUERY_PARAMETERS = "&corever=1"
    private const val WAIT_MS = 30_000L
    private const val POLL_MS = 10L

    /** The stage that `HeldStageFailures` treats as the RTSP handshake. */
    const val RTSP_STAGE = 4

    val calls = CopyOnWriteArrayList<BridgeCall>()

    /** What `setupBridge` was given last, as the native code would call it back. */
    @Volatile var listener: NvConnectionListener? = null

    /** The result of every call that answers with an int, except `startConnection`. */
    @Volatile var intResult = 0

    /**
     * What `startConnection` does: it may block or call [listener] back, and returns the result.
     */
    @Volatile var onStartConnection: (BridgeCall) -> Int = { 0 }

    /** What `interruptConnection` does besides recording the call. */
    @Volatile var onInterrupt: () -> Unit = {}

    @Volatile var externalAddress: String? = null

    fun reset() {
        calls.clear()
        listener = null
        intResult = 0
        onStartConnection = { 0 }
        onInterrupt = {}
        externalAddress = null
    }

    /** The calls named [name], in order. */
    fun named(name: String): List<BridgeCall> = calls.filter { it.name == name }

    val names: List<String>
        get() = calls.map { it.name }

    /** Waits until [count] calls named [name] were made, and returns the last. */
    fun await(name: String, count: Int = 1): BridgeCall {
        val limit = System.currentTimeMillis() + WAIT_MS
        while (named(name).size < count) {
            check(System.currentTimeMillis() < limit) {
                "Timed out waiting for $count call(s) of $name; calls: $names"
            }
            Thread.sleep(POLL_MS)
        }
        return named(name)[count - 1]
    }

    private fun record(name: String, vararg args: Any?): BridgeCall =
        BridgeCall(name, args.toList(), Thread.holdsLock(MoonBridge::class.java)).also {
            calls += it
        }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun __staticInitializer__() {
        set("AUDIO_CONFIGURATION_STEREO", MoonBridge.AudioConfiguration(2, 0x3))
        set("AUDIO_CONFIGURATION_51_SURROUND", MoonBridge.AudioConfiguration(6, 0x3F))
        set("AUDIO_CONFIGURATION_71_SURROUND", MoonBridge.AudioConfiguration(8, 0x63F))
    }

    private fun set(name: String, value: MoonBridge.AudioConfiguration) {
        ReflectionHelpers.setStaticField(MoonBridge::class.java, name, value)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun getLaunchUrlQueryParameters(): String = LAUNCH_URL_QUERY_PARAMETERS

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun getStageName(stage: Int): String =
        if (stage == RTSP_STAGE) "RTSP handshake" else "stage $stage"

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun setupBridge(
        videoRenderer: VideoDecoderRenderer?,
        audioRenderer: AudioRenderer?,
        connectionListener: NvConnectionListener?,
    ) {
        listener = connectionListener
        record("setupBridge", videoRenderer, audioRenderer, connectionListener)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun cleanupBridge() {
        listener = null
        record("cleanupBridge")
    }

    @Suppress("unused", "LongParameterList")
    @Implementation
    @JvmStatic
    fun startConnection(
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
    ): Int {
        val call =
            record(
                "startConnection",
                address,
                appVersion,
                gfeVersion,
                rtspSessionUrl,
                serverCodecModeSupport,
                width,
                height,
                fps,
                bitrate,
                packetSize,
                streamingRemotely,
                audioConfiguration,
                supportedVideoFormats,
                clientRefreshRateX100,
                riAesKey,
                riAesIv,
                videoCapabilities,
                colorSpace,
                colorRange,
            )
        return onStartConnection(call)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun stopConnection() {
        record("stopConnection")
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun interruptConnection() {
        record("interruptConnection")
        onInterrupt()
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendMouseMove(deltaX: Short, deltaY: Short) {
        record("sendMouseMove", deltaX, deltaY)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendMousePosition(x: Short, y: Short, referenceWidth: Short, referenceHeight: Short) {
        record("sendMousePosition", x, y, referenceWidth, referenceHeight)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendMouseMoveAsMousePosition(
        deltaX: Short,
        deltaY: Short,
        referenceWidth: Short,
        referenceHeight: Short,
    ) {
        record("sendMouseMoveAsMousePosition", deltaX, deltaY, referenceWidth, referenceHeight)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendMouseButton(buttonEvent: Byte, mouseButton: Byte) {
        record("sendMouseButton", buttonEvent, mouseButton)
    }

    @Suppress("unused", "LongParameterList")
    @Implementation
    @JvmStatic
    fun sendMultiControllerInput(
        controllerNumber: Short,
        activeGamepadMask: Short,
        buttonFlags: Int,
        leftTrigger: Byte,
        rightTrigger: Byte,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
    ) {
        record(
            "sendMultiControllerInput",
            controllerNumber,
            activeGamepadMask,
            buttonFlags,
            leftTrigger,
            rightTrigger,
            leftStickX,
            leftStickY,
            rightStickX,
            rightStickY,
        )
    }

    @Suppress("unused", "LongParameterList")
    @Implementation
    @JvmStatic
    fun sendTouchEvent(
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
        contactAreaMajor: Float,
        contactAreaMinor: Float,
        rotation: Short,
    ): Int {
        record(
            "sendTouchEvent",
            eventType,
            pointerId,
            x,
            y,
            pressure,
            contactAreaMajor,
            contactAreaMinor,
            rotation,
        )
        return intResult
    }

    @Suppress("unused", "LongParameterList")
    @Implementation
    @JvmStatic
    fun sendPenEvent(
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
    ): Int {
        record(
            "sendPenEvent",
            eventType,
            toolType,
            penButtons,
            x,
            y,
            pressure,
            contactAreaMajor,
            contactAreaMinor,
            rotation,
            tilt,
        )
        return intResult
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendControllerArrivalEvent(
        controllerNumber: Byte,
        activeGamepadMask: Short,
        type: Byte,
        supportedButtonFlags: Int,
        capabilities: Short,
    ): Int {
        record(
            "sendControllerArrivalEvent",
            controllerNumber,
            activeGamepadMask,
            type,
            supportedButtonFlags,
            capabilities,
        )
        return intResult
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendControllerTouchEvent(
        controllerNumber: Byte,
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
    ): Int {
        record("sendControllerTouchEvent", controllerNumber, eventType, pointerId, x, y, pressure)
        return intResult
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendControllerMotionEvent(
        controllerNumber: Byte,
        motionType: Byte,
        x: Float,
        y: Float,
        z: Float,
    ): Int {
        record("sendControllerMotionEvent", controllerNumber, motionType, x, y, z)
        return intResult
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendControllerBatteryEvent(
        controllerNumber: Byte,
        batteryState: Byte,
        batteryPercentage: Byte,
    ): Int {
        record("sendControllerBatteryEvent", controllerNumber, batteryState, batteryPercentage)
        return intResult
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendKeyboardInput(keyMap: Short, keyDirection: Byte, modifier: Byte, flags: Byte) {
        record("sendKeyboardInput", keyMap, keyDirection, modifier, flags)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendMouseHighResScroll(scrollAmount: Short) {
        record("sendMouseHighResScroll", scrollAmount)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendMouseHighResHScroll(scrollAmount: Short) {
        record("sendMouseHighResHScroll", scrollAmount)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendUtf8Text(text: ByteArray?) {
        record("sendUtf8Text", text)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun requestIdrFrame() {
        record("requestIdrFrame")
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun findExternalAddressIP4(stunHostName: String?, stunPort: Int): String? {
        record("findExternalAddressIP4", stunHostName, stunPort)
        return externalAddress
    }
}

/**
 * The activity manager of Robolectric, which always answers that the user is no monkey. A test sets
 * [monkey] before it builds a connection, which reads it once. Nothing else of the activity manager
 * is used by the tests that load this shadow.
 */
@Implements(ActivityManager::class)
@Suppress("UtilityClassWithPublicConstructor") // The shadow needs the public empty constructor.
class ShadowMonkeyActivityManager {
    companion object {
        @Volatile var monkey = false

        @Suppress("unused") @Implementation @JvmStatic fun isUserAMonkey(): Boolean = monkey
    }
}
