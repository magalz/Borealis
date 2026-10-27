package io.github.magalz.borealis.input.events

import android.app.Activity
import android.content.ComponentName
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.testing.addInputDeviceContext
import io.github.magalz.borealis.testing.addSystemInputDevice
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowInputEvent
import org.robolectric.shadows.ShadowLooper

/** One controller packet as the handler handed it to the connection, with the gamepad mask. */
data class Sent(
    val controller: Int,
    val activeMask: Int,
    val buttons: Int,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val leftStickX: Int = 0,
    val leftStickY: Int = 0,
    val rightStickX: Int = 0,
    val rightStickY: Int = 0,
)

/** One controller touch event as the handler handed it to the connection. */
data class Touch(
    val controller: Int,
    val type: Int,
    val pointerId: Int,
    val x: Float,
    val y: Float,
    val pressure: Float,
)

/**
 * Records what reaches the PC, with the fields the shared rig leaves out: the gamepad mask of every
 * packet and the whole touch event. [touchResult] is what the touch call answers; the real
 * connection answers [UNSUPPORTED] when the host has no controller touch.
 */
class EventsConnection(activity: Activity) :
    NvConnection(activity, null, 0, "test", null, null, null) {
    val packets = mutableListOf<Sent>()
    val mouse = mutableListOf<String>()
    val touches = mutableListOf<Touch>()
    var touchResult = 0

    override fun sendControllerInput(
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
        packets +=
            Sent(
                controllerNumber.toInt(),
                activeGamepadMask.toInt(),
                buttonFlags,
                leftTrigger.toInt(),
                rightTrigger.toInt(),
                leftStickX.toInt(),
                leftStickY.toInt(),
                rightStickX.toInt(),
                rightStickY.toInt(),
            )
    }

    override fun sendMouseMove(deltaX: Short, deltaY: Short) {
        mouse += "move $deltaX $deltaY"
    }

    override fun sendMouseButtonDown(mouseButton: Byte) {
        mouse += "down $mouseButton"
    }

    override fun sendMouseButtonUp(mouseButton: Byte) {
        mouse += "up $mouseButton"
    }

    override fun sendMouseScroll(scrollClicks: Byte) {
        mouse += "scroll $scrollClicks"
    }

    override fun sendMouseHScroll(scrollClicks: Byte) {
        mouse += "hscroll $scrollClicks"
    }

    override fun sendMouseHighResScroll(scrollAmount: Short) {
        mouse += "hires scroll $scrollAmount"
    }

    override fun sendMouseHighResHScroll(scrollAmount: Short) {
        mouse += "hires hscroll $scrollAmount"
    }

    override fun sendControllerArrivalEvent(
        controllerNumber: Byte,
        activeGamepadMask: Short,
        type: Byte,
        supportedButtonFlags: Int,
        capabilities: Short,
    ): Int = 0

    override fun sendControllerTouchEvent(
        controllerNumber: Byte,
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
    ): Int {
        touches += Touch(controllerNumber.toInt(), eventType.toInt(), pointerId, x, y, pressure)
        return touchResult
    }

    companion object {
        /** `MoonBridge.LI_ERR_UNSUPPORTED`. */
        const val UNSUPPORTED = -5501
    }
}

/**
 * A real [ControllerHandler] over an [EventsConnection]. Events reach the contexts the handler
 * holds: the default context for device id 0, and the pads a test adds with [addPad].
 *
 * Events are stamped with the main looper's clock, which [advanceBy] moves, so the handler's
 * scheduled work runs when the test says time has passed.
 *
 * The contexts are reached by reflection, as the shared rig does: the handler fields
 * `defaultContext` and `inputDeviceContexts`, and the fields of the contexts by name.
 */
class EventsRig(
    multiController: Boolean = false,
    configure: PreferenceConfiguration.() -> Unit = {},
) {
    init {
        // The SHIELD extension service does not exist here; without this the bind answers with no
        // binder.
        shadowOf(RuntimeEnvironment.getApplication())
            .declareComponentUnbindable(
                ComponentName("com.nvidia.blakepairing", "com.nvidia.blakepairing.AccessoryService")
            )
    }

    val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()
    val connection = EventsConnection(activity)
    val prefs =
        PreferenceConfiguration().apply {
            mouseEmulation = true
            this.multiController = multiController
            configure()
        }
    val handler = ControllerHandler(activity, connection, {}, prefs)

    /** How many times the menu chord was reported. */
    var chords = 0
        private set

    init {
        // Robolectric starts the clock at zero, where a lone Start looks like a Start+RB combo.
        advanceBy(10_000)
        handler.setMenuChordListener { chords++ }
    }

    /** The events of device id 0, which the handler gives its default context. */
    val default = Pad(this, 0, null, handlerField("defaultContext"))

    val now: Long
        get() = SystemClock.uptimeMillis()

    /** Moves the clock and runs what the main looper has scheduled in that time. */
    fun advanceBy(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    /** The packets sent so far. */
    val packets: List<Sent>
        get() = connection.packets

    /** The buttons of every packet sent so far. */
    fun buttons(): List<Int> = packets.map { it.buttons }

    /** A field of the handler itself. */
    fun handlerField(name: String): Any? =
        ControllerHandler::class
            .java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .get(handler)

    fun setHandlerField(name: String, value: Any?) {
        ControllerHandler::class
            .java
            .getDeclaredField(name)
            .apply { isAccessible = true }
            .set(handler, value)
    }

    /**
     * Connects a system pad: an Android input device with a context of its own in the handler. The
     * context is made here because creating one from a device reads native code that unit tests do
     * not have. It has a controller number, so no arrival is reported. [fields] are set on it by
     * name before it goes in.
     */
    fun addPad(
        deviceId: Int,
        controllerNumber: Int = 0,
        fields: Map<String, Any?> = emptyMap(),
    ): Pad {
        val device = addSystemInputDevice("pad $deviceId", deviceId)
        val context =
            handler.addInputDeviceContext(
                deviceId,
                mapOf(
                    "name" to "pad $deviceId",
                    "assignedControllerNumber" to true,
                    "controllerNumber" to controllerNumber.toShort(),
                ) + fields,
            )
        val pad = Pad(this, deviceId, device, context)
        return pad
    }
}

/**
 * The events of one device id and the context behind them. [context] is the handler's context for
 * the pad; its fields are read and written by name.
 */
class Pad(
    private val rig: EventsRig,
    val deviceId: Int,
    private val device: InputDevice?,
    val context: Any?,
) {
    private fun fieldOwner(name: String): Class<*> =
        generateSequence(context!!.javaClass as Class<*>) { it.superclass }
            .first { owner -> owner.declaredFields.any { it.name == name } }

    private fun field(name: String) =
        fieldOwner(name).getDeclaredField(name).apply { isAccessible = true }

    /** Sets a field of the context by name; the value must have the field's own type. */
    fun set(name: String, value: Any?) = field(name).set(context, value)

    fun get(name: String): Any? = field(name).get(context)

    fun getInt(name: String): Int = field(name).getInt(context)

    fun getShort(name: String): Int = field(name).getShort(context).toInt()

    fun getByte(name: String): Int = field(name).getByte(context).toInt()

    fun getBoolean(name: String): Boolean = field(name).getBoolean(context)

    /** The buttons the context holds. */
    val inputMap: Int
        get() = getInt("inputMap")

    /** A key event of this device; Robolectric needs its device handed over. */
    fun keyEvent(
        downTime: Long,
        eventTime: Long,
        action: Int,
        keyCode: Int,
        detail: KeyDetail = KeyDetail(),
    ): KeyEvent =
        KeyEvent(
                downTime,
                eventTime,
                action,
                keyCode,
                detail.repeat,
                detail.metaState,
                deviceId,
                detail.scanCode,
                detail.flags,
            )
            .also { event -> device?.let { Shadow.extract<ShadowInputEvent>(event).setDevice(it) } }

    private val downTimes = mutableMapOf<Int, Long>()

    /** Presses a key and answers what the handler answered. */
    fun press(keyCode: Int, detail: KeyDetail = KeyDetail(), at: Long = rig.now): Boolean {
        downTimes[keyCode] = at
        return rig.handler.handleButtonDown(keyEvent(at, at, KeyEvent.ACTION_DOWN, keyCode, detail))
    }

    /**
     * Releases a key, held since [press] unless [downTime] says otherwise. A release never repeats:
     * the repeat count of [detail] is not used.
     */
    fun release(
        keyCode: Int,
        detail: KeyDetail = KeyDetail(),
        at: Long = rig.now,
        downTime: Long = downTimes[keyCode] ?: at,
    ): Boolean =
        rig.handler.handleButtonUp(
            keyEvent(downTime, at, KeyEvent.ACTION_UP, keyCode, detail.copy(repeat = 0))
        )

    /**
     * Holds a key down for [heldMs] of the main looper's clock, then lets it go. The hold is long
     * enough that the handler does not wait for a short press.
     */
    fun tap(keyCode: Int, scanCode: Int = 0, heldMs: Long = 100): Boolean {
        press(keyCode, KeyDetail(scanCode = scanCode))
        rig.advanceBy(heldMs)
        return release(keyCode, KeyDetail(scanCode = scanCode))
    }

    /**
     * Presses a key, waits 100 ms so that a lone Start or Select has reached the PC, lets the key
     * go and waits again so that the next key is not a bumper release away from this one. Answers
     * whether the handler took the press and the last packet it caused.
     */
    fun observe(keyCode: Int, scanCode: Int = 0, metaState: Int = 0, flags: Int = 0): KeyResult {
        val detail = KeyDetail(scanCode = scanCode, metaState = metaState, flags = flags)
        val before = rig.packets.size
        val handled = press(keyCode, detail)
        rig.advanceBy(100)
        val sent = rig.packets.drop(before).lastOrNull()
        release(keyCode, detail)
        rig.advanceBy(150)
        return KeyResult(handled, sent)
    }

    /** A joystick event with the given axis values, from a pad that reports them as a joystick. */
    fun motion(
        axes: Map<Int, Float>,
        source: Int = InputDevice.SOURCE_JOYSTICK,
        history: List<Map<Int, Float>> = emptyList(),
    ): Boolean {
        val event =
            motionEvent(
                source,
                MotionEvent.ACTION_MOVE,
                listOf(PointerState(0, axes)),
                history = history,
            )
        return rig.handler.handleMotionEvent(event)
    }

    /**
     * A touch event: [pointers] are in the event's order and [index] is the one the action is for.
     */
    fun touch(
        action: Int,
        pointers: List<PointerState>,
        index: Int = 0,
        source: Int = InputDevice.SOURCE_TOUCHPAD,
        extras: TouchExtras = TouchExtras(),
    ): Boolean {
        val event =
            motionEvent(
                source,
                action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),
                pointers,
                extras.flags,
            )
        // setActionButton is hidden API: the framework sets it for button press and release events.
        if (extras.actionButton != 0) {
            MotionEvent::class
                .java
                .getDeclaredMethod("setActionButton", Int::class.javaPrimitiveType)
                .apply { isAccessible = true }
                .invoke(event, extras.actionButton)
        }
        return rig.handler.tryHandleTouchpadEvent(event)
    }

    /**
     * A motion event of this device with the pointers given; the device is what `getDevice()`
     * answers.
     */
    private fun motionEvent(
        source: Int,
        action: Int,
        pointers: List<PointerState>,
        flags: Int = 0,
        history: List<Map<Int, Float>> = emptyList(),
    ): MotionEvent {
        val properties =
            pointers
                .map { pointer ->
                    MotionEvent.PointerProperties().apply {
                        id = pointer.id
                        toolType = MotionEvent.TOOL_TYPE_FINGER
                    }
                }
                .toTypedArray()
        val coords =
            pointers
                .map { pointer ->
                    MotionEvent.PointerCoords().apply {
                        pointer.axes.forEach { (axis, value) -> setAxisValue(axis, value) }
                    }
                }
                .toTypedArray()
        // The oldest sample is what the event is made with; the later ones are batched on it, so
        // the
        // last sample is the current value and the earlier ones are the history.
        val samples = history + listOf(pointers.first().axes)
        val event =
            MotionEvent.obtain(
                0L,
                0L,
                action,
                pointers.size,
                properties,
                if (history.isEmpty()) coords else arrayOf(coordsOf(samples.first())),
                0,
                0,
                1f,
                1f,
                deviceId,
                0,
                source,
                flags,
            )
        if (history.isNotEmpty()) {
            samples.drop(1).forEachIndexed { index, sample ->
                event.addBatch(index + 1L, arrayOf(coordsOf(sample)), 0)
            }
        }
        device?.let { Shadow.extract<ShadowInputEvent>(event).setDevice(it) }
        return event
    }
}

/**
 * What a key event carries besides its key code: the scan code, the repeat count, the meta state
 * and the flags.
 */
data class KeyDetail(
    val scanCode: Int = 0,
    val repeat: Int = 0,
    val metaState: Int = 0,
    val flags: Int = 0,
)

/** What a touch event carries besides its action and pointers: its flags and its action button. */
data class TouchExtras(val flags: Int = 0, val actionButton: Int = 0)

/** What a key press did: whether the handler took it, and the last packet it sent, if any. */
data class KeyResult(val handled: Boolean, val sent: Sent?)

/** One pointer of a motion event: its id and the axis values it carries. */
data class PointerState(val id: Int, val axes: Map<Int, Float>)

private fun coordsOf(axes: Map<Int, Float>) =
    MotionEvent.PointerCoords().apply {
        axes.forEach { (axis, value) -> setAxisValue(axis, value) }
    }

/** A motion range as the framework builds one from the kernel's axis description. */
fun motionRange(axis: Int, min: Float, max: Float, source: Int = InputDevice.SOURCE_TOUCHPAD) =
    InputDevice.MotionRange::class
        .java
        .declaredConstructors
        .first { it.parameterCount == 7 }
        .apply { isAccessible = true }
        .newInstance(axis, source, min, max, 0f, 0f, 0f) as InputDevice.MotionRange
