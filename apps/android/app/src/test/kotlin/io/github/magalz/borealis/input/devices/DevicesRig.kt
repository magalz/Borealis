package io.github.magalz.borealis.input.devices

import android.app.Activity
import android.content.ComponentName
import android.hardware.input.InputManager
import android.os.Build
import android.os.SystemClock
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.designsystem.testing.inputDeviceListeners
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.InputDeviceBuilder
import org.robolectric.shadows.ShadowInputEvent
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

/**
 * Stands in for the native `moonlight-core` library, which a unit test does not have. Creating a
 * controller context from a real [InputDevice] asks it for the controller type and for the paddles
 * and the share button of a vendor and product, and this shadow answers from [types], [paddles] and
 * [shares] (anything not listed is unknown, no paddles and no share button) and records every
 * question in [queries]. The static initializer, which loads the native library, only sets the
 * audio configurations that `PreferenceConfiguration.readPreferences` reads.
 *
 * State lives in this object, which every test of one Robolectric sandbox shares: call [reset]
 * before each test.
 *
 * Use it with `@Config(shadows = [ShadowDevicesMoonBridge::class])`.
 */
@Implements(MoonBridge::class)
object ShadowDevicesMoonBridge {
    /** The controller type that `guessControllerType` answers for a vendor and product. */
    val types = mutableMapOf<Pair<Int, Int>, Byte>()

    /** The vendor and product ids that `guessControllerHasPaddles` answers true for. */
    val paddles = mutableSetOf<Pair<Int, Int>>()

    /** The vendor and product ids that `guessControllerHasShareButton` answers true for. */
    val shares = mutableSetOf<Pair<Int, Int>>()

    /** Every question the handler asked, as `"<method> <vendor> <product>"`, in order. */
    val queries = CopyOnWriteArrayList<String>()

    fun reset() {
        types.clear()
        paddles.clear()
        shares.clear()
        queries.clear()
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun __staticInitializer__() {
        set("AUDIO_CONFIGURATION_STEREO", MoonBridge.AudioConfiguration(2, 0x3))
        set("AUDIO_CONFIGURATION_51_SURROUND", MoonBridge.AudioConfiguration(6, 0x3F))
        set("AUDIO_CONFIGURATION_71_SURROUND", MoonBridge.AudioConfiguration(8, 0x63F))
    }

    private fun set(name: String, value: MoonBridge.AudioConfiguration) {
        ReflectionHelpers.setStaticField(MoonBridge::class.java, name, value)
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun guessControllerType(vendorId: Int, productId: Int): Byte {
        queries += "guessControllerType $vendorId $productId"
        return types[vendorId to productId] ?: MoonBridge.LI_CTYPE_UNKNOWN
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun guessControllerHasPaddles(vendorId: Int, productId: Int): Boolean {
        queries += "guessControllerHasPaddles $vendorId $productId"
        return (vendorId to productId) in paddles
    }

    @Suppress("unused")
    @Implementation
    @JvmStatic
    fun guessControllerHasShareButton(vendorId: Int, productId: Int): Boolean {
        queries += "guessControllerHasShareButton $vendorId $productId"
        return (vendorId to productId) in shares
    }
}

/** One controller packet as the handler handed it to the connection. */
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

/** One arrival report: what the handler told the host about a controller that showed up. */
data class Arrival(
    val controller: Int,
    val activeMask: Int,
    val type: Int,
    val supportedButtons: Int,
    val capabilities: Int,
)

/** Records what reaches the PC; the real connection would hand it to the native library. */
class DevicesConnection(activity: Activity) :
    NvConnection(activity, null, 0, "test", null, null, null) {
    val packets = CopyOnWriteArrayList<Sent>()
    val arrivals = CopyOnWriteArrayList<Arrival>()

    /** `"arrival <controller>"` and `"packet <controller>"` in the order the handler sent them. */
    val log = CopyOnWriteArrayList<String>()

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
        log += "packet $controllerNumber"
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

    override fun sendControllerArrivalEvent(
        controllerNumber: Byte,
        activeGamepadMask: Short,
        type: Byte,
        supportedButtonFlags: Int,
        capabilities: Short,
    ): Int {
        log += "arrival $controllerNumber"
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
    ): Int = 0

    override fun sendControllerTouchEvent(
        controllerNumber: Byte,
        eventType: Byte,
        pointerId: Int,
        x: Float,
        y: Float,
        pressure: Float,
    ): Int = 0

    override fun sendMouseMove(deltaX: Short, deltaY: Short) = Unit

    override fun sendMouseButtonDown(mouseButton: Byte) = Unit

    override fun sendMouseButtonUp(mouseButton: Byte) = Unit

    override fun sendMouseScroll(scrollClicks: Byte) = Unit

    override fun sendMouseHScroll(scrollClicks: Byte) = Unit
}

/**
 * One motion range of a test device: the [axis] as the [source] reports it, with its flat value.
 */
data class Axis(val axis: Int, val source: Int = InputDevice.SOURCE_JOYSTICK, val flat: Float = 0f)

/**
 * An input device as the framework reports it, and the key codes its key layout has (what
 * `InputDevice.hasKeys` answers true for).
 */
class Pad(val device: InputDevice, val keys: IntArray) {
    val id: Int
        get() = device.id
}

/** The sources of a typical pad: gamepad buttons and a joystick. */
const val PAD_SOURCES = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK

/** The left stick and the Z and RZ axes of most pads. */
val STICKS_AND_Z =
    listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ).map {
        Axis(it)
    }

/** The left stick alone. */
val LEFT_STICK = listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y).map { Axis(it) }

/**
 * Builds a device. A typical pad by default: external, gamepad and joystick sources, a
 * non-alphabetic keyboard, the left stick and the Z and RZ axes, a descriptor of its own, no
 * vibrator. A negative [id] makes it virtual.
 *
 * Robolectric's `InputDeviceBuilder` needs Android 14; before it the device is made with the
 * framework's own constructor, whose arguments the Android level sets.
 */
fun pad(id: Int, configure: PadSpec.() -> Unit = {}): Pad {
    val spec = PadSpec().apply(configure)
    val name = spec.name ?: "pad-$id"
    val descriptor = spec.descriptor ?: "descriptor-$id"
    val device =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val builder =
                InputDeviceBuilder.newBuilder()
                    .setId(id)
                    .setName(name)
                    .setVendorId(spec.vendorId)
                    .setProductId(spec.productId)
                    .setDescriptor(descriptor)
                    .setExternal(spec.external)
                    .setSources(spec.sources)
                    .setKeyboardType(spec.keyboardType)
                    .setHasVibrator(spec.hasVibrator)
            for (axis in spec.axes) {
                builder.addMotionRange(axis.axis, axis.source, -1f, 1f, axis.flat, 0f, 0f)
            }
            builder.build()
        } else {
            legacyDevice(id, name, descriptor, spec).also { device ->
                for (axis in spec.axes) {
                    ReflectionHelpers.callInstanceMethod<Unit>(
                        device,
                        "addMotionRange",
                        ClassParameter.from(Int::class.javaPrimitiveType, axis.axis),
                        ClassParameter.from(Int::class.javaPrimitiveType, axis.source),
                        ClassParameter.from(Float::class.javaPrimitiveType, -1f),
                        ClassParameter.from(Float::class.javaPrimitiveType, 1f),
                        ClassParameter.from(Float::class.javaPrimitiveType, axis.flat),
                        ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                        ClassParameter.from(Float::class.javaPrimitiveType, 0f),
                    )
                }
            }
        }
    return Pad(device, spec.keys)
}

/**
 * What a test sets of a pad made by [pad]; the rest is a gamepad with two sticks and a Z axis, an
 * external one, with no vibrator and no keys. The name and the descriptor default to `pad-<id>` and
 * `descriptor-<id>`.
 */
class PadSpec {
    var name: String? = null
    var vendorId = 0
    var productId = 0
    var sources = PAD_SOURCES
    var keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC
    var external = true
    var descriptor: String? = null
    var axes: List<Axis> = STICKS_AND_Z
    var keys = intArrayOf()
    var hasVibrator = false
}

// The hidden constructor of Android 9 to 13. Android 12 added the sensor and battery flags.
private fun legacyDevice(id: Int, name: String, descriptor: String, spec: PadSpec): InputDevice {
    val int = Int::class.javaPrimitiveType
    val boolean = Boolean::class.javaPrimitiveType
    val arguments =
        mutableListOf(
            ClassParameter.from(int, id),
            ClassParameter.from(int, 0),
            ClassParameter.from(int, 0),
            ClassParameter.from(String::class.java, name),
            ClassParameter.from(int, spec.vendorId),
            ClassParameter.from(int, spec.productId),
            ClassParameter.from(String::class.java, descriptor),
            ClassParameter.from(boolean, spec.external),
            ClassParameter.from(int, spec.sources),
            ClassParameter.from(int, spec.keyboardType),
            ClassParameter.from(KeyCharacterMap::class.java, null),
            ClassParameter.from(boolean, spec.hasVibrator),
            ClassParameter.from(boolean, false),
            ClassParameter.from(boolean, false),
        )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arguments += ClassParameter.from(boolean, false)
        arguments += ClassParameter.from(boolean, false)
    }
    return ReflectionHelpers.callConstructor(InputDevice::class.java, *arguments.toTypedArray())
}

/** Makes [pad] one of the devices Android has, with its keys. It fires no listener. */
fun connectPad(pad: Pad) {
    val inputManager =
        RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java)
    shadowOf(inputManager).addInputDevice(pad.device)
    shadowOf(inputManager).addInputDeviceKeys(pad.id, pad.keys)
}

/** Takes the device away from Android. It fires no listener. */
fun disconnectPad(pad: Pad) {
    val inputManager =
        RuntimeEnvironment.getApplication().getSystemService(InputManager::class.java)
    shadowOf(inputManager).removeInputDevice(pad.id)
}

/**
 * A real [ControllerHandler] over a [DevicesConnection], with the system's input devices the test
 * gives it. The handler reads the attached devices when it is made, so the [devices] are connected
 * first; [connect] adds one later. Events are stamped with the main looper's clock, which
 * [advanceBy] moves.
 */
class DevicesRig(
    multiController: Boolean = false,
    devices: List<Pad> = emptyList(),
    configure: PreferenceConfiguration.() -> Unit = {},
) {
    init {
        // The SHIELD extension service does not exist here; without this the bind answers with no
        // binder.
        shadowOf(RuntimeEnvironment.getApplication())
            .declareComponentUnbindable(
                ComponentName("com.nvidia.blakepairing", "com.nvidia.blakepairing.AccessoryService")
            )
        devices.forEach(::connectPad)
    }

    val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()
    val connection = DevicesConnection(activity)
    val prefs =
        PreferenceConfiguration().apply {
            this.multiController = multiController
            configure()
        }
    val handler = ControllerHandler(activity, connection, {}, prefs)

    init {
        // Robolectric starts the clock at zero, where a lone Start looks like a Start+RB combo.
        advanceBy(10_000)
    }

    val now: Long
        get() = SystemClock.uptimeMillis()

    /** Moves the clock and runs what the main looper has scheduled in that time. */
    fun advanceBy(millis: Long) {
        ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
    }

    /** Makes [pad] one of the devices Android has, with its keys. It fires no listener. */
    fun connect(pad: Pad) = connectPad(pad)

    /** Takes the device away from Android and tells the handler, as the framework does. */
    fun disconnect(pad: Pad) {
        disconnectPad(pad)
        handler.onInputDeviceRemoved(pad.id)
    }

    /** Tells the handler that Android changed the device, as it does when a pad gains an axis. */
    fun change(pad: Pad) {
        handler.onInputDeviceChanged(pad.id)
    }

    /** The listeners that the handler registered with the framework's input manager. */
    fun registeredListeners(): List<InputManager.InputDeviceListener> = inputDeviceListeners()

    /**
     * A key event of [pad]. Robolectric needs the device handed over for `event.getDevice()`; a
     * test that leaves [withDevice] out sends an event of a device that has gone.
     */
    fun keyEvent(
        pad: Pad,
        action: Int,
        keyCode: Int,
        scanCode: Int = 0,
        withDevice: Boolean = true,
    ): KeyEvent {
        val at = now
        return KeyEvent(at, at, action, keyCode, 0, 0, pad.id, scanCode).also { event ->
            if (withDevice) {
                Shadow.extract<ShadowInputEvent>(event).setDevice(pad.device)
            }
        }
    }

    fun press(pad: Pad, keyCode: Int, scanCode: Int = 0, withDevice: Boolean = true): Boolean =
        handler.handleButtonDown(keyEvent(pad, KeyEvent.ACTION_DOWN, keyCode, scanCode, withDevice))

    /**
     * Makes the handler's context for [pad], with a key the handler ignores: it sends no packet and
     * gives the pad no controller number.
     */
    fun touch(pad: Pad): ContextView {
        press(pad, KeyEvent.KEYCODE_A)
        return context(pad)
    }

    fun release(pad: Pad, keyCode: Int, scanCode: Int = 0): Boolean =
        handler.handleButtonUp(keyEvent(pad, KeyEvent.ACTION_UP, keyCode, scanCode))

    /** The handler's contexts for Android's input devices, by device id. */
    @Suppress("UNCHECKED_CAST") // The field holds the handler's contexts by construction.
    fun contexts(): SparseArray<Any> = field(handler, "inputDeviceContexts") as SparseArray<Any>

    /** The handler's contexts for USB-driver pads, by controller id. */
    @Suppress("UNCHECKED_CAST") // The field holds the handler's contexts by construction.
    fun usbContexts(): SparseArray<Any> = field(handler, "usbDeviceContexts") as SparseArray<Any>

    /** The handler's context for [pad], or null when it has none. */
    fun contextOf(pad: Pad): ContextView? = contexts().get(pad.id)?.let(::ContextView)

    /** The context that [pad]'s events make, which the handler has made once an event came. */
    fun context(pad: Pad): ContextView =
        checkNotNull(contextOf(pad)) { "no context for device ${pad.id}" }

    /** The handler's default context: events with no device and the on-screen controller. */
    fun defaultContext(): ContextView = ContextView(checkNotNull(field(handler, "defaultContext")))

    /** Ends the handler's background thread, as the stream does when it ends. */
    fun close() {
        handler.destroy()
    }
}

/** A field of [target] or of one of its superclasses, read through reflection. */
fun field(target: Any, name: String): Any? =
    fieldOwner(target.javaClass, name)
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(target)

fun setField(target: Any, name: String, value: Any?) {
    fieldOwner(target.javaClass, name)
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .set(target, value)
}

private fun fieldOwner(start: Class<*>, name: String): Class<*> =
    generateSequence(start) { it.superclass }
        .firstOrNull { owner -> owner.declaredFields.any { it.name == name } }
        ?: error("${start.name} has no field $name")

/**
 * One of the handler's controller contexts (`ControllerHandler.InputDeviceContext`), read by field
 * name. The names are the ones of the Java; the Kotlin conversion keeps them.
 */
class ContextView(val context: Any) {
    operator fun get(name: String): Any? = field(context, name)

    fun bool(name: String): Boolean = get(name) as Boolean

    fun int(name: String): Int = get(name) as Int

    fun short(name: String): Int = (get(name) as Short).toInt()

    fun float(name: String): Float = get(name) as Float

    fun string(name: String): String? = get(name) as String?
}
