package io.github.magalz.borealis.input

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration
import java.util.concurrent.TimeUnit
import android.content.ComponentName
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
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
 * the handler's default context, which is the one the on-screen controller also uses.
 *
 * Events are stamped with the main looper's clock, which [advanceBy] moves, so the handler's
 * scheduled work runs when the test says time has passed.
 */
class ControllerHandlerRig(multiController: Boolean = false) {
    // The SHIELD extension service does not exist here; without this the bind answers with no binder.
    private val noShieldService =
        shadowOf(RuntimeEnvironment.getApplication())
            .declareComponentUnbindable(
                ComponentName("com.nvidia.blakepairing", "com.nvidia.blakepairing.AccessoryService")
            )

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
        handler.handleButtonDown(KeyEvent(at, at, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, 0))
    }

    fun release(keyCode: Int, at: Long = now) {
        val downTime = downTimes[keyCode] ?: at
        handler.handleButtonUp(KeyEvent(downTime, at, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0))
    }

    /** The buttons of every packet sent so far. */
    fun buttons(): List<Int> = connection.packets.map { it.buttons }

    /** The last packet's buttons, or null when nothing was sent. */
    fun lastButtons(): Int? = connection.packets.lastOrNull()?.buttons

    /** The handler's default context (on-screen controller and device 0), for state a test cannot reach. */
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

    override fun stop() {}

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {}

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {}

    fun report(buttons: Int, leftStickX: Float = 0f, leftStickY: Float = 0f) {
        buttonFlags = buttons
        this.leftStickX = leftStickX
        this.leftStickY = leftStickY
        reportInput()
    }
}
