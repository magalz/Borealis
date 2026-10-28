package io.github.magalz.borealis.input.devices

import android.hardware.SensorManager
import android.hardware.lights.Light
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.os.Build
import android.os.Vibrator
import android.view.InputDevice
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import org.cgutman.shieldcontrollerextensions.SceManager
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * What the framework tells the handler about the vibrators and the lights of an input device.
 * Robolectric answers every one of those questions with null, which the handler does not expect
 * from a real device, so [ShadowDeviceHardware] answers them from here. The sensors of a device
 * are the ones Robolectric's sensor manager has (they are shared by every sensor manager).
 */
data class Hardware(
    /** How many vibrators the device's vibrator manager has. */
    val vibrators: Int = 0,
    /** Whether each of them has amplitude control. */
    val amplitudeControl: Boolean = true,
    /** How many of its lights can show a color. */
    val rgbLights: Int = 0,
    /** How many of its lights cannot. */
    val plainLights: Int = 0,
)

/** The hardware of each device id, and what the handler did to it. */
object DeviceHardware {
    private val byDevice = mutableMapOf<Int, Hardware>()
    private val sensorManagers = mutableMapOf<Int, SensorManager>()

    /** The device ids whose vibrator manager the handler cancelled, in order. */
    val vibratorManagerCancels = mutableListOf<Int>()

    /** How many vibrators the vibrator manager of the phone itself has. */
    var systemVibrators = 0

    /** Whether `InputDevice.isExternal()` throws, as a framework that cannot answer would. */
    var externalFails = false

    fun set(deviceId: Int, hardware: Hardware) {
        byDevice[deviceId] = hardware
    }

    fun of(deviceId: Int): Hardware = byDevice[deviceId] ?: Hardware()

    /** The sensor manager that [InputDevice.getSensorManager] answers for the device. */
    fun sensorManagerOf(deviceId: Int): SensorManager =
        sensorManagers.getOrPut(deviceId) { newInstance("android.hardware.SystemSensorManager") }

    fun reset() {
        byDevice.clear()
        sensorManagers.clear()
        vibratorManagerCancels.clear()
        systemVibrators = 0
        externalFails = false
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> newInstance(className: String): T =
    ReflectionHelpers.callConstructor(Class.forName(className)) as T

private fun light(id: Int, rgb: Boolean): Light =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        val capabilities =
            if (rgb) Light.LIGHT_CAPABILITY_COLOR_RGB else Light.LIGHT_CAPABILITY_BRIGHTNESS
        ReflectionHelpers.callConstructor(
            Light::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, id),
            ClassParameter.from(String::class.java, "light-$id"),
            ClassParameter.from(Int::class.javaPrimitiveType, id),
            ClassParameter.from(Int::class.javaPrimitiveType, Light.LIGHT_TYPE_INPUT),
            ClassParameter.from(Int::class.javaPrimitiveType, capabilities),
        )
    } else {
        // Before Android 14 the capabilities are not an argument: hasRgbControl() is always true.
        ReflectionHelpers.callConstructor(
            Light::class.java,
            ClassParameter.from(Int::class.javaPrimitiveType, id),
            ClassParameter.from(Int::class.javaPrimitiveType, id),
            ClassParameter.from(Int::class.javaPrimitiveType, Light.LIGHT_TYPE_INPUT),
        )
    }

/**
 * Gives an input device the vibrators, lights and sensor manager of [DeviceHardware]. The vibrator
 * and lights managers are typed `Any` because their classes only exist from Android 12, and this
 * shadow is also loaded on the levels before it.
 */
@Implements(InputDevice::class)
class ShadowDeviceHardware {
    // Robolectric sets the field when it makes the shadow, so it cannot be a val.
    @Suppress("VarCouldBeVal") @RealObject private lateinit var device: InputDevice

    @Suppress("unused")
    @Implementation
    fun getVibratorManager(): Any {
        val manager = newInstance<Any>("android.hardware.input.InputDeviceVibratorManager")
        Shadow.extract<ShadowDeviceVibratorManager>(manager).deviceId = device.id
        return manager
    }

    @Suppress("unused")
    @Implementation
    fun getLightsManager(): Any {
        val manager = newInstance<Any>("android.hardware.input.InputDeviceLightsManager")
        Shadow.extract<ShadowDeviceLightsManager>(manager).deviceId = device.id
        return manager
    }

    @Suppress("unused")
    @Implementation
    fun getSensorManager(): SensorManager = DeviceHardware.sensorManagerOf(device.id)

    /** The device's own answer, unless [DeviceHardware.externalFails] makes it throw. */
    @Suppress("unused")
    @Implementation
    fun isExternal(): Boolean {
        check(!DeviceHardware.externalFails) { "the framework cannot say" }
        return Shadow.directlyOn<Boolean, InputDevice>(device, InputDevice::class.java, "isExternal")
    }
}

@Implements(
    className = "android.hardware.input.InputDeviceVibratorManager",
    isInAndroidSdk = false,
    minSdk = Build.VERSION_CODES.S,
)
class ShadowDeviceVibratorManager {
    var deviceId = 0

    @Suppress("unused")
    @Implementation
    fun getVibratorIds(): IntArray = IntArray(DeviceHardware.of(deviceId).vibrators) { it + 1 }

    @Suppress("unused")
    @Implementation
    fun getVibrator(vibratorId: Int): Vibrator {
        val vibrator = newInstance<Vibrator>("android.hardware.input.InputDeviceVibrator")
        Shadow.extract<ShadowDeviceVibrator>(vibrator).deviceId = deviceId
        return vibrator
    }

    @Suppress("unused")
    @Implementation
    fun cancel() {
        DeviceHardware.vibratorManagerCancels += deviceId
    }
}

@Implements(
    className = "android.hardware.input.InputDeviceVibrator",
    isInAndroidSdk = false,
    minSdk = Build.VERSION_CODES.S,
)
class ShadowDeviceVibrator {
    var deviceId = 0

    @Suppress("unused")
    @Implementation
    fun hasAmplitudeControl(): Boolean = DeviceHardware.of(deviceId).amplitudeControl

    @Suppress("unused", "FunctionOnlyReturningConstant")
    @Implementation
    fun hasVibrator(): Boolean = true

    /** How many times the handler cancelled this vibrator. */
    var cancels = 0

    @Suppress("unused")
    @Implementation
    fun cancel() {
        cancels++
    }
}

@Implements(
    className = "android.hardware.input.InputDeviceLightsManager",
    isInAndroidSdk = false,
    minSdk = Build.VERSION_CODES.S,
)
class ShadowDeviceLightsManager {
    var deviceId = 0

    @Suppress("unused")
    @Implementation
    fun getLights(): List<Light> {
        val hardware = DeviceHardware.of(deviceId)
        return List(hardware.rgbLights) { light(it, rgb = true) } +
            List(hardware.plainLights) { light(hardware.rgbLights + it, rgb = false) }
    }
}

/**
 * A session with the lights of an input device, as `InputDeviceLightsManager.openSession()` gives
 * one, that records what the handler asks of it: every request, and every close (which fails with
 * [closeFailure] when the test sets one). The requests come from the handler's background thread;
 * [firstRequest] opens when the first one arrives.
 */
@Implements(
    className = "android.hardware.input.InputDeviceLightsManager\$InputDeviceLightsSession",
    isInAndroidSdk = false,
    minSdk = Build.VERSION_CODES.S,
)
class ShadowDeviceLightsSession {
    val requests = CopyOnWriteArrayList<LightsRequest>()
    val firstRequest = CountDownLatch(1)
    val closes = CopyOnWriteArrayList<Unit>()
    var closeFailure: RuntimeException? = null

    @Suppress("unused")
    @Implementation
    fun requestLights(request: LightsRequest) {
        requests += request
        firstRequest.countDown()
    }

    @Suppress("unused")
    @Implementation
    fun close() {
        closes += Unit
        closeFailure?.let { throw it }
    }
}

/** A new [ShadowDeviceLightsSession], for a test to hand to a context as its lights session. */
fun recordingLightsSession(): LightsManager.LightsSession =
    newInstance("android.hardware.input.InputDeviceLightsManager\$InputDeviceLightsSession")

val LightsManager.LightsSession.recorded: ShadowDeviceLightsSession
    get() = Shadow.extract(this)

/**
 * The vibrator manager of the phone itself. Robolectric's has no vibrators; this one has
 * [DeviceHardware.systemVibrators], each of which has amplitude control when Robolectric's vibrator
 * does (`ShadowVibrator.setHasAmplitudeControl`).
 */
@Implements(
    className = "android.os.SystemVibratorManager",
    isInAndroidSdk = false,
    minSdk = Build.VERSION_CODES.S,
)
class ShadowSystemVibratorManager {
    @Suppress("unused")
    @Implementation
    fun getVibratorIds(): IntArray = IntArray(DeviceHardware.systemVibrators) { it + 1 }

    @Suppress("unused")
    @Implementation
    fun getVibrator(vibratorId: Int): Vibrator =
        RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
}

/** The ids of the input devices that the SHIELD controller service says it knows. */
object SceDevices {
    val recognized = mutableSetOf<Int>()

    fun reset() {
        recognized.clear()
    }
}

/**
 * The SHIELD controller service, which a unit test cannot bind. The handler asks it whether a
 * device is a SHIELD controller; it answers from [SceDevices.recognized]. Everything else it does
 * (battery, rumble) answers as an unbound service does, so nothing is reported.
 */
@Implements(SceManager::class)
class ShadowDeviceSceManager {
    @Suppress("unused")
    @Implementation
    fun isRecognizedDevice(device: InputDevice): Boolean = device.id in SceDevices.recognized
}
