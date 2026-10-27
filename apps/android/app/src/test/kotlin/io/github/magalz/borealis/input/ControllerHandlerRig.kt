package io.github.magalz.borealis.input

import android.app.Activity
import android.content.ComponentName
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
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

/** One controller packet as the handler handed it to the connection. */
data class Packet(
    val controller: Int,
    val buttons: Int,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val leftStickX: Int = 0,
    val leftStickY: Int = 0,
    val rightStickX: Int = 0,
    val rightStickY: Int = 0,
)

/** Records what reaches the PC; the real connection would hand it to the native library. */
class RecordingConnection(activity: Activity) :
    NvConnection(activity, null, 0, "test", null, null, null) {
    val packets = mutableListOf<Packet>()
    val mouse = mutableListOf<String>()

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
            Packet(
                controllerNumber.toInt(),
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

    val motion = mutableListOf<String>()
    val touches = mutableListOf<String>()

    override fun sendControllerMotionEvent(
        controllerNumber: Byte,
        motionType: Byte,
        x: Float,
        y: Float,
        z: Float,
    ): Int {
        motion += "motion $controllerNumber $motionType $x $y $z"
        return 0
    }

    override fun sendControllerTouchEvent(
        controllerNumber: Byte,
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
    ): Int {
        touches += "touch $controllerNumber $eventType"
        return 0
    }
}

/**
 * A real [ControllerHandler] over a [RecordingConnection]. Events carry device id 0, so they reach
 * the handler's default context, which is the one the on-screen controller also uses, until
 * [useSystemPad] connects a system pad for them.
 *
 * Events are stamped with the main looper's clock, which [advanceBy] moves, so the handler's
 * scheduled work runs when the test says time has passed.
 */
class ControllerHandlerRig(multiController: Boolean = false) {
    init {
        // The SHIELD extension service does not exist here; without this the bind answers with no
        // binder.
        shadowOf(RuntimeEnvironment.getApplication())
            .declareComponentUnbindable(
                ComponentName("com.nvidia.blakepairing", "com.nvidia.blakepairing.AccessoryService")
            )
    }

    val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()
    val connection = RecordingConnection(activity)
    val prefs =
        PreferenceConfiguration().apply {
            mouseEmulation = true
            this.multiController = multiController
        }
    val handler = ControllerHandler(activity, connection, {}, prefs)

    /** How many times the menu chord was reported. */
    var chords = 0
        private set

    private val downTimes = mutableMapOf<Int, Long>()

    /**
     * The device id of the events [press] and [release] send: 0, the default context, until
     * [useSystemPad].
     */
    var padDeviceId = 0
        private set

    private var padDevice: InputDevice? = null

    init {
        // Robolectric starts the clock at zero, where a lone Start looks like a Start+RB combo.
        advanceBy(10_000)
        handler.setMenuChordListener { chords++ }
    }

    val now: Long
        get() = SystemClock.uptimeMillis()

    /** Moves the clock and runs what the main looper has scheduled in that time. */
    fun advanceBy(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    fun press(keyCode: Int, at: Long = now) {
        downTimes[keyCode] = at
        handler.handleButtonDown(keyEvent(at, at, KeyEvent.ACTION_DOWN, keyCode))
    }

    /**
     * A key event from the pad of [press] and [release]; Robolectric needs its device handed over.
     */
    fun keyEvent(downTime: Long, eventTime: Long, action: Int, keyCode: Int, repeat: Int = 0) =
        KeyEvent(downTime, eventTime, action, keyCode, repeat, 0, padDeviceId, 0).also { event ->
            padDevice?.let { Shadow.extract<ShadowInputEvent>(event).setDevice(it) }
        }

    fun release(keyCode: Int, at: Long = now) {
        val downTime = downTimes[keyCode] ?: at
        handler.handleButtonUp(keyEvent(downTime, at, KeyEvent.ACTION_UP, keyCode))
    }

    /** The buttons of every packet sent so far. */
    fun buttons(): List<Int> = connection.packets.map { it.buttons }

    /** The last packet's buttons, or null when nothing was sent. */
    fun lastButtons(): Int? = connection.packets.lastOrNull()?.buttons

    /**
     * Connects a system pad, an Android input device with a context of its own in the handler, and
     * sends the events of [press] and [release] to it from now on. The context is made here because
     * creating one from a device reads native code that unit tests do not have.
     */
    fun useSystemPad(hasSelect: Boolean = true, deviceId: Int = 7) {
        val device = addSystemInputDevice("pad", deviceId)
        // As the default context has it: a controller number, so that no native arrival report is
        // made
        handler.addInputDeviceContext(
            deviceId,
            mapOf("name" to "pad", "hasSelect" to hasSelect, "assignedControllerNumber" to true),
        )

        padDeviceId = deviceId
        padDevice = device
    }

    /**
     * Holds the sticks of the system pad of [useSystemPad] where they are, in the handler's own
     * range (up and right are positive), as the axis events of a real pad would leave them.
     */
    fun holdSystemPadSticks(leftX: Int = 0, leftY: Int = 0, rightX: Int = 0, rightY: Int = 0) {
        val context = inputDeviceContexts().get(padDeviceId)
        fun set(name: String, value: Int) =
            generateSequence(context.javaClass as Class<*>) { it.superclass }
                .first { owner -> owner.declaredFields.any { it.name == name } }
                .getDeclaredField(name)
                .apply { isAccessible = true }
                .setShort(context, value.toShort())
        set("leftStickX", leftX)
        set("leftStickY", leftY)
        set("rightStickX", rightX)
        set("rightStickY", rightY)
    }

    /** The handler's contexts by device id, for the ones a test adds or reads. */
    @Suppress("UNCHECKED_CAST") // The field holds the handler's contexts by construction.
    private fun inputDeviceContexts(): android.util.SparseArray<Any> =
        ControllerHandler::class
            .java
            .getDeclaredField("inputDeviceContexts")
            .apply { isAccessible = true }
            .get(handler) as android.util.SparseArray<Any>

    /**
     * Makes the pad behind device 0 one that Android reports a Select button for, as a real system
     * pad is.
     */
    fun givePadASelectButton() = setDefaultContextField("hasSelect", true)

    /**
     * The handler's default context (on-screen controller and device 0), for state a test cannot
     * reach.
     */
    fun setDefaultContextField(name: String, value: Any?) {
        val handlerField = ControllerHandler::class.java.getDeclaredField("defaultContext")
        handlerField.isAccessible = true
        val context = handlerField.get(handler)
        val field = context.javaClass.getDeclaredField(name)
        field.isAccessible = true
        field.set(context, value)
    }
}

/** A USB-driver pad: it reports its whole button state each time, as the driver service does. */
class FakeUsbController(id: Int, listener: UsbDriverListener) :
    AbstractController(id, listener, 0x045e, 0x028e) {
    override fun start() = true

    override fun stop() {
        // Nothing is held open.
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        // The fake has no motors.
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        // The fake has no motors.
    }

    fun report(
        buttons: Int,
        leftStickX: Float = 0f,
        leftStickY: Float = 0f,
        rightStickX: Float = 0f,
        rightStickY: Float = 0f,
    ) {
        buttonFlags = buttons
        this.leftStickX = leftStickX
        this.leftStickY = leftStickY
        this.rightStickX = rightStickX
        this.rightStickY = rightStickY
        reportInput()
    }
}
