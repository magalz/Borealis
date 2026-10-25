package io.github.magalz.borealis.input.feedback

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.Vibrator
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.InputDeviceBuilder
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import org.robolectric.shadows.ShadowContextImpl
import org.robolectric.shadows.ShadowInputEvent

/** One controller packet, with the signed bytes and shorts the handler handed the connection. */
data class InputPacket(
    val controller: Int,
    val activeMask: Int,
    val buttons: Int,
    val leftTrigger: Int,
    val rightTrigger: Int,
    val leftStickX: Int,
    val leftStickY: Int,
    val rightStickX: Int,
    val rightStickY: Int,
)

/** One arrival report of a controller, as the connection received it. */
data class Arrival(
    val controller: Int,
    val activeMask: Int,
    val type: Int,
    val supportedButtons: Int,
    val capabilities: Int,
)

/** One motion report: the sensor type and the three values, as the connection received them. */
data class Motion(
    val controller: Int,
    val type: Int,
    val x: Float,
    val y: Float,
    val z: Float,
)

/** Records what the handler reports to the PC; the real connection would hand it to the native library. */
class FeedbackConnection(activity: Activity) :
    NvConnection(activity, null, 0, "test", null, null, null) {
    val packets = CopyOnWriteArrayList<InputPacket>()
    val arrivals = CopyOnWriteArrayList<Arrival>()
    val motions = CopyOnWriteArrayList<Motion>()

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
            InputPacket(
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

    override fun sendControllerArrivalEvent(
        controllerNumber: Byte,
        activeGamepadMask: Short,
        type: Byte,
        supportedButtonFlags: Int,
        capabilities: Short,
    ): Int {
        arrivals +=
            Arrival(
                controllerNumber.toInt(),
                activeGamepadMask.toInt(),
                type.toInt(),
                supportedButtonFlags,
                capabilities.toInt(),
            )
        return 0
    }

    override fun sendControllerMotionEvent(
        controllerNumber: Byte,
        motionType: Byte,
        x: Float,
        y: Float,
        z: Float,
    ): Int {
        motions += Motion(controllerNumber.toInt(), motionType.toInt(), x, y, z)
        return 0
    }
}

/** A pad the rig connected: its Android device and the hardware the test gave it. */
class Pad(val device: InputDevice, val features: DeviceFeatures) {
    val id: Int
        get() = device.id
}

/**
 * A real [ControllerHandler] over a [FeedbackConnection], with the hardware that gives feedback
 * made by the test: the phone's vibrator and sensors, and the vibrators, sensors, lights and
 * battery of each pad. The phone's hardware is installed as the activity's system services, so the
 * handler finds it the way it does on a device.
 *
 * The shadows of [ShadowFeedbackInputDevice] and [ShadowFeedbackBridge] must be set on the test.
 * [close] stops the handler's own thread; call it after each test.
 */
class FeedbackRig(
    /** The phone's vibrator: Android 12 and later reach it through [phoneVibratorManager]. */
    val phoneVibrator: Vibrator = Hardware.vibrator(),
    /** A manager made by [Hardware.vibratorManager]; the default has no vibrators of its own. */
    phoneVibratorManager: Any? = null,
    val phoneSensors: SensorManager =
        Hardware.sensorManager(Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_GYROSCOPE),
    configure: PreferenceConfiguration.() -> Unit = {},
) {
    val activity: Activity

    init {
        ShadowFeedbackBridge.reset()
        ShadowSceManager.reset()
        ShadowFeedbackInputDevice.reset()
        // The SHIELD extension service does not exist here; without this the bind answers with no
        // binder.
        shadowOf(RuntimeEnvironment.getApplication())
            .declareComponentUnbindable(
                ComponentName("com.nvidia.blakepairing", "com.nvidia.blakepairing.AccessoryService")
            )
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        val services = Shadow.extract<ShadowContextImpl>(activity.baseContext)
        services.setSystemService(Context.SENSOR_SERVICE, phoneSensors)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            services.setSystemService(
                Context.VIBRATOR_MANAGER_SERVICE,
                phoneVibratorManager ?: Hardware.vibratorManager(defaultVibrator = phoneVibrator),
            )
        } else {
            services.setSystemService(Context.VIBRATOR_SERVICE, phoneVibrator)
        }
    }

    val connection = FeedbackConnection(activity)
    val prefs = PreferenceConfiguration().apply(configure)
    val handler = ControllerHandler(activity, connection, {}, prefs)

    private var destroyed = false

    private val downTimes = mutableMapOf<Int, Long>()

    /** `handleRumble` with the values as ints, so that a value past the sign bit reads as it is written. */
    fun rumble(low: Int, high: Int, controller: Int = 0) =
        handler.handleRumble(controller.toShort(), low.toShort(), high.toShort())

    fun rumbleTriggers(left: Int, right: Int, controller: Int = 0) =
        handler.handleRumbleTriggers(controller.toShort(), left.toShort(), right.toShort())

    fun motionState(type: Int, rateHz: Int, controller: Int = 0) =
        handler.handleSetMotionEventState(controller.toShort(), type.toByte(), rateHz.toShort())

    fun led(red: Int, green: Int, blue: Int, controller: Int = 0) =
        handler.handleSetControllerLED(controller.toShort(), red.toByte(), green.toByte(), blue.toByte())

    /** Robolectric's looper clock, which [advanceBackgroundBy] moves. */
    val now: Long
        get() = SystemClock.uptimeMillis()

    /** The looper of the handler's own `HandlerThread`, which batteries, sensors and LEDs run on. */
    val backgroundLooper: Looper
        get() = backgroundHandler().looper

    private fun backgroundHandler(): Handler =
        ControllerHandler::class
            .java
            .getDeclaredField("backgroundThreadHandler")
            .apply { isAccessible = true }
            .get(handler) as Handler

    /**
     * Waits until the handler's thread has run everything that was due when this was called. The
     * thread is a real one, so a test must wait for it instead of assuming it already ran.
     */
    fun awaitBackground() {
        val ran = CountDownLatch(1)
        Handler(backgroundLooper).post { ran.countDown() }
        check(ran.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "The handler's thread did not run" }
    }

    /**
     * Keeps the handler's thread busy until the returned function is called, so that what the
     * handler posts meanwhile queues up behind it, as it does when the thread is slow.
     */
    fun holdBackground(): () -> Unit {
        val release = CountDownLatch(1)
        Handler(backgroundLooper).post {
            check(release.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "The thread was never released" }
        }
        return { release.countDown() }
    }

    /** Moves the handler thread's clock by [millis] and waits for the work that became due. */
    fun advanceBackgroundBy(millis: Long) {
        shadowOf(backgroundLooper).idleFor(Duration.ofMillis(millis))
    }

    /** Connects an Android game pad, with [features] as its hardware, and returns it. */
    fun connectPad(
        id: Int = PAD_ID,
        vendorId: Int = XBOX_VENDOR,
        productId: Int = 0x028e,
        external: Boolean = true,
        features: DeviceFeatures = DeviceFeatures(),
    ): Pad {
        val device = newPadDevice(id, vendorId, productId, external)
        ShadowFeedbackInputDevice.features[id] = features.withAbsentHardware()
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java))
            .addInputDevice(device)
        return Pad(device, features)
    }

    /**
     * Gives [pad] its context in the handler, and its controller number, with a first button
     * press: that is what a real pad does the first time it is used.
     */
    fun wake(pad: Pad) {
        press(pad, KeyEvent.KEYCODE_BUTTON_A)
        // The arrival report posts the battery poll: let it run now, at this time of the clock
        awaitBackground()
        // The clock does not move here, so what the handler's thread schedules is due at a known time;
        // the handler sleeps 25 ms of real time before it lets go of a button that was down for less
        release(pad, KeyEvent.KEYCODE_BUTTON_A)
    }

    fun press(pad: Pad, keyCode: Int, at: Long = now) {
        downTimes[keyCode] = at
        handler.handleButtonDown(keyEvent(pad, at, at, KeyEvent.ACTION_DOWN, keyCode))
    }

    fun release(pad: Pad, keyCode: Int, at: Long = now) {
        val downTime = downTimes[keyCode] ?: at
        handler.handleButtonUp(keyEvent(pad, downTime, at, KeyEvent.ACTION_UP, keyCode))
    }

    private fun keyEvent(pad: Pad, downTime: Long, eventTime: Long, action: Int, keyCode: Int) =
        KeyEvent(downTime, eventTime, action, keyCode, 0, 0, pad.id, 0).also { event ->
            Shadow.extract<ShadowInputEvent>(event).setDevice(pad.device)
        }

    /** An input device with the joystick axes X and Y. */
    private fun newPadDevice(id: Int, vendorId: Int, productId: Int, external: Boolean): InputDevice =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            builtPadDevice(id, vendorId, productId, external)
        } else {
            fieldPadDevice(id, vendorId, productId, external)
        }

    private fun builtPadDevice(id: Int, vendorId: Int, productId: Int, external: Boolean): InputDevice {
        val builder =
            InputDeviceBuilder.newBuilder()
                .setId(id)
                .setName("pad-$id")
                .setDescriptor("descriptor-$id")
                .setVendorId(vendorId)
                .setProductId(productId)
                .setExternal(external)
                .setSources(InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
            builder.addMotionRange(axis, InputDevice.SOURCE_JOYSTICK, -1f, 1f, 0f, 0f, 0f)
        }
        return builder.build()
    }

    /** Made field by field before Android 14, which `InputDeviceBuilder` needs (`InputDevice.Builder`). */
    private fun fieldPadDevice(id: Int, vendorId: Int, productId: Int, external: Boolean): InputDevice {
        val device = Shadow.newInstanceOf(InputDevice::class.java)
        ReflectionHelpers.setField(device, "mId", id)
        ReflectionHelpers.setField(device, "mName", "pad-$id")
        ReflectionHelpers.setField(device, "mDescriptor", "descriptor-$id")
        ReflectionHelpers.setField(device, "mVendorId", vendorId)
        ReflectionHelpers.setField(device, "mProductId", productId)
        ReflectionHelpers.setField(device, "mIsExternal", external)
        ReflectionHelpers.setField(device, "mSources", InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK)
        ReflectionHelpers.setField(device, "mMotionRanges", ArrayList<Any>())
        for (axis in listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y)) {
            ReflectionHelpers.callInstanceMethod<Any>(
                device,
                "addMotionRange",
                ClassParameter.from(Int::class.javaPrimitiveType, axis),
                ClassParameter.from(Int::class.javaPrimitiveType, InputDevice.SOURCE_JOYSTICK),
                ClassParameter.from(Float::class.javaPrimitiveType, -1f),
                ClassParameter.from(Float::class.javaPrimitiveType, 1f),
                ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                ClassParameter.from(Float::class.javaPrimitiveType, 0f),
            )
        }
        return device
    }

    /** The handler's own thread. */
    val backgroundThread: Thread
        get() = backgroundLooper.thread

    /** Stops the handler's thread and waits for it, so that no work of it reaches the next test. */
    fun close() {
        if (!destroyed) {
            destroy()
        }
    }

    /** Destroys the handler, as the stream does at its end; [close] then has nothing left to do. */
    fun destroy() {
        destroyed = true
        val thread = backgroundThread
        handler.destroy()
        thread.join(WAIT_SECONDS * 1000)
    }

    companion object {
        const val PAD_ID = 5
        const val XBOX_VENDOR = 0x045e
        const val SONY_VENDOR = 0x054c
        const val NINTENDO_VENDOR = 0x057e
        private const val WAIT_SECONDS = 10L
    }
}
