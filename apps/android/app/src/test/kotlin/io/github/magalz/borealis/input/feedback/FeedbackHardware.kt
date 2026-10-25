package io.github.magalz.borealis.input.feedback

import android.hardware.Sensor
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.ClassName
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.SensorBuilder
import org.robolectric.shadows.SensorEventBuilder

/*
 * The hardware that gives feedback: vibrators, sensors, LEDs and batteries. The framework makes
 * their classes abstract with constructors a test cannot call, and Robolectric's own shadows keep
 * one global state for all vibrators, so these shadows keep the state of each object instead. A test
 * makes an object with [Hardware], which asks Robolectric for an instance of the framework's own
 * class, and reads or sets its state through the shadow. Several of these types do not exist before
 * Android 12, so the shadows name them through `@ClassName` and the factories answer `Any`.
 */

/** One call a vibrator received, with the attributes as the handler passed them. */
sealed interface VibratorCall {
    /** `cancel()`. */
    data object Cancel : VibratorCall

    /** `vibrate(effect, attributes)`: [attributes] is a `VibrationAttributes` or an `AudioAttributes`. */
    data class Play(val effect: VibrationEffect, val attributes: Any?) : VibratorCall

    /** `vibrate(pattern, repeat, audioAttributes)`, the call for a vibrator without amplitude control. */
    data class Pattern(val timings: List<Long>, val repeat: Int, val attributes: AudioAttributes?) :
        VibratorCall
}

/**
 * A vibrator that records what it is asked. Robolectric hands a call to the shadow of the class
 * that declares the method, so the three `vibrate` overloads that `Vibrator` declares are here and
 * the methods `SystemVibrator` declares are in [ShadowRecordingSystemVibrator].
 */
@Implements(Vibrator::class)
open class ShadowRecordingVibrator {
    var present = true
    var amplitudeControl = true
    val calls = CopyOnWriteArrayList<VibratorCall>()

    @Suppress("unused")
    @Implementation(minSdk = Build.VERSION_CODES.TIRAMISU)
    fun vibrate(
        effect: VibrationEffect,
        @ClassName("android.os.VibrationAttributes") attributes: Any,
    ) {
        calls += VibratorCall.Play(effect, attributes)
    }

    @Suppress("unused", "DEPRECATION") // The handler uses it before Android 13.
    @Implementation
    fun vibrate(effect: VibrationEffect, attributes: AudioAttributes?) {
        calls += VibratorCall.Play(effect, attributes)
    }

    @Suppress("unused", "DEPRECATION") // The handler uses it before Android 13.
    @Implementation
    fun vibrate(pattern: LongArray, repeat: Int, attributes: AudioAttributes?) {
        calls += VibratorCall.Pattern(pattern.toList(), repeat, attributes)
    }
}

@Implements(className = "android.os.SystemVibrator")
class ShadowRecordingSystemVibrator : ShadowRecordingVibrator() {
    @Suppress("unused") @Implementation fun hasVibrator() = present

    @Suppress("unused") @Implementation fun hasAmplitudeControl() = amplitudeControl

    @Suppress("unused")
    @Implementation
    fun cancel() {
        calls += VibratorCall.Cancel
    }
}

/** One `vibrate` of a vibrator manager: a `CombinedVibration` and its `VibrationAttributes`. */
data class ManagerVibration(val combined: Any, val attributes: Any)

/** A vibrator manager of Android 12 and later over the vibrators a test sets. */
@Implements(className = "android.os.VibratorManager", minSdk = Build.VERSION_CODES.S)
open class ShadowRecordingVibratorManager {
    var ids = IntArray(0)
    var vibrators = emptyList<Vibrator>()
    lateinit var defaultOne: Vibrator
    val vibrations = CopyOnWriteArrayList<ManagerVibration>()
    var cancels = 0

    @Suppress("unused")
    @Implementation
    fun vibrate(
        @ClassName("android.os.CombinedVibration") effect: Any,
        @ClassName("android.os.VibrationAttributes") attributes: Any,
    ) {
        vibrations += ManagerVibration(effect, attributes)
    }
}

@Implements(className = "android.os.SystemVibratorManager", minSdk = Build.VERSION_CODES.S)
class ShadowRecordingSystemVibratorManager : ShadowRecordingVibratorManager() {
    @Suppress("unused") @Implementation fun getVibratorIds(): IntArray = ids.copyOf()

    @Suppress("unused")
    @Implementation
    fun getVibrator(vibratorId: Int): Any = vibrators[ids.indexOf(vibratorId)]

    @Suppress("unused") @Implementation fun getDefaultVibrator(): Any = defaultOne

    @Suppress("unused")
    @Implementation
    fun cancel() {
        cancels++
    }
}

/** A sensor manager that records the listeners of the handler and the sampling period each asked for. */
@Implements(SensorManager::class)
open class ShadowRecordingSensorManager {
    /** The sensors this manager has, by type. */
    val sensors = mutableMapOf<Int, Sensor>()

    /** The registrations in force, in the order they were made. */
    val registrations = CopyOnWriteArrayList<Registration>()

    /** Every register and unregister, as "register <type> <period>" and "unregister <type>". */
    val log = CopyOnWriteArrayList<String>()

    /** A listener that is registered now, with the sampling period it asked for. */
    data class Registration(
        val listener: SensorEventListener,
        val sensor: Sensor,
        val samplingPeriodUs: Int,
    )

    @Suppress("unused") @Implementation fun getDefaultSensor(type: Int): Sensor? = sensors[type]

    @Suppress("unused")
    @Implementation
    fun registerListener(
        listener: SensorEventListener,
        sensor: Sensor,
        samplingPeriodUs: Int,
    ): Boolean {
        registrations += Registration(listener, sensor, samplingPeriodUs)
        log += "register ${sensor.type} $samplingPeriodUs"
        return true
    }

    @Suppress("unused")
    @Implementation
    fun unregisterListener(listener: SensorEventListener) {
        val gone = registrations.filter { it.listener === listener }
        registrations.removeAll(gone)
        gone.forEach { log += "unregister ${it.sensor.type}" }
    }

    /** The listener registered for [type]. */
    fun listenerOf(type: Int): SensorEventListener =
        registrations.single { it.sensor.type == type }.listener

    /** Reports a reading of the sensor of [type] to the listener registered for it. */
    fun deliver(type: Int, x: Float, y: Float, z: Float) {
        val registration = registrations.single { it.sensor.type == type }
        val event =
            SensorEventBuilder.newBuilder(registration.sensor, floatArrayOf(x, y, z)).build()
        registration.listener.onSensorChanged(event)
    }
}

@Implements(className = "android.hardware.SystemSensorManager")
class ShadowRecordingSystemSensorManager : ShadowRecordingSensorManager()

/** The colour one `requestLights` carried for a light, as the signed ARGB int. */
data class LightColor(val lightId: Int, val argb: Int)

/** A lights manager of Android 12 and later over the lights a test sets. */
@Implements(className = "android.hardware.lights.LightsManager", minSdk = Build.VERSION_CODES.S)
open class ShadowRecordingLightsManager {
    /** The `Light`s of the device. */
    var deviceLights = emptyList<Any>()

    /** Every session this manager opened, in order. */
    val sessions = CopyOnWriteArrayList<Any>()
}

@Implements(
    className = "android.hardware.lights.SystemLightsManager",
    minSdk = Build.VERSION_CODES.S,
)
class ShadowRecordingSystemLightsManager : ShadowRecordingLightsManager() {
    @Suppress("unused") @Implementation fun getLights(): List<Any> = deviceLights

    @Suppress("unused")
    @Implementation
    fun openSession(): Any =
        Shadow.newInstanceOf(Class.forName(SYSTEM_SESSION_CLASS)).also { sessions += it }

    companion object {
        const val SYSTEM_SESSION_CLASS =
            "android.hardware.lights.SystemLightsManager\$SystemLightsSession"
    }
}

/** A lights session that records the requests it got and whether it was closed. */
@Implements(
    className = "android.hardware.lights.LightsManager\$LightsSession",
    minSdk = Build.VERSION_CODES.S,
)
open class ShadowRecordingLightsSession {
    val requests = CopyOnWriteArrayList<List<LightColor>>()

    @Volatile var closed = false

    /** An exception `close` throws after it recorded the close, as a failing framework would. */
    @Volatile var failOnClose: RuntimeException? = null
}

@Implements(
    className = "android.hardware.lights.SystemLightsManager\$SystemLightsSession",
    minSdk = Build.VERSION_CODES.S,
)
class ShadowRecordingSystemLightsSession : ShadowRecordingLightsSession() {
    @Suppress("unused")
    @Implementation
    fun requestLights(@ClassName("android.hardware.lights.LightsRequest") request: Any) {
        requests += Hardware.colorsOf(request)
    }

    @Suppress("unused")
    @Implementation
    fun close() {
        closed = true
        failOnClose?.let { throw it }
    }
}

/** A battery that a test sets, as `InputDevice.getBatteryState()` reports it. */
@Implements(className = "android.hardware.BatteryState", minSdk = Build.VERSION_CODES.S)
open class ShadowRecordingBatteryState {
    @Volatile var present = true
    @Volatile var batteryStatus = 0
    @Volatile var batteryCapacity = Float.NaN
}

@Implements(
    className = "android.hardware.input.InputDeviceBatteryState",
    minSdk = Build.VERSION_CODES.S,
)
class ShadowRecordingInputDeviceBatteryState : ShadowRecordingBatteryState() {
    @Suppress("unused") @Implementation fun isPresent() = present

    @Suppress("unused") @Implementation fun getStatus() = batteryStatus

    @Suppress("unused") @Implementation fun getCapacity() = batteryCapacity
}

@Implements(
    className = "android.hardware.input.InputManagerGlobal\$LocalBatteryState",
    minSdk = Build.VERSION_CODES.S,
)
class ShadowRecordingLocalBatteryState : ShadowRecordingBatteryState() {
    @Suppress("unused") @Implementation fun isPresent() = present

    @Suppress("unused") @Implementation fun getStatus() = batteryStatus

    @Suppress("unused") @Implementation fun getCapacity() = batteryCapacity
}

/** The factories for the objects above, and what reads their state. */
object Hardware {
    const val FIRST_VIBRATOR_ID = 10
    private const val LIGHT_TYPE_INPUT = 10001
    private const val BRIGHTNESS = 1
    private const val COLOR_RGB = 2

    /** A vibrator. */
    fun vibrator(present: Boolean = true, amplitudeControl: Boolean = true): Vibrator {
        val vibrator = Shadow.newInstanceOf(Class.forName("android.os.SystemVibrator")) as Vibrator
        with(Shadow.extract<ShadowRecordingVibrator>(vibrator)) {
            this.present = present
            this.amplitudeControl = amplitudeControl
        }
        return vibrator
    }

    /** What [vibrator] recorded. */
    fun calls(vibrator: Vibrator): List<VibratorCall> =
        Shadow.extract<ShadowRecordingVibrator>(vibrator).calls

    /**
     * A `VibratorManager` over [vibrators] with the ids [ids]; [defaultVibrator] is the one
     * `getDefaultVibrator()` answers. Android 12 and later.
     */
    fun vibratorManager(
        vibrators: List<Vibrator> = emptyList(),
        ids: IntArray = IntArray(vibrators.size) { FIRST_VIBRATOR_ID + it },
        defaultVibrator: Vibrator = vibrator(present = false, amplitudeControl = false),
    ): Any {
        val manager = Shadow.newInstanceOf(Class.forName("android.os.SystemVibratorManager"))
        with(Shadow.extract<ShadowRecordingVibratorManager>(manager)) {
            this.vibrators = vibrators
            this.ids = ids
            this.defaultOne = defaultVibrator
        }
        return manager
    }

    /** The state of a manager made by [vibratorManager]. */
    fun manager(manager: Any): ShadowRecordingVibratorManager = Shadow.extract(manager)

    /** A sensor manager with the sensors of [types]. */
    fun sensorManager(vararg types: Int): SensorManager {
        val manager =
            Shadow.newInstanceOf(Class.forName("android.hardware.SystemSensorManager"))
                as SensorManager
        val shadow = Shadow.extract<ShadowRecordingSensorManager>(manager)
        for (type in types) {
            shadow.sensors[type] =
                SensorBuilder.newBuilder().setType(type).setName("sensor-$type").build()
        }
        return manager
    }

    /** The state of a manager made by [sensorManager]. */
    fun sensors(manager: SensorManager): ShadowRecordingSensorManager = Shadow.extract(manager)

    /** A battery, as `InputDevice.getBatteryState()` reports it. Android 12 and later. */
    fun battery(present: Boolean = true, status: Int = 0, capacity: Float = Float.NaN): Any {
        val className =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                "android.hardware.input.InputManagerGlobal\$LocalBatteryState"
            } else {
                "android.hardware.input.InputDeviceBatteryState"
            }
        val battery = Shadow.newInstanceOf(Class.forName(className))
        with(Shadow.extract<ShadowRecordingBatteryState>(battery)) {
            this.present = present
            this.batteryStatus = status
            this.batteryCapacity = capacity
        }
        return battery
    }

    /** The state of a battery made by [battery]. */
    fun batteryState(battery: Any): ShadowRecordingBatteryState = Shadow.extract(battery)

    /** A light that can be set to a colour. Android 12 and later. */
    fun rgbLight(id: Int): Any =
        newLight(
            id,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) COLOR_RGB else 0,
        )

    /** A light that has no colour of its own. Android 14 and later: before it, every light says RGB. */
    fun plainLight(id: Int): Any = newLight(id, BRIGHTNESS)

    private fun newLight(id: Int, capabilities: Int): Any =
        Class.forName("android.hardware.lights.Light")
            .getConstructor(
                Int::class.java,
                String::class.java,
                Int::class.java,
                Int::class.java,
                Int::class.java,
            )
            .newInstance(id, "light-$id", 0, LIGHT_TYPE_INPUT, capabilities)

    /** A lights manager over [lights]. Android 12 and later. */
    fun lightsManager(lights: List<Any> = emptyList()): Any {
        val manager =
            Shadow.newInstanceOf(Class.forName("android.hardware.lights.SystemLightsManager"))
        Shadow.extract<ShadowRecordingLightsManager>(manager).deviceLights = lights
        return manager
    }

    /** The state of a manager made by [lightsManager]. */
    fun lights(manager: Any): ShadowRecordingLightsManager = Shadow.extract(manager)

    /** The state of a session a lights manager opened. */
    fun session(session: Any): ShadowRecordingLightsSession = Shadow.extract(session)

    /** The colours of a `LightsRequest`, read where the SDK stubs hide them. */
    @Suppress("UNCHECKED_CAST") // The request's map is `Map<Light, LightState>` by definition.
    fun colorsOf(request: Any): List<LightColor> {
        val states =
            request.javaClass.getMethod("getLightsAndStates").invoke(request) as Map<Any, Any>
        return states.map { (light, state) ->
            LightColor(
                light.javaClass.getMethod("getId").invoke(light) as Int,
                state.javaClass.getMethod("getColor").invoke(state) as Int,
            )
        }
    }
}
