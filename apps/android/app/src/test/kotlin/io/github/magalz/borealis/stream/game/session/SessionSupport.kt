package io.github.magalz.borealis.stream.game.session

import android.app.Activity
import android.app.GameManager
import android.app.GameState
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.SparseArray
import android.view.Surface
import com.limelight.binding.input.ControllerHandler
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import com.limelight.nvstream.NvConnection
import com.limelight.ui.StreamView
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.stream.game.GameRig
import java.lang.reflect.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowSurfaceView
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/** How long a test waits for a thread of the activity before it gives up. */
internal const val THREAD_WAIT_MS = 10_000L

/**
 * A connection that failed in a given way, as `NvConnection` reports it to `StreamFailures`. The
 * rig puts it in the place of the connection that the activity built, with [useConnection].
 */
internal class FailedConnection(
    rig: GameRig,
    private val cause: CauseKind = CauseKind.NONE,
    private val http: Int = 0,
    private val collision: Boolean = false,
    private val exhausted: Boolean = false,
) : NvConnection(rig.application, null, 0, "test", null, null, null) {
    override fun lastFailureCause(): CauseKind = cause

    override fun lastHttpCode(): Int = http

    override fun endedByResumeCollision(): Boolean = collision

    override fun launchRetriesExhausted(): Boolean = exhausted
}

/** Puts [connection] where the activity keeps its connection. */
internal fun GameRig.useConnection(connection: NvConnection) {
    ReflectionHelpers.setField(game, "conn", connection)
}

/**
 * Runs [block] on a thread of its own and waits for it, the way the native library and the
 * connection thread call the listener of the activity: the callback comes from outside the main
 * thread, so what it does to the activity waits for the main looper.
 */
internal fun onConnectionThread(block: () -> Unit) {
    var failure: Throwable? = null
    val thread = Thread {
        try {
            block()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            failure = e
        }
    }
    thread.start()
    thread.join(THREAD_WAIT_MS)
    check(!thread.isAlive) { "the callback did not return" }
    failure?.let { throw it }
}

/** Waits for the threads whose name is [name] to end. */
internal fun joinThreadsNamed(name: String) {
    val threads = Thread.getAllStackTraces().keys.filter { it.name == name }
    for (thread in threads) {
        thread.join(THREAD_WAIT_MS)
        check(!thread.isAlive) { "the thread $name did not end" }
    }
}

/** One call of `GameManager.setGameState`, as the game service of the system received it. */
internal data class GameModeCall(val loading: Boolean, val mode: Int)

/**
 * Stands in for the game service of the system, which `UiHelper` tells whether the stream runs: a
 * unit test has none. [install] gives the activity a `GameManager` that records what it is told.
 */
internal class GameModeRecorder {
    val calls = CopyOnWriteArrayList<GameModeCall>()

    fun install(activity: Activity) {
        val contract = Class.forName("android.app.IGameManagerService")
        val service =
            Proxy.newProxyInstance(contract.classLoader, arrayOf(contract)) { _, method, args ->
                if (method.name == "setGameState") {
                    val state = args[1] as GameState
                    calls += GameModeCall(state.isLoading, state.mode)
                }
                null
            }
        // The constructor of the framework asks the service manager for the service; the one that
        // Robolectric adds to the instrumented class does nothing.
        val manager = GameManager::class.java.getDeclaredConstructor().newInstance()
        ReflectionHelpers.setField(manager, "mContext", activity)
        ReflectionHelpers.setField(manager, "mService", service)
        Shadow.extract<ShadowContextImpl>(activity.baseContext)
            .setSystemService(Context.GAME_SERVICE, manager)
    }
}

/** The game modes that the stream tells the system about, by the `GameState` constants. */
internal object GameModes {
    val INTERRUPTIBLE = GameModeCall(false, GameState.MODE_GAMEPLAY_INTERRUPTIBLE)
    val UNINTERRUPTIBLE = GameModeCall(false, GameState.MODE_GAMEPLAY_UNINTERRUPTIBLE)
    val ENDED = GameModeCall(false, GameState.MODE_NONE)
}

/** A gamepad on the USB driver of the app that records what the host asks it to do. */
internal class RecordingPad(id: Int, listener: UsbDriverListener) :
    AbstractController(id, listener, VENDOR_MICROSOFT, PRODUCT_XBOX_360) {
    val rumbles = CopyOnWriteArrayList<Pair<Short, Short>>()
    val triggerRumbles = CopyOnWriteArrayList<Pair<Short, Short>>()

    override fun start() = true

    override fun stop() = Unit

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        rumbles += lowFreqMotor to highFreqMotor
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        triggerRumbles += leftTrigger to rightTrigger
    }

    private companion object {
        const val VENDOR_MICROSOFT = 0x045e
        const val PRODUCT_XBOX_360 = 0x028e
    }
}

/**
 * A gamepad that Android knows, as far as the controller handler needs it for the feedback that the
 * host asks for: its number, and the report rates and the LED of the context that the handler keeps
 * for it. The handler builds such a context when the input manager reports a device, which a unit
 * test cannot make. [ledApplied] counts down when the handler runs the change of the LED.
 */
internal class KnownPad(val handler: ControllerHandler, controllerNumber: Short) {
    private val context: Any

    val ledApplied = CountDownLatch(1)

    /** Counts down when the handler turns the sensors of the pad on again. */
    val sensorsEnabled = CountDownLatch(1)

    init {
        val contextClass =
            Class.forName("com.limelight.binding.input.ControllerHandler\$InputDeviceContext")
        context =
            ReflectionHelpers.callConstructor(
                contextClass,
                ClassParameter.from(ControllerHandler::class.java, handler),
            )
        ReflectionHelpers.setField(context, "controllerNumber", controllerNumber)
        ReflectionHelpers.setField(context, "hasRgbLed", true)
        // The change of the LED asks the input device for its lights, which a unit test has not.
        ReflectionHelpers.setField(
            context,
            "setLedStateRunnable",
            Runnable { ledApplied.countDown() },
        )
        // Turning the sensors on again asks the sensor manager of the pad, which a unit test has
        // not.
        ReflectionHelpers.setField(
            context,
            "enableSensorRunnable",
            Runnable { sensorsEnabled.countDown() },
        )
        ReflectionHelpers.getField<SparseArray<Any>>(handler, "inputDeviceContexts")
            .put(CONTEXT_ID, context)
    }

    val accelReportRateHz: Short
        get() = ReflectionHelpers.getField(context, "accelReportRateHz")

    val gyroReportRateHz: Short
        get() = ReflectionHelpers.getField(context, "gyroReportRateHz")

    val ledArgbValue: Int
        get() = ReflectionHelpers.getField(context, "ledArgbValue")

    fun awaitLed(): Boolean = ledApplied.await(THREAD_WAIT_MS, TimeUnit.MILLISECONDS)

    fun awaitSensors(): Boolean = sensorsEnabled.await(THREAD_WAIT_MS, TimeUnit.MILLISECONDS)

    /** The pad has a gyroscope that the host asked for, and the handler listens to it. */
    fun listenToGyroscope(sensors: SensorManager) {
        ReflectionHelpers.setField(context, "sensorManager", sensors)
        ReflectionHelpers.setField(
            context,
            "gyroListener",
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) = Unit

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            },
        )
    }

    val listensToGyroscope: Boolean
        get() = ReflectionHelpers.getField<Any?>(context, "gyroListener") != null

    private companion object {
        const val CONTEXT_ID = 1
    }
}

/** Gives the stream view of the activity a surface that [surface] stands for. */
internal fun useSurface(view: StreamView, surface: Surface) {
    ReflectionHelpers.setField(shadowOf(view), "fakeSurfaceHolder", ValidSurfaceHolder(surface))
}

/** The holder that a surface view has in a unit test has no surface; this one has. */
internal class ValidSurfaceHolder(private val surface: Surface) :
    ShadowSurfaceView.FakeSurfaceHolder() {
    override fun getSurface(): Surface = surface
}
