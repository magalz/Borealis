package io.github.magalz.borealis.input

import android.app.Activity
import android.os.SystemClock
import android.view.KeyEvent
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration
import org.robolectric.Robolectric

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
}

/**
 * A real [ControllerHandler] over a [RecordingConnection]. Events carry device id 0, so they reach
 * the handler's default context, which is the one the on-screen controller also uses.
 */
class ControllerHandlerRig {
    val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()
    val connection = RecordingConnection(activity)
    val prefs = PreferenceConfiguration().apply { mouseEmulation = true }
    val handler = ControllerHandler(activity, connection, { }, prefs)

    /** The clock the events are stamped with; the main looper's clock moves it. */
    var now: Long = SystemClock.uptimeMillis()
        private set

    private val downTimes = mutableMapOf<Int, Long>()

    /** Moves the clock and the main looper to [time], running what is due. */
    fun advanceTo(time: Long) {
        val delta = time - SystemClock.uptimeMillis()
        if (delta > 0) org.robolectric.shadows.ShadowLooper.idleMainLooper(delta, java.util.concurrent.TimeUnit.MILLISECONDS)
        now = time
    }

    fun advanceBy(millis: Long) = advanceTo(now + millis)

    fun press(keyCode: Int, at: Long = now) {
        now = at
        downTimes[keyCode] = at
        handler.handleButtonDown(KeyEvent(at, at, KeyEvent.ACTION_DOWN, keyCode, 0, 0, 0, 0))
    }

    fun release(keyCode: Int, at: Long = now) {
        now = at
        handler.handleButtonUp(KeyEvent(downTimes[keyCode] ?: at, at, KeyEvent.ACTION_UP, keyCode, 0, 0, 0, 0))
    }

    /** The last packet's buttons, or null when nothing was sent. */
    fun lastButtons(): Int? = connection.packets.lastOrNull()?.buttons
}
