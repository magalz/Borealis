package io.github.magalz.borealis.input.feedback

import android.os.Build
import android.view.InputDevice
import com.limelight.nvstream.jni.MoonBridge
import java.util.concurrent.CopyOnWriteArrayList
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.annotation.RealObject

/** One battery packet that reached the native library, with the signed bytes as the handler sent them. */
data class BatteryEvent(val controller: Int, val state: Int, val percentage: Int)

/**
 * Stands in for the native `moonlight-core` library, whose static initialiser a unit test cannot
 * run. Creating a controller context asks it for the pad's type, paddles and Share button, and the
 * battery packets of the handler end in it.
 *
 * State lives in this object, which every test of one Robolectric sandbox shares: call [reset]
 * before each test. Battery packets come from the handler's own thread, hence the concurrent list.
 *
 * Use it with `@Config(shadows = [ShadowFeedbackBridge::class, ShadowFeedbackInputDevice::class])`.
 */
@Implements(MoonBridge::class)
object ShadowFeedbackBridge {
    val batteryEvents = CopyOnWriteArrayList<BatteryEvent>()

    fun reset() {
        batteryEvents.clear()
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun __staticInitializer__() {
        // No native library to load.
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun guessControllerType(vendorId: Int, productId: Int): Byte = MoonBridge.LI_CTYPE_UNKNOWN

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun guessControllerHasPaddles(vendorId: Int, productId: Int): Boolean = false

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun guessControllerHasShareButton(vendorId: Int, productId: Int): Boolean = false

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun sendControllerBatteryEvent(
        controllerNumber: Byte,
        batteryState: Byte,
        batteryPercentage: Byte,
    ): Int {
        batteryEvents +=
            BatteryEvent(controllerNumber.toInt(), batteryState.toInt(), batteryPercentage.toInt())
        return 0
    }
}

/**
 * What a test gives one input device: the framework objects Robolectric cannot make for it, made
 * with [Hardware]. The fields are `Any?` because several of these types do not exist before
 * Android 12, and a shadow that names them in a signature cannot load there. A field left null
 * gets an absent piece of hardware when the rig connects the pad: no vibrator, no sensors, no
 * lights, no battery.
 */
class DeviceFeatures(
    /** A `Vibrator`: what `InputDevice.getVibrator()` answers. */
    var vibrator: Any? = null,
    /** A `VibratorManager`, from Android 12. */
    var vibratorManager: Any? = null,
    /** A `SensorManager`, from Android 12. */
    var sensorManager: Any? = null,
    /** A `LightsManager`, from Android 12. */
    var lightsManager: Any? = null,
    /** A `BatteryState`, from Android 12. */
    var batteryState: Any? = null,
) {
    /** Fills what the test left out with hardware that is not there. */
    fun withAbsentHardware(): DeviceFeatures {
        if (vibrator == null) vibrator = Hardware.vibrator(present = false, amplitudeControl = false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (vibratorManager == null) vibratorManager = Hardware.vibratorManager()
            if (sensorManager == null) sensorManager = Hardware.sensorManager()
            if (lightsManager == null) lightsManager = Hardware.lightsManager()
            if (batteryState == null) batteryState = Hardware.battery(present = false)
        }
        return this
    }
}

/**
 * Robolectric answers `InputDevice.getVibratorManager()`, `getLightsManager()` and the other
 * hardware accessors from Android 12 with nothing or a null, which the handler dereferences. This
 * shadow answers them from [features], by device id.
 *
 * Call [reset] before each test.
 */
@Implements(InputDevice::class)
class ShadowFeedbackInputDevice {
    @Suppress("VarCouldBeVal") @RealObject private lateinit var device: InputDevice

    @Suppress("unused") @Implementation fun getVibrator(): Any = features.getValue(device.id).vibrator!!

    @Suppress("unused")
    @Implementation(minSdk = Build.VERSION_CODES.S)
    fun getVibratorManager(): Any = features.getValue(device.id).vibratorManager!!

    @Suppress("unused")
    @Implementation(minSdk = Build.VERSION_CODES.S)
    fun getSensorManager(): Any = features.getValue(device.id).sensorManager!!

    @Suppress("unused")
    @Implementation(minSdk = Build.VERSION_CODES.S)
    fun getLightsManager(): Any = features.getValue(device.id).lightsManager!!

    @Suppress("unused")
    @Implementation(minSdk = Build.VERSION_CODES.S)
    fun getBatteryState(): Any = features.getValue(device.id).batteryState!!

    companion object {
        /** Device id to its hardware. */
        val features = mutableMapOf<Int, DeviceFeatures>()

        fun reset() = features.clear()
    }
}
