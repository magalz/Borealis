package io.github.magalz.borealis.stream.game.motion

import android.util.SparseArray
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import com.limelight.R
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.game.GameRig
import io.github.magalz.borealis.stream.game.GameTestBase
import java.util.concurrent.TimeUnit
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/** The width of the stream view in the rig: a 16:9 picture on the 320 pixel wide test display. */
internal const val STREAM_WIDTH = 320

/** The height of the stream view in the rig. */
internal const val STREAM_HEIGHT = 180

/** Where the stream view sits in the background touch view: its top, under the free space. */
internal const val STREAM_TOP = 145

/**
 * One call that reached the native library, as the tests compare it: the name of the call and its
 * arguments with their types (`Short` for the coordinates, `Byte` for a button).
 */
internal data class Sent(val name: String, val args: List<Any?>)

/** Every call that reached the native library since the test began, in order. */
internal fun sent(): List<Sent> = ShadowRecordingBridge.calls.map { Sent(it.name, it.args) }

/** Forgets the calls so far. */
internal fun forgetSent() = ShadowRecordingBridge.calls.clear()

/** `sendMousePosition`: the pointer is at ([x], [y]) on a stream view of [width] by [height]. */
internal fun position(
    x: Int,
    y: Int,
    width: Int = STREAM_WIDTH,
    height: Int = STREAM_HEIGHT,
) = Sent("sendMousePosition", listOf(x.toShort(), y.toShort(), width.toShort(), height.toShort()))

/** `sendMouseMove`: the pointer moves by ([dx], [dy]). */
internal fun moveBy(dx: Int, dy: Int) = Sent("sendMouseMove", listOf(dx.toShort(), dy.toShort()))

/** `sendMouseMoveAsMousePosition`: a move of ([dx], [dy]) on a view of [width] by [height]. */
internal fun moveByOnView(
    dx: Int,
    dy: Int,
    width: Int = STREAM_WIDTH,
    height: Int = STREAM_HEIGHT,
) =
    Sent(
        "sendMouseMoveAsMousePosition",
        listOf(dx.toShort(), dy.toShort(), width.toShort(), height.toShort()),
    )

/** `sendMouseButton` with the press event of the host button [button]. */
internal fun press(button: Byte) =
    Sent("sendMouseButton", listOf(MouseButtonPacket.PRESS_EVENT, button))

/** `sendMouseButton` with the release event of the host button [button]. */
internal fun release(button: Byte) =
    Sent("sendMouseButton", listOf(MouseButtonPacket.RELEASE_EVENT, button))

/** `sendMouseHighResScroll` of [amount]. */
internal fun scrolled(amount: Int) = Sent("sendMouseHighResScroll", listOf(amount.toShort()))

/** `sendMouseHighResHScroll` of [amount]. */
internal fun scrolledAcross(amount: Int) = Sent("sendMouseHighResHScroll", listOf(amount.toShort()))

/**
 * A pen event as it reached the native library, read from the arguments of the call: the
 * `LI_TOUCH_EVENT_*` type, the `LI_TOOL_TYPE_*` tool, the pen buttons, the normalized position, the
 * pressure or distance, the normalized contact area, the rotation in degrees and the tilt.
 */
internal class PenCall(private val args: List<Any?>) {
    val type: Int
        get() = (args[0] as Byte).toInt()

    val tool: Int
        get() = (args[1] as Byte).toInt()

    val buttons: Int
        get() = (args[2] as Byte).toInt()

    val x: Float
        get() = args[3] as Float

    val y: Float
        get() = args[4] as Float

    val pressure: Float
        get() = args[5] as Float

    val major: Float
        get() = args[6] as Float

    val minor: Float
        get() = args[7] as Float

    val rotation: Int
        get() = (args[8] as Short).toInt()

    val tilt: Int
        get() = (args[9] as Byte).toInt()
}

/** The pen events that reached the native library, in order. */
internal fun pens(): List<PenCall> =
    ShadowRecordingBridge.named("sendPenEvent").map { PenCall(it.args) }

/** The calls that are not pen events: what the activity does besides telling the host a pen. */
internal fun sentBesidesPens(): List<Sent> = sent().filterNot { it.name == "sendPenEvent" }

/**
 * The base of the tests of the motion path: the stream activity in the streaming state, with the
 * stream view where the rig puts it, and the helpers to reach its views.
 */
// Abstract so that Gradle does not run the base as a test of its own.
@Suppress("AbstractClassCanBeConcreteClass")
internal abstract class MotionTestBase : GameTestBase() {
    protected val streamView: View
        get() = rig.game.findViewById(R.id.surfaceView)

    protected val backgroundView: View
        get() = rig.game.findViewById(R.id.backgroundTouchView)

    /**
     * The activity streams, the pointer is grabbed and the stream view is [width] wide at [left].
     */
    protected fun streaming(left: Int = 0, width: Int = STREAM_WIDTH) {
        rig.streaming()
        streamView.layout(left, STREAM_TOP, left + width, STREAM_TOP + STREAM_HEIGHT)
    }

    /** The capture provider that the activity asked for the stream view. */
    protected fun captureProvider(): InputCaptureProvider = rig.field("inputCaptureProvider")

    /** What the activity registered on the stream view for a pointer that the system captured. */
    protected fun capturedPointerListener(): View.OnCapturedPointerListener {
        val info = ReflectionHelpers.getField<Any>(streamView, "mListenerInfo")
        return ReflectionHelpers.getField(info, "mOnCapturedPointerListener")
    }

    protected fun idle(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    /** Gives the controller handler a pad that the system knows by [deviceId]. */
    protected fun addGamepadContext(deviceId: Int, controllerNumber: Short) {
        rig.addGamepadContext(deviceId, controllerNumber)
    }
}

/**
 * Makes the controller handler of the activity know the pad with [deviceId], as it would after the
 * pad sent its first event. The handler makes a context from the real device, which reads the
 * vibrators, lights and sensors that Robolectric does not have; the context is made here instead,
 * with its controller number, so that no arrival is reported.
 */
internal fun GameRig.addGamepadContext(deviceId: Int, controllerNumber: Short) {
    val handler = field<ControllerHandler>("controllerHandler")
    val contextClass =
        Class.forName("com.limelight.binding.input.ControllerHandler\$InputDeviceContext")
    val context =
        contextClass
            .getDeclaredConstructor(ControllerHandler::class.java)
            .apply { isAccessible = true }
            .newInstance(handler)
    ReflectionHelpers.setField(context, "assignedControllerNumber", true)
    ReflectionHelpers.setField(context, "controllerNumber", controllerNumber)
    val contexts = ReflectionHelpers.getField<SparseArray<Any?>>(handler, "inputDeviceContexts")
    contexts.put(deviceId, context)
}

/** A mouse event of the source mouse, with the pointer at ([x], [y]) and these buttons down. */
internal fun mouseEvent(action: Int, x: Float, y: Float, buttons: Int = 0): MotionEvent =
    motionEventOf(InputDevice.SOURCE_MOUSE, action) {
        pointer(x, y)
        buttonState = buttons
    }

/**
 * A touch screen event with fingers at [points], in the order of the event; the action is for the
 * pointer [index]. [build] changes the rest: the time, the flags, the earlier samples.
 */
internal fun fingerEvent(
    action: Int,
    points: List<Pair<Float, Float>>,
    index: Int = 0,
    build: MotionSpec.() -> Unit = {},
): MotionEvent =
    motionEventOf(InputDevice.SOURCE_TOUCHSCREEN, action) {
        points.forEach { (x, y) -> pointer(x, y, MotionEvent.TOOL_TYPE_FINGER) }
        actionIndex = index
        build()
    }
