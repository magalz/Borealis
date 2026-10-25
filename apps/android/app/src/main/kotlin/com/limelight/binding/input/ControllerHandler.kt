package com.limelight.binding.input

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.hardware.BatteryState
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.input.InputManager
import android.hardware.lights.LightState
import android.hardware.lights.LightsManager
import android.hardware.lights.LightsRequest
import android.hardware.usb.UsbManager
import android.media.AudioAttributes
import android.os.Build
import android.os.CombinedVibration
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.SparseArray
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.util.isNotEmpty
import androidx.core.util.size
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import com.limelight.binding.input.driver.UsbDriverService
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.input.ControllerPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.GameGestures
import com.limelight.utils.Vector2d
import io.github.magalz.borealis.designsystem.adaptive.hasControllerJoystickAxes
import io.github.magalz.borealis.stream.input.ButtonState
import io.github.magalz.borealis.stream.input.CaptureEnd
import io.github.magalz.borealis.stream.input.ChromeKeySink
import io.github.magalz.borealis.stream.input.GamepadChrome
import io.github.magalz.borealis.stream.input.GuideChord
import io.github.magalz.borealis.stream.input.HeldSinceCapture
import io.github.magalz.borealis.stream.input.MenuChordDetector
import io.github.magalz.borealis.stream.input.MenuChordListener
import io.github.magalz.borealis.stream.input.PixelDelta
import io.github.magalz.borealis.stream.input.PointerMotion
import io.github.magalz.borealis.stream.input.PointerSpeed
import io.github.magalz.borealis.stream.input.QuitCombo
import io.github.magalz.borealis.stream.input.StartHold
import io.github.magalz.borealis.stream.input.UsbChromeInput
import java.lang.reflect.InvocationTargetException
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import org.cgutman.shieldcontrollerextensions.SceChargingState
import org.cgutman.shieldcontrollerextensions.SceConnectionType
import org.cgutman.shieldcontrollerextensions.SceManager

// Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
@Suppress("LargeClass", "TooManyFunctions")
class ControllerHandler(
    private val activityContext: Activity,
    private val conn: NvConnection,
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    // The Java stored the gestures and never read them.
    @Suppress("UnusedPrivateProperty") private val gestures: GameGestures,
    private val prefConfig: PreferenceConfiguration,
) : InputManager.InputDeviceListener, UsbDriverListener, GamepadChrome {

    private val inputVector = Vector2d()

    private val inputDeviceContexts = SparseArray<InputDeviceContext>()
    private val usbDeviceContexts = SparseArray<UsbDeviceContext>()

    private val stickDeadzone: Double
    private val defaultContext = InputDeviceContext()
    private val inputManager: InputManager
    private val deviceVibrator: Vibrator
    private val deviceVibratorManager: VibratorManager?
    private val deviceSensorManager: SensorManager?
    private val sceManager: SceManager
    private val mainThreadHandler: Handler
    private val backgroundHandlerThread: HandlerThread
    private val backgroundThreadHandler: Handler
    private var stopped = false
    @Volatile private var menuChordListener: MenuChordListener? = null
    @Volatile private var chromeKeySink: ChromeKeySink? = null

    // While the chrome owns the gamepad nothing from any pad, or the on-screen controller, may
    // reach the PC
    @Volatile private var captured = false
    private val captureEnd = CaptureEnd()
    @Volatile private var mouseEmulationEnabled = false

    // The speed of the gamepad mouse for this session, as a factor of the setting
    @Volatile private var pointerSpeedFactor = 1f
    private val inputLock = Any()
    private var controllersWithInput = 0 // guarded by inputLock

    private var currentControllers: Short = 0
    private var initialControllers: Short = 0

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                activityContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE)
                    as VibratorManager
            deviceVibratorManager = vibratorManager
            deviceVibrator = vibratorManager.defaultVibrator
        } else {
            deviceVibratorManager = null
            @Suppress("DEPRECATION")
            deviceVibrator = activityContext.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        deviceSensorManager =
            activityContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
        inputManager = activityContext.getSystemService(Context.INPUT_SERVICE) as InputManager
        mainThreadHandler = Handler(Looper.getMainLooper())

        // Create a HandlerThread to process battery state updates. These can be slow enough
        // that they lead to ANRs if we do them on the main thread.
        backgroundHandlerThread = HandlerThread("ControllerHandler")
        backgroundHandlerThread.start()
        backgroundThreadHandler = Handler(backgroundHandlerThread.looper)

        sceManager = SceManager(activityContext)
        sceManager.start()

        var deadzonePercentage = prefConfig.deadzonePercentage

        // 1% is the lowest possible deadzone we support
        if (deadzonePercentage <= 0) {
            deadzonePercentage = 1
        }

        stickDeadzone = deadzonePercentage.toDouble() / PERCENT

        // Initialize the default context for events with no device
        defaultContext.leftStickXAxis = MotionEvent.AXIS_X
        defaultContext.leftStickYAxis = MotionEvent.AXIS_Y
        defaultContext.leftStickDeadzoneRadius = stickDeadzone.toFloat()
        defaultContext.rightStickXAxis = MotionEvent.AXIS_Z
        defaultContext.rightStickYAxis = MotionEvent.AXIS_RZ
        defaultContext.rightStickDeadzoneRadius = stickDeadzone.toFloat()
        defaultContext.leftTriggerAxis = MotionEvent.AXIS_BRAKE
        defaultContext.rightTriggerAxis = MotionEvent.AXIS_GAS
        defaultContext.hatXAxis = MotionEvent.AXIS_HAT_X
        defaultContext.hatYAxis = MotionEvent.AXIS_HAT_Y
        defaultContext.controllerNumber = 0
        defaultContext.assignedControllerNumber = true
        defaultContext.external = false

        // Some devices (GPD XD) have a back button which sends input events
        // with device ID == 0. This hits the default context which would normally
        // consume these. Instead, let's ignore them since that's probably the
        // most likely case.
        defaultContext.ignoreBack = true

        // Get the initially attached set of gamepads. As each gamepad receives
        // its initial InputEvent, we will move these from this set onto the
        // currentControllers set which will allow them to properly unplug
        // if they are removed.
        initialControllers = getAttachedControllerMask(activityContext, prefConfig)

        // Register ourselves for input device notifications
        inputManager.registerInputDeviceListener(this, null)
    }

    override fun onInputDeviceAdded(deviceId: Int) {
        // Nothing happening here yet
    }

    override fun onInputDeviceRemoved(deviceId: Int) {
        val context = inputDeviceContexts.get(deviceId)
        if (context != null) {
            LimeLog.info("Removed controller: ${context.name} ($deviceId)")
            releaseControllerNumber(context)
            context.destroy()
            inputDeviceContexts.remove(deviceId)
        }
    }

    // This can happen when gaining/losing input focus with some devices.
    // Input devices that have a trackpad may gain/lose AXIS_RELATIVE_X/Y.
    override fun onInputDeviceChanged(deviceId: Int) {
        val device = InputDevice.getDevice(deviceId) ?: return

        // If we don't have a context for this device, we don't need to update anything
        val existingContext = inputDeviceContexts.get(deviceId) ?: return

        LimeLog.info("Device changed: ${existingContext.name} ($deviceId)")

        // Migrate the existing context into this new one by moving any stateful elements
        val newContext = createInputDeviceContextForDevice(device)
        newContext.migrateContext(existingContext)
        inputDeviceContexts.put(deviceId, newContext)
    }

    fun stop() {
        if (stopped) {
            return
        }

        // Stop new device contexts from being created or used
        stopped = true

        // Unregister our input device callbacks
        inputManager.unregisterInputDeviceListener(this)

        inputDeviceContexts.forEachValue { deviceContext -> deviceContext.destroy() }

        usbDeviceContexts.forEachValue { deviceContext -> deviceContext.destroy() }

        // The default context is not in either list but schedules work too
        defaultContext.destroy()

        deviceVibrator.cancel()
    }

    fun destroy() {
        if (!stopped) {
            stop()
        }

        sceManager.stop()
        backgroundHandlerThread.quit()
    }

    fun disableSensors() {
        inputDeviceContexts.forEachValue { deviceContext -> deviceContext.disableSensors() }
    }

    fun enableSensors() {
        if (stopped) {
            return
        }

        inputDeviceContexts.forEachValue { deviceContext -> deviceContext.enableSensors() }
    }

    private fun releaseControllerNumber(context: GenericControllerContext) {
        // If we reserved a controller number, remove that reservation
        if (context.reservedControllerNumber) {
            LimeLog.info("Controller number ${context.controllerNumber} is now available")
            currentControllers =
                (currentControllers.toInt() and (1 shl context.controllerNumber.toInt()).inv())
                    .toShort()

            // The PC drops the controller with the zeroed packet below, so it has no input to
            // release
            synchronized(inputLock) {
                controllersWithInput =
                    controllersWithInput and (1 shl context.controllerNumber.toInt()).inv()
            }
        }

        // If this device sent data as a gamepad, zero the values before removing.
        // We must do this after clearing the currentControllers entry so this
        // causes the device to be removed on the server PC.
        if (context.assignedControllerNumber) {
            conn.sendControllerInput(
                context.controllerNumber,
                getActiveControllerMask(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount")
    private fun isAssociatedJoystick(
        originalDevice: InputDevice?,
        possibleAssociatedJoystick: InputDevice?,
    ): Boolean {
        if (possibleAssociatedJoystick == null) {
            return false
        }

        // This can't be an associated joystick if it's not a joystick
        if (
            (possibleAssociatedJoystick.sources and InputDevice.SOURCE_JOYSTICK) !=
                InputDevice.SOURCE_JOYSTICK
        ) {
            return false
        }

        // Make sure the device names *don't* match in order to prevent us from accidentally
        // matching on another of the exact same device.
        val original = checkNotNull(originalDevice)
        if (possibleAssociatedJoystick.name == original.name) {
            return false
        }

        // Make sure the descriptor matches. This can match in cases where two of the exact same
        // input device are connected, so we perform the name check to exclude that case.
        if (possibleAssociatedJoystick.descriptor != original.descriptor) {
            return false
        }

        return true
    }

    // Called before sending input but after we've determined that this
    // is definitely a controller (not a keyboard, mouse, or something else)
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun assignControllerNumberIfNeeded(context: GenericControllerContext) {
        if (context.assignedControllerNumber) {
            return
        }

        if (context is InputDeviceContext) {
            val devContext: InputDeviceContext = context

            LimeLog.info("${devContext.name} (${context.id}) needs a controller number assigned")
            if (!devContext.external) {
                LimeLog.info("Built-in buttons hardcoded as controller 0")
                context.controllerNumber = 0
            } else if (prefConfig.multiController && devContext.hasJoystickAxes) {
                context.controllerNumber = 0

                LimeLog.info("Reserving the next available controller number")
                for (i in 0 until MAX_GAMEPADS) {
                    if ((currentControllers.toInt() and (1 shl i)) == 0) {
                        // Found an unused controller value
                        currentControllers = (currentControllers.toInt() or (1 shl i)).toShort()

                        // Take this value out of the initial gamepad set
                        initialControllers =
                            (initialControllers.toInt() and (1 shl i).inv()).toShort()

                        context.controllerNumber = i.toShort()
                        context.reservedControllerNumber = true
                        break
                    }
                }
            } else if (!devContext.hasJoystickAxes) {
                // If this device doesn't have joystick axes, it may be an input device associated
                // with another joystick (like a PS4 touchpad). We'll propagate that joystick's
                // controller number to this associated device.

                context.controllerNumber = 0

                // For the DS4 case, the associated joystick is the next device after the touchpad.
                // We'll try the opposite case too, just to be a little future-proof.
                var associatedDevice = InputDevice.getDevice(devContext.id + 1)
                if (!isAssociatedJoystick(devContext.inputDevice, associatedDevice)) {
                    associatedDevice = InputDevice.getDevice(devContext.id - 1)
                    if (!isAssociatedJoystick(devContext.inputDevice, associatedDevice)) {
                        LimeLog.info("No associated joystick device found")
                        associatedDevice = null
                    }
                }

                if (associatedDevice != null) {
                    var associatedDeviceContext = inputDeviceContexts.get(associatedDevice.id)

                    // Create a new context for the associated device if one doesn't exist
                    if (associatedDeviceContext == null) {
                        associatedDeviceContext =
                            createInputDeviceContextForDevice(associatedDevice)
                        inputDeviceContexts.put(associatedDevice.id, associatedDeviceContext)
                    }

                    // Assign a controller number for the associated device if one isn't assigned
                    if (!associatedDeviceContext.assignedControllerNumber) {
                        assignControllerNumberIfNeeded(associatedDeviceContext)
                    }

                    // Propagate the associated controller number
                    context.controllerNumber = associatedDeviceContext.controllerNumber

                    LimeLog.info(
                        "Propagated controller number from ${associatedDeviceContext.name}"
                    )
                }
            } else {
                LimeLog.info("Not reserving a controller number")
                context.controllerNumber = 0
            }

            // If the gamepad doesn't have motion sensors, use the on-device sensors as a fallback
            // for player 1
            if (
                prefConfig.gamepadMotionSensorsFallbackToDevice &&
                    context.controllerNumber.toInt() == 0 &&
                    devContext.sensorManager == null
            ) {
                devContext.sensorManager = deviceSensorManager
            }
        } else {
            if (prefConfig.multiController) {
                context.controllerNumber = 0

                LimeLog.info("Reserving the next available controller number")
                for (i in 0 until MAX_GAMEPADS) {
                    if ((currentControllers.toInt() and (1 shl i)) == 0) {
                        // Found an unused controller value
                        currentControllers = (currentControllers.toInt() or (1 shl i)).toShort()

                        // Take this value out of the initial gamepad set
                        initialControllers =
                            (initialControllers.toInt() and (1 shl i).inv()).toShort()

                        context.controllerNumber = i.toShort()
                        context.reservedControllerNumber = true
                        break
                    }
                }
            } else {
                LimeLog.info("Not reserving a controller number")
                context.controllerNumber = 0
            }
        }

        LimeLog.info("Assigned as controller ${context.controllerNumber}")
        context.assignedControllerNumber = true

        // Report attributes of this new controller to the host
        context.sendControllerArrival()
    }

    private fun createUsbDeviceContextForDevice(device: AbstractController): UsbDeviceContext {
        val context = UsbDeviceContext()

        context.id = device.controllerId
        context.device = device
        context.external = true

        context.vendorId = device.vendorId
        context.productId = device.productId

        context.leftStickDeadzoneRadius = stickDeadzone.toFloat()
        context.rightStickDeadzoneRadius = stickDeadzone.toFloat()
        context.triggerDeadzone = DEFAULT_TRIGGER_DEADZONE

        return context
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount")
    private fun shouldIgnoreBack(dev: InputDevice): Boolean {
        val devName = dev.name

        // The Serval has a Select button but the framework doesn't
        // know about that because it uses a non-standard scancode.
        if (devName.contains("Razer Serval")) {
            return true
        }

        // Classify this device as a remote by name if it has no joystick axes
        if (!hasJoystickAxes(dev) && devName.lowercase(Locale.getDefault()).contains("remote")) {
            return true
        }

        // Otherwise, dynamically try to determine whether we should allow this
        // back button to function for navigation.
        //
        // First, check if this is an internal device we're being called on.
        if (!isExternal(dev)) {
            val im = activityContext.getSystemService(Context.INPUT_SERVICE) as InputManager

            var foundInternalGamepad = false
            var foundInternalSelect = false
            for (id in im.inputDeviceIds) {
                val currentDev = im.getInputDevice(id)

                // Ignore external devices
                if (currentDev == null || isExternal(currentDev)) {
                    continue
                }

                // Note that we are explicitly NOT excluding the current device we're examining
                // here, since the other gamepad buttons may be on our current device and that's
                // fine.
                if (currentDev.hasKeys(KeyEvent.KEYCODE_BUTTON_SELECT)[0]) {
                    foundInternalSelect = true
                }

                // We don't check KEYCODE_BUTTON_A here, since the Shield Android TV has a
                // virtual mouse device that claims to have KEYCODE_BUTTON_A. Instead, we rely
                // on the SOURCE_GAMEPAD flag to be set on gamepad devices.
                if (hasGamepadButtons(currentDev)) {
                    foundInternalGamepad = true
                }
            }

            // Allow the back button to function for navigation if we either:
            // a) have no internal gamepad (most phones)
            // b) have an internal gamepad but also have an internal select button (GPD XD)
            // but not:
            // c) have an internal gamepad but no internal select button (NVIDIA SHIELD Portable)
            return !foundInternalGamepad || foundInternalSelect
        } else {
            // For external devices, we want to pass through the back button if the device
            // has no gamepad axes or gamepad buttons.
            return !hasJoystickAxes(dev) && !hasGamepadButtons(dev)
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress(
        "ComplexCondition",
        "CyclomaticComplexMethod",
        "DEPRECATION",
        "LongMethod",
    )
    private fun createInputDeviceContextForDevice(dev: InputDevice): InputDeviceContext {
        val context = InputDeviceContext()
        val devName = dev.name

        LimeLog.info("Creating controller context for device: $devName")
        LimeLog.info("Vendor ID: ${dev.vendorId}")
        LimeLog.info("Product ID: ${dev.productId}")
        LimeLog.info(dev.toString())

        context.inputDevice = dev
        context.name = devName
        context.id = dev.id
        context.external = isExternal(dev)

        context.vendorId = dev.vendorId
        context.productId = dev.productId

        // These aren't always present in the Android key layout files, so they won't show up
        // in our normal InputDevice.hasKeys() probing.
        context.hasPaddles =
            MoonBridge.guessControllerHasPaddles(context.vendorId, context.productId)
        context.hasShare =
            MoonBridge.guessControllerHasShareButton(context.vendorId, context.productId)

        // Try to use the InputDevice's associated vibrators first
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                hasQuadAmplitudeControlledRumbleVibrators(dev.vibratorManager)
        ) {
            context.vibratorManager = dev.vibratorManager
            context.quadVibrators = true
        } else if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                hasDualAmplitudeControlledRumbleVibrators(dev.vibratorManager)
        ) {
            context.vibratorManager = dev.vibratorManager
            context.quadVibrators = false
        } else if (dev.vibrator.hasVibrator()) {
            context.vibrator = dev.vibrator
        } else if (!context.external) {
            // If this is an internal controller, try to use the device's vibrator
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    hasQuadAmplitudeControlledRumbleVibrators(checkNotNull(deviceVibratorManager))
            ) {
                context.vibratorManager = deviceVibratorManager
                context.quadVibrators = true
            } else if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    hasDualAmplitudeControlledRumbleVibrators(checkNotNull(deviceVibratorManager))
            ) {
                context.vibratorManager = deviceVibratorManager
                context.quadVibrators = false
            } else if (deviceVibrator.hasVibrator()) {
                context.vibrator = deviceVibrator
            }
        }

        // On Android 12, we can try to use the InputDevice's sensors. This may not work if the
        // Linux kernel version doesn't have motion sensor support, which is common for third-party
        // gamepads.
        //
        // Android 12 has a bug that causes InputDeviceSensorManager to cause a NPE on a background
        // thread due to bad error checking in InputListener callbacks. InputDeviceSensorManager is
        // created upon the first call to InputDevice.getSensorManager(), so we avoid calling this
        // on Android 12 unless we have a gamepad that could plausibly have motion sensors.
        // https://cs.android.com/android/_/android/platform/frameworks/base/+/8970010a5e9f3dc5c069f56b4147552accfcbbeb
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ||
                    context.vendorId == VENDOR_SONY ||
                    context.vendorId == VENDOR_NINTENDO) && // Sony or Nintendo
                    prefConfig.gamepadMotionSensors
            ) {
                if (
                    dev.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
                        dev.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null
                ) {
                    context.sensorManager = dev.sensorManager
                }
            }
        }

        // Check if this device has a usable RGB LED and cache that result
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            for (light in dev.lightsManager.lights) {
                if (light.hasRgbControl()) {
                    context.hasRgbLed = true
                    break
                }
            }

            // Light.hasRgbControl() was totally broken prior to Android 14.
            // It always returned true because LIGHT_CAPABILITY_RGB was defined as 0,
            // so we will just guess RGB is supported if it's a PlayStation controller.
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    context.vendorId != VENDOR_SONY
            ) {
                context.hasRgbLed = false
            }
        }

        // Detect if the gamepad has Mode and Select buttons according to the Android key layouts.
        // We do this first because other codepaths below may override these defaults.
        val buttons =
            dev.hasKeys(
                KeyEvent.KEYCODE_BUTTON_MODE,
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BACK,
                0,
            )
        context.hasMode = buttons[0]
        context.hasSelect = buttons[1] || buttons[2]

        context.touchpadXRange = dev.getMotionRange(MotionEvent.AXIS_X, InputDevice.SOURCE_TOUCHPAD)
        context.touchpadYRange = dev.getMotionRange(MotionEvent.AXIS_Y, InputDevice.SOURCE_TOUCHPAD)
        context.touchpadPressureRange =
            dev.getMotionRange(MotionEvent.AXIS_PRESSURE, InputDevice.SOURCE_TOUCHPAD)

        context.leftStickXAxis = MotionEvent.AXIS_X
        context.leftStickYAxis = MotionEvent.AXIS_Y
        if (
            getMotionRangeForJoystickAxis(dev, context.leftStickXAxis) != null &&
                getMotionRangeForJoystickAxis(dev, context.leftStickYAxis) != null
        ) {
            // This is a gamepad
            context.hasJoystickAxes = true
        }

        // This is hack to deal with the Nvidia Shield's modifications that causes the DS4 clickpad
        // to work as a duplicate Select button instead of a unique button we can handle separately.
        context.isDualShockStandaloneTouchpad =
            context.vendorId == VENDOR_SONY && // Sony
                devName.endsWith(" Touchpad") &&
                dev.sources == (InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_MOUSE)

        val leftTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_LTRIGGER)
        val rightTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RTRIGGER)
        val brakeRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_BRAKE)
        val gasRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_GAS)
        val throttleRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_THROTTLE)
        if (leftTriggerRange != null && rightTriggerRange != null) {
            // Some controllers use LTRIGGER and RTRIGGER (like Ouya)
            context.leftTriggerAxis = MotionEvent.AXIS_LTRIGGER
            context.rightTriggerAxis = MotionEvent.AXIS_RTRIGGER
        } else if (brakeRange != null && gasRange != null) {
            // Others use GAS and BRAKE (like Moga)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE
            context.rightTriggerAxis = MotionEvent.AXIS_GAS
        } else if (brakeRange != null && throttleRange != null) {
            // Others use THROTTLE and BRAKE (like Xiaomi)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE
            context.rightTriggerAxis = MotionEvent.AXIS_THROTTLE
        } else {
            val rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX)
            val ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY)
            if (rxRange != null && ryRange != null && devName != null) {
                if (dev.vendorId == VENDOR_SONY) { // Sony
                    if (dev.hasKeys(KeyEvent.KEYCODE_BUTTON_C)[0]) {
                        LimeLog.info("Detected non-standard DualShock 4 mapping")
                        context.isNonStandardDualShock4 = true
                    } else {
                        LimeLog.info("Detected DualShock 4 (Linux standard mapping)")
                        context.usesLinuxGamepadStandardFaceButtons = true
                    }
                }

                if (context.isNonStandardDualShock4) {
                    // The old DS4 driver uses RX and RY for triggers
                    context.leftTriggerAxis = MotionEvent.AXIS_RX
                    context.rightTriggerAxis = MotionEvent.AXIS_RY

                    // DS4 has Select and Mode buttons (possibly mapped non-standard)
                    context.hasSelect = true
                    context.hasMode = true
                } else {
                    // If it's not a non-standard DS4 controller, it's probably an Xbox controller
                    // or other sane controller that uses RX and RY for right stick and Z and RZ for
                    // triggers.
                    context.rightStickXAxis = MotionEvent.AXIS_RX
                    context.rightStickYAxis = MotionEvent.AXIS_RY

                    // While it's likely that Z and RZ are triggers, we may have digital trigger
                    // buttons instead. We must check that we actually have Z and RZ axes before
                    // assigning them.
                    if (
                        getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z) != null &&
                            getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ) != null
                    ) {
                        context.leftTriggerAxis = MotionEvent.AXIS_Z
                        context.rightTriggerAxis = MotionEvent.AXIS_RZ
                    }
                }

                // Triggers always idle negative on axes that are centered at zero
                context.triggersIdleNegative = true
            }
        }

        if (context.rightStickXAxis == -1 && context.rightStickYAxis == -1) {
            val zRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z)
            val rzRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ)

            // Most other controllers use Z and RZ for the right stick
            if (zRange != null && rzRange != null) {
                context.rightStickXAxis = MotionEvent.AXIS_Z
                context.rightStickYAxis = MotionEvent.AXIS_RZ
            } else {
                val rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX)
                val ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY)

                // Try RX and RY now
                if (rxRange != null && ryRange != null) {
                    context.rightStickXAxis = MotionEvent.AXIS_RX
                    context.rightStickYAxis = MotionEvent.AXIS_RY
                }
            }
        }

        // Some devices have "hats" for d-pads
        val hatXRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_X)
        val hatYRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_Y)
        if (hatXRange != null && hatYRange != null) {
            context.hatXAxis = MotionEvent.AXIS_HAT_X
            context.hatYAxis = MotionEvent.AXIS_HAT_Y
        }

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            context.leftStickDeadzoneRadius = stickDeadzone.toFloat()
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            context.rightStickDeadzoneRadius = stickDeadzone.toFloat()
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            val ltRange = getMotionRangeForJoystickAxis(dev, context.leftTriggerAxis)
            val rtRange = getMotionRangeForJoystickAxis(dev, context.rightTriggerAxis)

            // It's important to have a valid deadzone so controller packet batching works properly
            context.triggerDeadzone =
                max(abs(checkNotNull(ltRange).flat), abs(checkNotNull(rtRange).flat))

            // For triggers without (valid) deadzones, we'll use 13% (around XInput's default)
            if (
                context.triggerDeadzone < DEFAULT_TRIGGER_DEADZONE ||
                    context.triggerDeadzone > MAX_TRIGGER_DEADZONE
            ) {
                context.triggerDeadzone = DEFAULT_TRIGGER_DEADZONE
            }
        }

        // The ADT-1 controller needs a similar fixup to the ASUS Gamepad
        if (dev.vendorId == VENDOR_GOOGLE && dev.productId == PRODUCT_ADT1) {
            context.backIsStart = true
            context.modeIsSelect = true
            context.triggerDeadzone = MAX_TRIGGER_DEADZONE
            context.hasSelect = true
            context.hasMode = false
        }

        context.ignoreBack = shouldIgnoreBack(dev)

        if (devName != null) {
            // For the Nexus Player (and probably other ATV devices), we should
            // use the back button as start since it doesn't have a start/menu button
            // on the controller
            if (devName.contains("ASUS Gamepad")) {
                val hasStartKey =
                    dev.hasKeys(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, 0)
                if (!hasStartKey[0] && !hasStartKey[1]) {
                    context.backIsStart = true
                    context.modeIsSelect = true
                    context.hasSelect = true
                    context.hasMode = false
                }

                // The ASUS Gamepad has triggers that sit far forward and are prone to false presses
                // so we increase the deadzone on them to minimize this
                context.triggerDeadzone = MAX_TRIGGER_DEADZONE
            } else if (devName.contains("SHIELD") || devName.contains("NVIDIA Controller")) {
                // SHIELD controllers will use small stick deadzones

                // The big Nvidia button on the Shield controllers acts like a Search button. It
                // summons the Google Assistant on the Shield TV. On my Pixel 4, it seems to do
                // nothing, so we can hijack it to act like a mode button.
                if (
                    devName.contains("NVIDIA Controller v01.03") ||
                        devName.contains("NVIDIA Controller v01.04")
                ) {
                    context.searchIsMode = true
                    context.hasMode = true
                }
            } else if (devName.contains("Razer Serval")) {
                // The Serval has a couple of unknown buttons that are start and select. It also has
                // a back button which we want to ignore since there's already a select button.
                context.isServal = true

                // Serval has Select and Mode buttons (possibly mapped non-standard)
                context.hasMode = true
                context.hasSelect = true
            } else if (devName == "Xbox Wireless Controller") {
                // The Xbox One S Bluetooth controller has some mappings that need fixing up.
                // However, Microsoft released a firmware update with no change to VID/PID
                // or device name that fixed the mappings for Android. Since there's
                // no good way to detect this, we'll use the presence of GAS/BRAKE axes
                // that were added in the latest firmware. If those are present, the only
                // required fixup is ignoring the select button.
                if (gasRange == null) {
                    context.isNonStandardXboxBtController = true

                    // Xbox One S has Select and Mode buttons (possibly mapped non-standard)
                    context.hasMode = true
                    context.hasSelect = true
                }
            }
        }

        // Thrustmaster Score A gamepad home button reports directly to android as
        // KEY_HOMEPAGE event on another event channel
        if (dev.vendorId == VENDOR_THRUSTMASTER && dev.productId == PRODUCT_SCORE_A) {
            context.hasMode = false
        }

        LimeLog.info(
            "Analog stick deadzone: ${context.leftStickDeadzoneRadius} ${context.rightStickDeadzoneRadius}"
        )
        LimeLog.info("Trigger deadzone: ${context.triggerDeadzone}")

        return context
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ReturnCount")
    private fun getContextForEvent(event: InputEvent): InputDeviceContext? {
        // Don't return a context if we're stopped
        if (stopped) {
            return null
        } else if (event.deviceId == 0) {
            // Unknown devices use the default context
            return defaultContext
        } else if (event.device == null) {
            // During device removal, sometimes we can get events after the
            // input device has been destroyed. In this case we'll see a
            // != 0 device ID but no device attached.
            return null
        }

        // HACK for https://issuetracker.google.com/issues/163120692
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
            if (event.deviceId == -1) {
                return defaultContext
            }
        }

        // Return the existing context if it exists
        var context = inputDeviceContexts.get(event.deviceId)
        if (context != null) {
            return context
        }

        // Otherwise create a new context
        context = createInputDeviceContextForDevice(event.device)
        if (mouseEmulationEnabled) {
            context.setMouseEmulation(true, false)
        }
        inputDeviceContexts.put(event.deviceId, context)

        return context
    }

    private fun maxByMagnitude(a: Byte, b: Byte): Byte {
        val absA = abs(a.toInt())
        val absB = abs(b.toInt())
        return if (absA > absB) {
            a
        } else {
            b
        }
    }

    private fun maxByMagnitude(a: Short, b: Short): Short {
        val absA = abs(a.toInt())
        val absB = abs(b.toInt())
        return if (absA > absB) {
            a
        } else {
            b
        }
    }

    private fun getActiveControllerMask(): Short {
        return if (prefConfig.multiController) {
            (currentControllers.toInt() or
                    initialControllers.toInt() or
                    (if (prefConfig.onscreenController) 1 else 0))
                .toShort()
        } else {
            // Only Player 1 is active with multi-controller disabled
            1
        }
    }

    // This must not be called on the main thread due to risk of ANRs!
    @SuppressLint("InlinedApi")
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun sendControllerBatteryPacket(context: InputDeviceContext) {
        val currentBatteryStatus: Int
        val currentBatteryCapacity: Float
        val device = context.inputDevice

        // Use the BatteryState object introduced in Android S, if it's available and present.
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                checkNotNull(device).batteryState.isPresent
        ) {
            currentBatteryStatus = device.batteryState.status
            currentBatteryCapacity = device.batteryState.capacity
        } else if (sceManager.isRecognizedDevice(device)) {
            // On the SHIELD Android TV, we can use a proprietary API to access battery/charge
            // state.
            // We will convert it to the same form used by BatteryState to share code.
            val batteryPercentage = sceManager.getBatteryPercentage(device)
            if (batteryPercentage < 0) {
                currentBatteryCapacity = Float.NaN
            } else {
                currentBatteryCapacity = batteryPercentage / PERCENT.toFloat()
            }

            val connectionType = sceManager.getConnectionType(device)
            val chargingState = sceManager.getChargingState(device)

            // We can make some assumptions about charge state based on the connection type
            if (
                connectionType == SceConnectionType.WIRED ||
                    connectionType == SceConnectionType.BOTH
            ) {
                if (batteryPercentage == PERCENT) {
                    currentBatteryStatus = BatteryState.STATUS_FULL
                } else if (chargingState == SceChargingState.NOT_CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_NOT_CHARGING
                } else {
                    currentBatteryStatus = BatteryState.STATUS_CHARGING
                }
            } else if (connectionType == SceConnectionType.WIRELESS) {
                if (chargingState == SceChargingState.CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_CHARGING
                } else {
                    currentBatteryStatus = BatteryState.STATUS_DISCHARGING
                }
            } else {
                // If connection type is unknown, just use the charge state
                if (batteryPercentage == PERCENT) {
                    currentBatteryStatus = BatteryState.STATUS_FULL
                } else if (chargingState == SceChargingState.NOT_CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_DISCHARGING
                } else if (chargingState == SceChargingState.CHARGING) {
                    currentBatteryStatus = BatteryState.STATUS_CHARGING
                } else {
                    currentBatteryStatus = BatteryState.STATUS_UNKNOWN
                }
            }
        } else {
            return
        }

        if (
            currentBatteryStatus != context.lastReportedBatteryStatus ||
                !areBatteryCapacitiesEqual(
                    currentBatteryCapacity,
                    context.lastReportedBatteryCapacity,
                )
        ) {
            val state: Byte
            val percentage: Byte

            when (currentBatteryStatus) {
                BatteryState.STATUS_UNKNOWN -> state = MoonBridge.LI_BATTERY_STATE_UNKNOWN
                BatteryState.STATUS_CHARGING -> state = MoonBridge.LI_BATTERY_STATE_CHARGING
                BatteryState.STATUS_DISCHARGING -> state = MoonBridge.LI_BATTERY_STATE_DISCHARGING
                BatteryState.STATUS_NOT_CHARGING -> state = MoonBridge.LI_BATTERY_STATE_NOT_CHARGING
                BatteryState.STATUS_FULL -> state = MoonBridge.LI_BATTERY_STATE_FULL
                else -> return
            }

            if (currentBatteryCapacity.isNaN()) {
                percentage = MoonBridge.LI_BATTERY_PERCENTAGE_UNKNOWN
            } else {
                percentage = (currentBatteryCapacity * PERCENT).toInt().toByte()
            }

            conn.sendControllerBatteryEvent(context.controllerNumber.toByte(), state, percentage)

            context.lastReportedBatteryStatus = currentBatteryStatus
            context.lastReportedBatteryCapacity = currentBatteryCapacity
        }
    }

    private fun sendControllerInputPacket(context: GenericControllerContext) {
        sendControllerInputPacket(context, SystemClock.uptimeMillis())
    }

    private fun sendControllerInputPacket(context: GenericControllerContext, eventTime: Long) {
        if (captured) {
            return
        }

        // Start and Select are held back from the PC on every pad while the chord is pending, and a
        // lone one for 60 ms on a pad that sends them as separate key events
        val chord =
            context.menuChord.update(context.inputMap, eventTime, true, context.holdsLonePress())
        scheduleMenuChordCheck(context)

        // Releasing a button the PC never saw changes nothing for it
        if (!chord.silent) {
            transmitControllerInput(context)
        }

        if (chord.fired) {
            notifyMenuChord()
        } else {
            // A chord that ended early reaches the PC as a short press of what the user pressed
            val tap = context.chordTapButtons(chord)
            if (tap != 0) {
                sendChordTap(context, tap)
            }
        }
    }

    private fun scheduleMenuChordCheck(context: GenericControllerContext) {
        mainThreadHandler.removeCallbacks(context.menuChordCheck)

        val deadline = context.menuChord.deadline()
        if (deadline != MenuChordDetector.NO_DEADLINE) {
            mainThreadHandler.postDelayed(
                context.menuChordCheck,
                max(0L, deadline - SystemClock.uptimeMillis()),
            )
        }
    }

    // The chord can complete on the UI thread (the scheduled check) or on a USB driver thread
    private fun notifyMenuChord() {
        val listener = menuChordListener ?: return

        if (Looper.myLooper() == Looper.getMainLooper()) {
            listener.onMenuChord()
        } else {
            mainThreadHandler.post { listener.onMenuChord() }
        }
    }

    private fun sendChordTap(context: GenericControllerContext, buttons: Int) {
        context.tapButtons = buttons
        transmitControllerInput(context)

        mainThreadHandler.postDelayed(
            {
                context.tapButtons = 0
                transmitControllerInput(context)
            },
            GUIDE_TAP_MS.toLong(),
        )
    }

    fun setMenuChordListener(listener: MenuChordListener?) {
        menuChordListener = listener
    }

    fun setChromeKeySink(sink: ChromeKeySink?) {
        chromeKeySink = sink
    }

    // BOREALIS: whether the USB driver holds a pad, which Android has no input device for (stream)
    fun hasUsbPad(): Boolean {
        return usbDeviceContexts.isNotEmpty()
    }

    override fun setCaptured(captured: Boolean) {
        if (captured == this.captured) {
            return
        }

        this.captured = captured
        if (captured) {
            releaseAllInput()
        } else {
            captureEnd.ended(SystemClock.uptimeMillis())
        }
    }

    // Leaves every pad, and the PC, with no button held and no stick moved. Input can come from
    // the UI thread and from the USB driver threads, so this and the packet send share a lock: a
    // packet cannot land after the neutral ones.
    private fun releaseAllInput() {
        synchronized(inputLock) {
            // The default context is the on-screen controller and devices Android reports without
            // an id
            releaseContextInput(defaultContext)
            inputDeviceContexts.forEachValue { releaseContextInput(it) }
            usbDeviceContexts.forEachValue { releaseContextInput(it) }

            // One neutral packet per controller the PC has heard of
            for (number in 0 until MAX_GAMEPADS) {
                if ((controllersWithInput and (1 shl number)) != 0) {
                    conn.sendControllerInput(
                        number.toShort(),
                        getActiveControllerMask(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0,
                    )
                }
            }
        }
    }

    private fun releaseContextInput(context: GenericControllerContext) {
        // Mouse buttons held by a pad in mouse mode would stay down on the PC
        if (context.mouseEmulationActive) {
            if ((context.mouseEmulationLastInputMap and ControllerPacket.A_FLAG) != 0) {
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
            }
            if ((context.mouseEmulationLastInputMap and ControllerPacket.B_FLAG) != 0) {
                conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
            }
        }

        context.inputMap = 0
        context.leftTrigger = 0
        context.rightTrigger = 0
        context.leftStickX = 0
        context.leftStickY = 0
        context.rightStickX = 0
        context.rightStickY = 0
        context.mouseEmulationLastInputMap = 0
        context.leftStickMotion.reset()
        context.rightStickMotion.reset()
        context.tapButtons = 0
        context.menuChord.reset()
        context.quitCombo.reset()
        mainThreadHandler.removeCallbacks(context.menuChordCheck)

        if (context is InputDeviceContext) {
            val deviceContext: InputDeviceContext = context
            deviceContext.emulatingButtonFlags = 0
            deviceContext.pendingExit = false

            // A gyro that is still reporting would keep the virtual pad moving
            if (deviceContext.gyroListener != null) {
                conn.sendControllerMotionEvent(
                    deviceContext.controllerNumber.toByte(),
                    MoonBridge.LI_MOTION_TYPE_GYRO,
                    0f,
                    0f,
                    0f,
                )
            }

            // A finger on the touchpad would stay down on the PC
            if (deviceContext.touchpadXRange != null && deviceContext.touchpadYRange != null) {
                conn.sendControllerTouchEvent(
                    deviceContext.controllerNumber.toByte(),
                    MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                    0,
                    0f,
                    0f,
                    0f,
                )
            }
        }
    }

    override fun setMouseEmulation(enabled: Boolean) {
        mouseEmulationEnabled = enabled

        // The pads, not the default context: that is the on-screen controller, which stays a
        // gamepad. One notice for the switch, however many pads it reaches
        var notify = true
        inputDeviceContexts.forEachValue { notify = applyMouseEmulation(it, enabled, notify) }
        usbDeviceContexts.forEachValue { notify = applyMouseEmulation(it, enabled, notify) }
    }

    override fun setPointerSpeed(factor: Float) {
        pointerSpeedFactor = factor
    }

    // Whether any pad emulates the mouse, which is what the menu's Gamepad mouse row shows. The
    // pads are the ones setMouseEmulation reaches; the default context stays a gamepad.
    val isMouseEmulationOnAnyPad: Boolean
        get() {
            inputDeviceContexts.forEachValue {
                if (it.mouseEmulationActive) {
                    return true
                }
            }
            usbDeviceContexts.forEachValue {
                if (it.mouseEmulationActive) {
                    return true
                }
            }
            return false
        }

    // Returns whether the next change should still show the notice
    private fun applyMouseEmulation(
        context: GenericControllerContext,
        enabled: Boolean,
        notify: Boolean,
    ): Boolean {
        if (context.mouseEmulationActive == enabled) {
            return notify
        }

        context.setMouseEmulation(enabled, notify)
        return false
    }

    private fun transmitControllerInput(originalContext: GenericControllerContext) {
        // Checked inside the lock: once the capture has its neutral packets out, nothing follows
        // them
        synchronized(inputLock) {
            if (!captured) {
                transmitControllerInputLocked(originalContext)
            }
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun transmitControllerInputLocked(originalContext: GenericControllerContext) {
        assignControllerNumberIfNeeded(originalContext)
        controllersWithInput =
            controllersWithInput or (1 shl originalContext.controllerNumber.toInt())

        // Take the context's controller number and fuse all inputs with the same number
        val controllerNumber = originalContext.controllerNumber
        var inputMap = 0
        var leftTrigger: Byte = 0
        var rightTrigger: Byte = 0
        var leftStickX: Short = 0
        var leftStickY: Short = 0
        var rightStickX: Short = 0
        var rightStickY: Short = 0

        // In order to properly handle controllers that are split into multiple devices,
        // we must aggregate all controllers with the same controller number into a single
        // device before we send it.
        inputDeviceContexts.forEachValue { context ->
            if (
                context.assignedControllerNumber &&
                    context.controllerNumber == controllerNumber &&
                    context.mouseEmulationActive == originalContext.mouseEmulationActive
            ) {
                inputMap = inputMap or context.sentInputMap()
                leftTrigger =
                    (leftTrigger.toInt() or
                            maxByMagnitude(leftTrigger, context.leftTrigger).toInt())
                        .toByte()
                rightTrigger =
                    (rightTrigger.toInt() or
                            maxByMagnitude(rightTrigger, context.rightTrigger).toInt())
                        .toByte()
                leftStickX =
                    (leftStickX.toInt() or maxByMagnitude(leftStickX, context.leftStickX).toInt())
                        .toShort()
                leftStickY =
                    (leftStickY.toInt() or maxByMagnitude(leftStickY, context.leftStickY).toInt())
                        .toShort()
                rightStickX =
                    (rightStickX.toInt() or
                            maxByMagnitude(rightStickX, context.rightStickX).toInt())
                        .toShort()
                rightStickY =
                    (rightStickY.toInt() or
                            maxByMagnitude(rightStickY, context.rightStickY).toInt())
                        .toShort()
            }
        }
        usbDeviceContexts.forEachValue { context ->
            if (
                context.assignedControllerNumber &&
                    context.controllerNumber == controllerNumber &&
                    context.mouseEmulationActive == originalContext.mouseEmulationActive
            ) {
                inputMap = inputMap or context.sentInputMap()
                leftTrigger =
                    (leftTrigger.toInt() or
                            maxByMagnitude(leftTrigger, context.leftTrigger).toInt())
                        .toByte()
                rightTrigger =
                    (rightTrigger.toInt() or
                            maxByMagnitude(rightTrigger, context.rightTrigger).toInt())
                        .toByte()
                leftStickX =
                    (leftStickX.toInt() or maxByMagnitude(leftStickX, context.leftStickX).toInt())
                        .toShort()
                leftStickY =
                    (leftStickY.toInt() or maxByMagnitude(leftStickY, context.leftStickY).toInt())
                        .toShort()
                rightStickX =
                    (rightStickX.toInt() or
                            maxByMagnitude(rightStickX, context.rightStickX).toInt())
                        .toShort()
                rightStickY =
                    (rightStickY.toInt() or
                            maxByMagnitude(rightStickY, context.rightStickY).toInt())
                        .toShort()
            }
        }
        if (defaultContext.controllerNumber == controllerNumber) {
            inputMap = inputMap or defaultContext.sentInputMap()
            leftTrigger =
                (leftTrigger.toInt() or
                        maxByMagnitude(leftTrigger, defaultContext.leftTrigger).toInt())
                    .toByte()
            rightTrigger =
                (rightTrigger.toInt() or
                        maxByMagnitude(rightTrigger, defaultContext.rightTrigger).toInt())
                    .toByte()
            leftStickX =
                (leftStickX.toInt() or
                        maxByMagnitude(leftStickX, defaultContext.leftStickX).toInt())
                    .toShort()
            leftStickY =
                (leftStickY.toInt() or
                        maxByMagnitude(leftStickY, defaultContext.leftStickY).toInt())
                    .toShort()
            rightStickX =
                (rightStickX.toInt() or
                        maxByMagnitude(rightStickX, defaultContext.rightStickX).toInt())
                    .toShort()
            rightStickY =
                (rightStickY.toInt() or
                        maxByMagnitude(rightStickY, defaultContext.rightStickY).toInt())
                    .toShort()
        }

        if (originalContext.mouseEmulationActive) {
            val changedMask = inputMap xor originalContext.mouseEmulationLastInputMap

            val aDown = (inputMap and ControllerPacket.A_FLAG) != 0
            val bDown = (inputMap and ControllerPacket.B_FLAG) != 0

            originalContext.mouseEmulationLastInputMap = inputMap

            if ((changedMask and ControllerPacket.A_FLAG) != 0) {
                if (aDown) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_LEFT)
                } else {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_LEFT)
                }
            }
            if ((changedMask and ControllerPacket.B_FLAG) != 0) {
                if (bDown) {
                    conn.sendMouseButtonDown(MouseButtonPacket.BUTTON_RIGHT)
                } else {
                    conn.sendMouseButtonUp(MouseButtonPacket.BUTTON_RIGHT)
                }
            }
            if ((changedMask and ControllerPacket.UP_FLAG) != 0) {
                if ((inputMap and ControllerPacket.UP_FLAG) != 0) {
                    conn.sendMouseScroll(1)
                }
            }
            if ((changedMask and ControllerPacket.DOWN_FLAG) != 0) {
                if ((inputMap and ControllerPacket.DOWN_FLAG) != 0) {
                    conn.sendMouseScroll(-1)
                }
            }
            if ((changedMask and ControllerPacket.RIGHT_FLAG) != 0) {
                if ((inputMap and ControllerPacket.RIGHT_FLAG) != 0) {
                    conn.sendMouseHScroll(1)
                }
            }
            if ((changedMask and ControllerPacket.LEFT_FLAG) != 0) {
                if ((inputMap and ControllerPacket.LEFT_FLAG) != 0) {
                    conn.sendMouseHScroll(-1)
                }
            }

            conn.sendControllerInput(
                controllerNumber,
                getActiveControllerMask(),
                0,
                0,
                0,
                0,
                0,
                0,
                0,
            )
        } else {
            conn.sendControllerInput(
                controllerNumber,
                getActiveControllerMask(),
                inputMap,
                leftTrigger,
                rightTrigger,
                leftStickX,
                leftStickY,
                rightStickX,
                rightStickY,
            )
        }
    }

    // Return a valid keycode, -2 to consume, or -1 to not consume the event
    // Device MAY BE NULL
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress(
        "ComplexCondition",
        "CyclomaticComplexMethod",
        "LongMethod",
        "ReturnCount",
    )
    private fun handleRemapping(context: InputDeviceContext, event: KeyEvent): Int {
        // Don't capture the back button if configured
        if (context.ignoreBack) {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                return REMAP_IGNORE
            }
        }

        // If we know this gamepad has a share button and receive an unmapped
        // KEY_RECORD event, report that as a share button press.
        if (context.hasShare) {
            if (event.keyCode == KeyEvent.KEYCODE_UNKNOWN && event.scanCode == KEY_RECORD) {
                return KeyEvent.KEYCODE_MEDIA_RECORD
            }
        }

        // The Shield's key layout files map the DualShock 4 clickpad button to
        // BUTTON_SELECT instead of something sane like BUTTON_1 as the standard AOSP
        // mapping does. If we get a button from a Sony device reported as BUTTON_SELECT
        // that matches the keycode used by hid-sony for the clickpad or it's from the
        // separate touchpad input device, remap it to BUTTON_1 to match the current AOSP
        // layout and trigger our touchpad button logic.
        if (
            context.vendorId == VENDOR_SONY &&
                event.keyCode == KeyEvent.KEYCODE_BUTTON_SELECT &&
                (event.scanCode == BTN_THUMBL || context.isDualShockStandaloneTouchpad)
        ) {
            return KeyEvent.KEYCODE_BUTTON_1
        }

        // Override mode button for 8BitDo controllers
        if (context.vendorId == VENDOR_8BITDO && event.scanCode == BTN_C) {
            return KeyEvent.KEYCODE_BUTTON_MODE
        }

        // This mapping was adding in Android 10, then changed based on
        // kernel changes (adding hid-nintendo) in Android 11. If we're
        // on anything newer than Pie, just use the built-in mapping.
        if (
            (context.vendorId == VENDOR_NINTENDO &&
                context.productId == PRODUCT_SWITCH_PRO &&
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) || // Switch Pro controller
                (context.vendorId == VENDOR_HORI && context.productId == PRODUCT_HORIPAD_SWITCH)
        ) { // HORIPAD for Switch
            when (event.scanCode) {
                BTN_SOUTH -> return KeyEvent.KEYCODE_BUTTON_A
                BTN_EAST -> return KeyEvent.KEYCODE_BUTTON_B
                BTN_C -> return KeyEvent.KEYCODE_BUTTON_X
                BTN_NORTH -> return KeyEvent.KEYCODE_BUTTON_Y
                BTN_WEST -> return KeyEvent.KEYCODE_BUTTON_L1
                BTN_Z -> return KeyEvent.KEYCODE_BUTTON_R1
                BTN_TL -> return KeyEvent.KEYCODE_BUTTON_L2
                BTN_TR -> return KeyEvent.KEYCODE_BUTTON_R2
                BTN_TL2 -> return KeyEvent.KEYCODE_BUTTON_SELECT
                BTN_TR2 -> return KeyEvent.KEYCODE_BUTTON_START
                BTN_SELECT -> return KeyEvent.KEYCODE_BUTTON_THUMBL
                BTN_START -> return KeyEvent.KEYCODE_BUTTON_THUMBR
                BTN_THUMBL -> return KeyEvent.KEYCODE_BUTTON_MODE
            }
        }

        if (context.usesLinuxGamepadStandardFaceButtons) {
            // Android's Generic.kl swaps BTN_NORTH and BTN_WEST
            when (event.scanCode) {
                BTN_SOUTH -> return KeyEvent.KEYCODE_BUTTON_A
                BTN_EAST -> return KeyEvent.KEYCODE_BUTTON_B
                BTN_NORTH -> return KeyEvent.KEYCODE_BUTTON_Y
                BTN_WEST -> return KeyEvent.KEYCODE_BUTTON_X
            }
        }

        if (context.isNonStandardDualShock4) {
            when (event.scanCode) {
                BTN_SOUTH -> return KeyEvent.KEYCODE_BUTTON_X
                BTN_EAST -> return KeyEvent.KEYCODE_BUTTON_A
                BTN_C -> return KeyEvent.KEYCODE_BUTTON_B
                BTN_NORTH -> return KeyEvent.KEYCODE_BUTTON_Y
                BTN_WEST -> return KeyEvent.KEYCODE_BUTTON_L1
                BTN_Z -> return KeyEvent.KEYCODE_BUTTON_R1
                /*
                **** Using analog triggers instead ****
                case 310:
                    return KeyEvent.KEYCODE_BUTTON_L2;
                case 311:
                    return KeyEvent.KEYCODE_BUTTON_R2;
                */
                BTN_TL2 -> return KeyEvent.KEYCODE_BUTTON_SELECT
                BTN_TR2 -> return KeyEvent.KEYCODE_BUTTON_START
                BTN_SELECT -> return KeyEvent.KEYCODE_BUTTON_THUMBL
                BTN_START -> return KeyEvent.KEYCODE_BUTTON_THUMBR
                BTN_MODE -> return KeyEvent.KEYCODE_BUTTON_MODE
                else -> return REMAP_CONSUME
            }
        } else if (context.isServal && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            // If this is a Serval controller sending an unknown key code, it's probably
            // the start and select buttons
            when (event.scanCode) {
                BTN_SELECT -> return KeyEvent.KEYCODE_BUTTON_SELECT
                BTN_START -> return KeyEvent.KEYCODE_BUTTON_START
            }
        } else if (context.isNonStandardXboxBtController) {
            when (event.scanCode) {
                BTN_C -> return KeyEvent.KEYCODE_BUTTON_X
                BTN_NORTH -> return KeyEvent.KEYCODE_BUTTON_Y
                BTN_WEST -> return KeyEvent.KEYCODE_BUTTON_L1
                BTN_Z -> return KeyEvent.KEYCODE_BUTTON_R1
                BTN_TL -> return KeyEvent.KEYCODE_BUTTON_SELECT
                BTN_TR -> return KeyEvent.KEYCODE_BUTTON_START
                BTN_TL2 -> return KeyEvent.KEYCODE_BUTTON_THUMBL
                BTN_TR2 -> return KeyEvent.KEYCODE_BUTTON_THUMBR
                KEY_MENU -> return KeyEvent.KEYCODE_BUTTON_MODE
                else -> {
                    // Other buttons are mapped correctly
                }
            }

            // The Xbox button is sent as MENU
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                return KeyEvent.KEYCODE_BUTTON_MODE
            }
        } else if (
            context.vendorId == VENDOR_ASUS && // ASUS
                (context.productId == PRODUCT_KUNAI_USB || // Kunai - USB
                    context.productId == PRODUCT_KUNAI_BLUETOOTH) // Kunai - Bluetooth
        ) {
            // ROG Kunai has special M1-M4 buttons that are accessible via the
            // joycon-style detachable controllers that we should map to Start
            // and Select.
            when (event.scanCode) {
                KUNAI_START_1,
                KUNAI_START_2 -> return KeyEvent.KEYCODE_BUTTON_START
                KUNAI_SELECT_1,
                KUNAI_SELECT_2 -> return KeyEvent.KEYCODE_BUTTON_SELECT
            }
        }

        if (
            context.hatXAxis == -1 &&
                context.hatYAxis == -1 &&
                /* FIXME There's no good way to know for sure if xpad is bound
                to this device, so we won't use the name to validate if these
                scancodes should be mapped to DPAD

                context.isXboxController &&
                */
                event.keyCode == KeyEvent.KEYCODE_UNKNOWN
        ) {
            // If there's not a proper Xbox controller mapping, we'll translate the raw d-pad
            // scan codes into proper key codes
            when (event.scanCode) {
                BTN_TRIGGER_HAPPY1 -> return KeyEvent.KEYCODE_DPAD_LEFT
                BTN_TRIGGER_HAPPY2 -> return KeyEvent.KEYCODE_DPAD_RIGHT
                BTN_TRIGGER_HAPPY3 -> return KeyEvent.KEYCODE_DPAD_UP
                BTN_TRIGGER_HAPPY4 -> return KeyEvent.KEYCODE_DPAD_DOWN
            }
        }

        // Past here we can fixup the keycode and potentially trigger
        // another special case so we need to remember what keycode we're using
        var keyCode = event.keyCode

        // This is a hack for (at least) the "Tablet Remote" app
        // which sends BACK with META_ALT_ON instead of KEYCODE_BUTTON_B
        if (
            keyCode == KeyEvent.KEYCODE_BACK &&
                !event.hasNoModifiers() &&
                (event.flags and KeyEvent.FLAG_SOFT_KEYBOARD) != 0
        ) {
            keyCode = KeyEvent.KEYCODE_BUTTON_B
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_START || keyCode == KeyEvent.KEYCODE_MENU) {
            // Ensure that we never use back as start if we have a real start
            context.backIsStart = false
        } else if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            // Don't use mode as select if we have a select
            context.modeIsSelect = false
        } else if (context.backIsStart && keyCode == KeyEvent.KEYCODE_BACK) {
            // Emulate the start button with back
            return KeyEvent.KEYCODE_BUTTON_START
        } else if (context.modeIsSelect && keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            // Emulate the select button with mode
            return KeyEvent.KEYCODE_BUTTON_SELECT
        } else if (context.searchIsMode && keyCode == KeyEvent.KEYCODE_SEARCH) {
            // Emulate the mode button with search
            return KeyEvent.KEYCODE_BUTTON_MODE
        }

        return keyCode
    }

    private fun handleFlipFaceButtons(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }
    }

    private fun populateCachedVector(x: Float, y: Float): Vector2d {
        // Reinitialize our cached Vector2d object
        inputVector.initialize(x, y)
        return inputVector
    }

    private fun handleDeadZone(stickVector: Vector2d, deadzoneRadius: Float) {
        if (stickVector.magnitude <= deadzoneRadius) {
            // Deadzone
            stickVector.initialize(0f, 0f)
        }

        // We're not normalizing here because we let the computer handle the deadzones.
        // Normalizing can make the deadzones larger than they should be after the computer also
        // evaluates the deadzone.
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod",
        "LongParameterList",
    )
    private fun handleAxisSet(
        context: InputDeviceContext,
        lsX: Float,
        lsY: Float,
        rsX: Float,
        rsY: Float,
        lt: Float,
        rt: Float,
        hatX: Float,
        hatY: Float,
    ) {
        var leftTriggerValue = lt
        var rightTriggerValue = rt

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            val leftStickVector = populateCachedVector(lsX, lsY)

            handleDeadZone(leftStickVector, context.leftStickDeadzoneRadius)

            context.leftStickX = (leftStickVector.x * STICK_MAX).toInt().toShort()
            context.leftStickY = (-leftStickVector.y * STICK_MAX).toInt().toShort()
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            val rightStickVector = populateCachedVector(rsX, rsY)

            handleDeadZone(rightStickVector, context.rightStickDeadzoneRadius)

            context.rightStickX = (rightStickVector.x * STICK_MAX).toInt().toShort()
            context.rightStickY = (-rightStickVector.y * STICK_MAX).toInt().toShort()
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            // Android sends an initial 0 value for trigger axes even if the trigger
            // should be negative when idle. After the first touch, the axes will go back
            // to normal behavior, so ignore triggersIdleNegative for each trigger until
            // first touch.
            if (leftTriggerValue != 0f) {
                context.leftTriggerAxisUsed = true
            }
            if (rightTriggerValue != 0f) {
                context.rightTriggerAxisUsed = true
            }
            if (context.triggersIdleNegative) {
                if (context.leftTriggerAxisUsed) {
                    leftTriggerValue = (leftTriggerValue + 1) / 2
                }
                if (context.rightTriggerAxisUsed) {
                    rightTriggerValue = (rightTriggerValue + 1) / 2
                }
            }

            if (leftTriggerValue <= context.triggerDeadzone) {
                leftTriggerValue = 0f
            }
            if (rightTriggerValue <= context.triggerDeadzone) {
                rightTriggerValue = 0f
            }

            context.leftTrigger = (leftTriggerValue * TRIGGER_MAX).toInt().toByte()
            context.rightTrigger = (rightTriggerValue * TRIGGER_MAX).toInt().toByte()
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            context.inputMap =
                context.inputMap and
                    (ControllerPacket.LEFT_FLAG or ControllerPacket.RIGHT_FLAG).inv()
            if (hatX < -HAT_THRESHOLD) {
                context.inputMap = context.inputMap or ControllerPacket.LEFT_FLAG
                context.hatXAxisUsed = true
            } else if (hatX > HAT_THRESHOLD) {
                context.inputMap = context.inputMap or ControllerPacket.RIGHT_FLAG
                context.hatXAxisUsed = true
            }

            context.inputMap =
                context.inputMap and (ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG).inv()
            if (hatY < -HAT_THRESHOLD) {
                context.inputMap = context.inputMap or ControllerPacket.UP_FLAG
                context.hatYAxisUsed = true
            } else if (hatY > HAT_THRESHOLD) {
                context.inputMap = context.inputMap or ControllerPacket.DOWN_FLAG
                context.hatYAxisUsed = true
            }
        }

        sendControllerInputPacket(context)
    }

    // Normalize the given raw float value into a 0.0-1.0f range
    private fun normalizeRawValueWithRange(value: Float, range: InputDevice.MotionRange): Float {
        var normalized = max(value, range.min)
        normalized = min(normalized, range.max)

        normalized -= range.min

        return normalized / range.range
    }

    private fun sendTouchpadEventForPointer(
        context: InputDeviceContext,
        event: MotionEvent,
        touchType: Byte,
        pointerIndex: Int,
    ): Boolean {
        val normalizedX =
            normalizeRawValueWithRange(
                event.getX(pointerIndex),
                checkNotNull(context.touchpadXRange),
            )
        val normalizedY =
            normalizeRawValueWithRange(
                event.getY(pointerIndex),
                checkNotNull(context.touchpadYRange),
            )
        val pressureRange = context.touchpadPressureRange
        val normalizedPressure =
            if (pressureRange != null) {
                normalizeRawValueWithRange(event.getPressure(pointerIndex), pressureRange)
            } else {
                0f
            }

        return conn.sendControllerTouchEvent(
            context.controllerNumber.toByte(),
            touchType,
            event.getPointerId(pointerIndex),
            normalizedX,
            normalizedY,
            normalizedPressure,
        ) != MoonBridge.LI_ERR_UNSUPPORTED
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun tryHandleTouchpadEvent(event: MotionEvent): Boolean {
        // Bail if this is not a touchpad or mouse event
        if (
            event.source != InputDevice.SOURCE_TOUCHPAD && event.source != InputDevice.SOURCE_MOUSE
        ) {
            return false
        }

        // Only get a context if one already exists. We want to ensure we don't report non-gamepads.
        val context = inputDeviceContexts.get(event.deviceId) ?: return false

        // The touchpad of a pad the chrome owns reaches neither the controller nor the mouse
        if (captured) {
            return true
        }

        // When we're working with a mouse source instead of a touchpad, we're quite limited in
        // what useful input we can provide via the controller API. The ABS_X/ABS_Y values are
        // screen coordinates rather than touchpad coordinates. For now, we will just support
        // the clickpad button and nothing else.
        if (event.source == InputDevice.SOURCE_MOUSE) {
            // Unlike the touchpad where down and up refer to individual touches on the touchpad,
            // down and up on a mouse indicates the state of the left mouse button.
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG
                    sendControllerInputPacket(context)
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
                    sendControllerInputPacket(context)
                }
                else -> {}
            }

            return !prefConfig.gamepadTouchpadAsMouse
        }

        val touchType: Byte
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> touchType = MoonBridge.LI_TOUCH_EVENT_DOWN
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> {
                if ((event.flags and MotionEvent.FLAG_CANCELED) != 0) {
                    touchType = MoonBridge.LI_TOUCH_EVENT_CANCEL
                } else {
                    touchType = MoonBridge.LI_TOUCH_EVENT_UP
                }
            }
            MotionEvent.ACTION_MOVE -> touchType = MoonBridge.LI_TOUCH_EVENT_MOVE
            MotionEvent.ACTION_CANCEL ->
                // ACTION_CANCEL applies to *all* pointers in the gesture, so it maps to CANCEL_ALL
                // rather than CANCEL. For a single pointer cancellation, that's indicated via
                // FLAG_CANCELED on a ACTION_POINTER_UP.
                // https://developer.android.com/develop/ui/views/touch-and-input/gestures/multi
                touchType = MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL
            MotionEvent.ACTION_BUTTON_PRESS -> {
                if (event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG
                    sendControllerInputPacket(context)
                    // Report as unhandled event to trigger mouse handling
                    return !prefConfig.gamepadTouchpadAsMouse
                }
                return false
            }
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (event.actionButton == MotionEvent.BUTTON_PRIMARY) {
                    context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
                    sendControllerInputPacket(context)
                    // Report as unhandled event to trigger mouse handling
                    return !prefConfig.gamepadTouchpadAsMouse
                }
                return false
            }
            else -> return false
        }

        // Bail if the user wants gamepad touchpads to control the mouse
        //
        // NB: We do this after processing ACTION_BUTTON_PRESS and ACTION_BUTTON_RELEASE
        // because we want to still send the touchpad button via the gamepad even when
        // configured to use the touchpad for mouse control.
        if (prefConfig.gamepadTouchpadAsMouse) {
            return false
        }

        // If we don't have X and Y ranges, we can't process this event
        if (context.touchpadXRange == null || context.touchpadYRange == null) {
            return false
        }

        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Move events may impact all active pointers
            for (i in 0 until event.pointerCount) {
                if (!sendTouchpadEventForPointer(context, event, touchType, i)) {
                    // Controller touch events are not supported by the host
                    return false
                }
            }
            return true
        } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
            // Cancel impacts all active pointers
            return conn.sendControllerTouchEvent(
                context.controllerNumber.toByte(),
                MoonBridge.LI_TOUCH_EVENT_CANCEL_ALL,
                0,
                0f,
                0f,
                0f,
            ) != MoonBridge.LI_ERR_UNSUPPORTED
        } else {
            // Down and Up events impact the action index pointer
            return sendTouchpadEventForPointer(context, event, touchType, event.actionIndex)
        }
    }

    fun handleMotionEvent(event: MotionEvent): Boolean {
        val context = getContextForEvent(event) ?: return true

        var lsX = 0f
        var lsY = 0f
        var rsX = 0f
        var rsY = 0f
        var rt = 0f
        var lt = 0f
        var hatX = 0f
        var hatY = 0f

        // We purposefully ignore the historical values in the motion event as it makes
        // the controller feel sluggish for some users.

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            lsX = event.getAxisValue(context.leftStickXAxis)
            lsY = event.getAxisValue(context.leftStickYAxis)
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            rsX = event.getAxisValue(context.rightStickXAxis)
            rsY = event.getAxisValue(context.rightStickYAxis)
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            lt = event.getAxisValue(context.leftTriggerAxis)
            rt = event.getAxisValue(context.rightTriggerAxis)
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        }

        handleAxisSet(context, lsX, lsY, rsX, rsY, lt, rt, hatX, hatY)

        return true
    }

    // The move a stick makes over the time since the last tick; the motion keeps what is under a
    // pixel
    private fun emulatedMouseDelta(
        motion: PointerMotion,
        x: Short,
        y: Short,
        elapsedMs: Long,
        precision: Boolean,
    ): PixelDelta? {
        if (captured) {
            motion.reset()
            return null
        }
        return motion.step(
            x,
            y,
            elapsedMs,
            PointerSpeed(prefConfig.gamepadMouseSpeed, pointerSpeedFactor, precision),
        )
    }

    private fun sendEmulatedMouseMove(
        motion: PointerMotion,
        x: Short,
        y: Short,
        elapsedMs: Long,
        precision: Boolean,
    ) {
        val delta = emulatedMouseDelta(motion, x, y, elapsedMs, precision)
        if (delta != null && (delta.x != 0 || delta.y != 0)) {
            conn.sendMouseMove(delta.x.toShort(), (-delta.y).toShort())
        }
    }

    private fun sendEmulatedMouseScroll(
        motion: PointerMotion,
        x: Short,
        y: Short,
        elapsedMs: Long,
        precision: Boolean,
    ) {
        val delta = emulatedMouseDelta(motion, x, y, elapsedMs, precision) ?: return
        if (delta.y != 0) {
            conn.sendMouseHighResScroll(delta.y.toShort())
        }
        if (delta.x != 0) {
            conn.sendMouseHighResHScroll(delta.x.toShort())
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasDualAmplitudeControlledRumbleVibrators(vm: VibratorManager): Boolean {
        val vibratorIds = vm.vibratorIds

        // There must be exactly 2 vibrators on this device
        if (vibratorIds.size != 2) {
            return false
        }

        // Both vibrators must have amplitude control
        for (vid in vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false
            }
        }

        return true
    }

    // This must only be called if hasDualAmplitudeControlledRumbleVibrators() is true!
    @RequiresApi(Build.VERSION_CODES.S)
    private fun rumbleDualVibrators(
        vm: VibratorManager,
        lowFreqMotor: Short,
        highFreqMotor: Short,
    ) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        val highFreqAmplitude = (highFreqMotor.toInt() shr 8) and 0xFF
        val lowFreqAmplitude = (lowFreqMotor.toInt() shr 8) and 0xFF

        // If they're both zero, we can just call cancel().
        if (lowFreqAmplitude == 0 && highFreqAmplitude == 0) {
            vm.cancel()
            return
        }

        // There's no documentation that states that vibrators for FF_RUMBLE input devices will
        // always be enumerated in this order, but it seems consistent between Xbox Series X (USB),
        // PS3 (USB), and PS4 (USB+BT) controllers on Android 12 Beta 3.
        val vibratorIds = vm.vibratorIds
        val vibratorAmplitudes = intArrayOf(highFreqAmplitude, lowFreqAmplitude)

        val combo = CombinedVibration.startParallel()

        for (i in vibratorIds.indices) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(
                    vibratorIds[i],
                    VibrationEffect.createOneShot(RUMBLE_DURATION_MS, vibratorAmplitudes[i]),
                )
            }
        }

        val vibrationAttributes = VibrationAttributes.Builder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA)
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun hasQuadAmplitudeControlledRumbleVibrators(vm: VibratorManager): Boolean {
        val vibratorIds = vm.vibratorIds

        // There must be exactly 4 vibrators on this device
        if (vibratorIds.size != QUAD_VIBRATOR_COUNT) {
            return false
        }

        // All vibrators must have amplitude control
        for (vid in vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false
            }
        }

        return true
    }

    // This must only be called if hasQuadAmplitudeControlledRumbleVibrators() is true!
    @RequiresApi(Build.VERSION_CODES.S)
    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ComplexCondition")
    private fun rumbleQuadVibrators(
        vm: VibratorManager,
        lowFreqMotor: Short,
        highFreqMotor: Short,
        leftTrigger: Short,
        rightTrigger: Short,
    ) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        val highFreqAmplitude = (highFreqMotor.toInt() shr 8) and 0xFF
        val lowFreqAmplitude = (lowFreqMotor.toInt() shr 8) and 0xFF
        val leftTriggerAmplitude = (leftTrigger.toInt() shr 8) and 0xFF
        val rightTriggerAmplitude = (rightTrigger.toInt() shr 8) and 0xFF

        // If they're all zero, we can just call cancel().
        if (
            lowFreqAmplitude == 0 &&
                highFreqAmplitude == 0 &&
                leftTriggerAmplitude == 0 &&
                rightTriggerAmplitude == 0
        ) {
            vm.cancel()
            return
        }

        // This is a guess based upon the behavior of FF_RUMBLE, but untested due to lack of Linux
        // support for trigger rumble!
        val vibratorIds = vm.vibratorIds
        val vibratorAmplitudes =
            intArrayOf(
                highFreqAmplitude,
                lowFreqAmplitude,
                leftTriggerAmplitude,
                rightTriggerAmplitude,
            )

        val combo = CombinedVibration.startParallel()

        for (i in vibratorIds.indices) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(
                    vibratorIds[i],
                    VibrationEffect.createOneShot(RUMBLE_DURATION_MS, vibratorAmplitudes[i]),
                )
            }
        }

        val vibrationAttributes = VibrationAttributes.Builder()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA)
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build())
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("DEPRECATION")
    private fun rumbleSingleVibrator(
        vibrator: Vibrator,
        lowFreqMotor: Short,
        highFreqMotor: Short,
    ) {
        // Since we can only use a single amplitude value, compute the desired amplitude
        // by taking 80% of the big motor and 33% of the small motor, then capping to 255.
        // NB: This value is now 0-255 as required by VibrationEffect.
        val lowFreqMotorMSB = (lowFreqMotor.toInt() shr 8) and 0xFF
        val highFreqMotorMSB = (highFreqMotor.toInt() shr 8) and 0xFF
        val simulatedAmplitude =
            min(255, ((lowFreqMotorMSB * 0.80) + (highFreqMotorMSB * 0.33)).toInt())

        if (simulatedAmplitude == 0) {
            // This case is easy - just cancel the current effect and get out.
            // NB: We cannot simply check lowFreqMotor == highFreqMotor == 0
            // because our simulatedAmplitude could be 0 even though our inputs
            // are not (ex: lowFreqMotor == 0 && highFreqMotor == 1).
            vibrator.cancel()
            return
        }

        // Attempt to use amplitude-based control if we're on Oreo and the device
        // supports amplitude-based vibration control.
        if (vibrator.hasAmplitudeControl()) {
            val effect = VibrationEffect.createOneShot(RUMBLE_DURATION_MS, simulatedAmplitude)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val vibrationAttributes =
                    VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()
                vibrator.vibrate(effect, vibrationAttributes)
            } else {
                val audioAttributes =
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()
                vibrator.vibrate(effect, audioAttributes)
            }
            return
        }

        // If we reach this point, we don't have amplitude controls available, so
        // we must emulate it by PWMing the vibration. Ick.
        val pwmPeriod = 20L
        val onTime = ((simulatedAmplitude / 255.0) * pwmPeriod).toLong()
        val offTime = pwmPeriod - onTime
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val vibrationAttributes =
                VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_MEDIA).build()
            vibrator.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, onTime, offTime), 0),
                vibrationAttributes,
            )
        } else {
            val audioAttributes =
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build()
            vibrator.vibrate(longArrayOf(0, onTime, offTime), 0, audioAttributes)
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress(
        "ComplexCondition",
        "CyclomaticComplexMethod",
        "LongMethod",
    )
    fun handleRumble(controllerNumber: Short, lowFreqMotor: Short, highFreqMotor: Short) {
        var foundMatchingDevice = false
        var vibrated = false

        if (stopped) {
            return
        }

        inputDeviceContexts.forEachValue { deviceContext ->
            if (deviceContext.controllerNumber == controllerNumber) {
                foundMatchingDevice = true

                deviceContext.lowFreqMotor = lowFreqMotor
                deviceContext.highFreqMotor = highFreqMotor

                val contextVibratorManager = deviceContext.vibratorManager
                val contextVibrator = deviceContext.vibrator

                // Prefer the documented Android 12 rumble API which can handle dual vibrators on
                // PS/Xbox controllers
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && contextVibratorManager != null
                ) {
                    vibrated = true
                    if (deviceContext.quadVibrators) {
                        rumbleQuadVibrators(
                            contextVibratorManager,
                            deviceContext.lowFreqMotor,
                            deviceContext.highFreqMotor,
                            deviceContext.leftTriggerMotor,
                            deviceContext.rightTriggerMotor,
                        )
                    } else {
                        rumbleDualVibrators(
                            contextVibratorManager,
                            deviceContext.lowFreqMotor,
                            deviceContext.highFreqMotor,
                        )
                    }
                } else if (
                    sceManager.rumble(
                        deviceContext.inputDevice,
                        deviceContext.lowFreqMotor.toInt(),
                        deviceContext.highFreqMotor.toInt(),
                    )
                ) {
                    // On Shield devices, we can use their special API to rumble Shield controllers
                    vibrated = true
                } else if (contextVibrator != null) {
                    // If all else fails, we have to try the old Vibrator API
                    vibrated = true
                    rumbleSingleVibrator(
                        contextVibrator,
                        deviceContext.lowFreqMotor,
                        deviceContext.highFreqMotor,
                    )
                }
            }
        }

        usbDeviceContexts.forEachValue { deviceContext ->
            if (deviceContext.controllerNumber == controllerNumber) {
                vibrated = true
                foundMatchingDevice = true
                checkNotNull(deviceContext.device).rumble(lowFreqMotor, highFreqMotor)
            }
        }

        // We may decide to rumble the device for player 1
        if (controllerNumber.toInt() == 0) {
            // If we didn't find a matching device, it must be the on-screen
            // controls that triggered the rumble. Vibrate the device if
            // the user has requested that behavior.
            if (
                !foundMatchingDevice &&
                    prefConfig.onscreenController &&
                    !prefConfig.onlyL3R3 &&
                    prefConfig.vibrateOsc
            ) {
                rumbleSingleVibrator(deviceVibrator, lowFreqMotor, highFreqMotor)
            } else if (foundMatchingDevice && !vibrated && prefConfig.vibrateFallbackToDevice) {
                // We found a device to vibrate but it didn't have rumble support. The user
                // has requested us to vibrate the device in this case.

                // We cast the unsigned short value to a signed int before multiplying by
                // the preferred strength. The resulting value is capped at 65534 before
                // we cast it back to a short so it doesn't go above 100%.
                val lowFreqMotorAdjusted =
                    min(
                            (((lowFreqMotor.toInt() and 0xffff) *
                                prefConfig.vibrateFallbackToDeviceStrength) / 100),
                            Short.MAX_VALUE * 2,
                        )
                        .toShort()
                val highFreqMotorAdjusted =
                    min(
                            (((highFreqMotor.toInt() and 0xffff) *
                                prefConfig.vibrateFallbackToDeviceStrength) / 100),
                            Short.MAX_VALUE * 2,
                        )
                        .toShort()

                rumbleSingleVibrator(deviceVibrator, lowFreqMotorAdjusted, highFreqMotorAdjusted)
            }
        }
    }

    fun handleRumbleTriggers(controllerNumber: Short, leftTrigger: Short, rightTrigger: Short) {
        if (stopped) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            inputDeviceContexts.forEachValue { deviceContext ->
                if (deviceContext.controllerNumber == controllerNumber) {
                    deviceContext.leftTriggerMotor = leftTrigger
                    deviceContext.rightTriggerMotor = rightTrigger

                    if (deviceContext.quadVibrators) {
                        rumbleQuadVibrators(
                            checkNotNull(deviceContext.vibratorManager),
                            deviceContext.lowFreqMotor,
                            deviceContext.highFreqMotor,
                            deviceContext.leftTriggerMotor,
                            deviceContext.rightTriggerMotor,
                        )
                    }
                }
            }
        }

        usbDeviceContexts.forEachValue { deviceContext ->
            if (deviceContext.controllerNumber == controllerNumber) {
                checkNotNull(deviceContext.device).rumbleTriggers(leftTrigger, rightTrigger)
            }
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("DEPRECATION")
    private fun createSensorListener(
        controllerNumber: Short,
        motionType: Byte,
        needsDeviceOrientationCorrection: Boolean,
    ): SensorEventListener {
        return object : SensorEventListener {
            private val lastValues = FloatArray(3)

            // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13
            // backlog).
            @Suppress("LongMethod")
            override fun onSensorChanged(sensorEvent: SensorEvent) {
                if (captured) {
                    return
                }

                // Android will invoke our callback any time we get a new reading,
                // even if the values are the same as last time. Don't report a
                // duplicate set of values to save bandwidth.
                if (
                    sensorEvent.values[0] == lastValues[0] &&
                        sensorEvent.values[1] == lastValues[1] &&
                        sensorEvent.values[2] == lastValues[2]
                ) {
                    return
                } else {
                    lastValues[0] = sensorEvent.values[0]
                    lastValues[1] = sensorEvent.values[1]
                    lastValues[2] = sensorEvent.values[2]
                }

                var x = 0
                var y = 1
                var z = 2
                var xFactor = 1
                val yFactor = 1
                var zFactor = 1

                if (needsDeviceOrientationCorrection) {
                    val deviceRotation = activityContext.windowManager.defaultDisplay.rotation
                    when (deviceRotation) {
                        Surface.ROTATION_0,
                        Surface.ROTATION_180 -> {
                            x = 0
                            y = 2
                            z = 1
                        }
                        Surface.ROTATION_90,
                        Surface.ROTATION_270 -> {
                            x = 1
                            y = 2
                            z = 0
                        }
                    }

                    when (deviceRotation) {
                        Surface.ROTATION_0 -> zFactor = -1
                        Surface.ROTATION_90 -> {
                            xFactor = -1
                            zFactor = -1
                        }
                        Surface.ROTATION_180 -> xFactor = -1
                        Surface.ROTATION_270 -> {}
                    }
                }

                if (motionType == MoonBridge.LI_MOTION_TYPE_GYRO) {
                    // Convert from rad/s to deg/s
                    conn.sendControllerMotionEvent(
                        controllerNumber.toByte(),
                        motionType,
                        sensorEvent.values[x] * xFactor * RAD_TO_DEG,
                        sensorEvent.values[y] * yFactor * RAD_TO_DEG,
                        sensorEvent.values[z] * zFactor * RAD_TO_DEG,
                    )
                } else {
                    // Pass m/s^2 directly without conversion
                    conn.sendControllerMotionEvent(
                        controllerNumber.toByte(),
                        motionType,
                        sensorEvent.values[x] * xFactor,
                        sensorEvent.values[y] * yFactor,
                        sensorEvent.values[z] * zFactor,
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // Nothing to do on an accuracy change
            }
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress(
        "CyclomaticComplexMethod",
        "LongMethod",
        "LoopWithTooManyJumpStatements",
    )
    fun handleSetMotionEventState(controllerNumber: Short, motionType: Byte, reportRateHz: Short) {
        if (stopped) {
            return
        }

        // Report rate is restricted to <= 200 Hz without the HIGH_SAMPLING_RATE_SENSORS permission
        val rateHz = min(MAX_SENSOR_RATE_HZ, reportRateHz.toInt()).toShort()

        var i = 0
        while (i < inputDeviceContexts.size) {
            val deviceContext = inputDeviceContexts.valueAt(i)

            if (deviceContext.controllerNumber == controllerNumber) {
                // Store the desired report rate even if we don't have sensors. In some cases,
                // input devices can be reconfigured at runtime which results in a change where
                // sensors disappear and reappear. By storing the desired report rate, we can
                // reapply the desired motion sensor configuration after they reappear.
                when (motionType) {
                    MoonBridge.LI_MOTION_TYPE_ACCEL -> deviceContext.accelReportRateHz = rateHz
                    MoonBridge.LI_MOTION_TYPE_GYRO -> deviceContext.gyroReportRateHz = rateHz
                }

                backgroundThreadHandler.removeCallbacks(deviceContext.enableSensorRunnable)

                val sm = deviceContext.sensorManager
                if (sm == null) {
                    i++
                    continue
                }

                when (motionType) {
                    MoonBridge.LI_MOTION_TYPE_ACCEL -> {
                        val oldAccelListener = deviceContext.accelListener
                        if (oldAccelListener != null) {
                            sm.unregisterListener(oldAccelListener)
                            deviceContext.accelListener = null
                        }

                        // Enable the accelerometer if requested
                        val accelSensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
                        if (rateHz.toInt() != 0 && accelSensor != null) {
                            val accelListener =
                                createSensorListener(
                                    controllerNumber,
                                    motionType,
                                    sm === deviceSensorManager,
                                )
                            deviceContext.accelListener = accelListener
                            sm.registerListener(
                                accelListener,
                                accelSensor,
                                MICROSECONDS_PER_SECOND / rateHz,
                            )
                        }
                    }
                    MoonBridge.LI_MOTION_TYPE_GYRO -> {
                        val oldGyroListener = deviceContext.gyroListener
                        if (oldGyroListener != null) {
                            sm.unregisterListener(oldGyroListener)
                            deviceContext.gyroListener = null
                        }

                        // Enable the gyroscope if requested
                        val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
                        if (rateHz.toInt() != 0 && gyroSensor != null) {
                            val gyroListener =
                                createSensorListener(
                                    controllerNumber,
                                    motionType,
                                    sm === deviceSensorManager,
                                )
                            deviceContext.gyroListener = gyroListener
                            sm.registerListener(
                                gyroListener,
                                gyroSensor,
                                MICROSECONDS_PER_SECOND / rateHz,
                            )
                        }
                    }
                }
                break
            }
            i++
        }
    }

    fun handleSetControllerLED(controllerNumber: Short, r: Byte, g: Byte, b: Byte) {
        if (stopped) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            inputDeviceContexts.forEachValue { deviceContext ->
                // Ignore input devices without an RGB LED
                if (deviceContext.controllerNumber == controllerNumber && deviceContext.hasRgbLed) {
                    // Convert the RGB components into the integer value that LightState uses
                    deviceContext.ledArgbValue =
                        OPAQUE_ALPHA or
                            ((r.toInt() shl RED_SHIFT) and RED_MASK) or
                            ((g.toInt() shl GREEN_SHIFT) and GREEN_MASK) or
                            (b.toInt() and BLUE_MASK)

                    backgroundThreadHandler.removeCallbacks(deviceContext.setLedStateRunnable)
                    backgroundThreadHandler.post(deviceContext.setLedStateRunnable)
                }
            }
        }
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress(
        "ComplexCondition",
        "CyclomaticComplexMethod",
        "LongMethod",
        "PrintStackTrace",
        "ReturnCount",
    )
    fun handleButtonUp(event: KeyEvent): Boolean {
        val context = getContextForEvent(event) ?: return true

        var keyCode = handleRemapping(context, event)
        if (keyCode < 0) {
            return (keyCode == REMAP_CONSUME)
        }

        if (prefConfig.flipFaceButtons) {
            keyCode = handleFlipFaceButtons(keyCode)
        }

        // If the button hasn't been down long enough, sleep for a bit before sending the up event
        // This allows "instant" button presses (like OUYA's virtual menu button) to work. This
        // path should not be triggered during normal usage.
        val buttonDownTime = (event.eventTime - event.downTime).toInt()
        if (buttonDownTime < MINIMUM_BUTTON_DOWN_TIME_MS) {
            // Since our sleep time is so short (<= 25 ms), it shouldn't cause a problem doing this
            // in the UI thread.
            try {
                Thread.sleep((MINIMUM_BUTTON_DOWN_TIME_MS - buttonDownTime).toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()

                // InterruptedException clears the thread's interrupt status. Since we can't
                // handle that here, we will re-interrupt the thread to set the interrupt
                // status back to true.
                Thread.currentThread().interrupt()
            }
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_MODE ->
                context.inputMap = context.inputMap and ControllerPacket.SPECIAL_BUTTON_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU -> {
                // Sometimes we'll get a spurious key up event on controller disconnect.
                // Make sure it's real by checking that the key is actually down before taking
                // any action.
                if (
                    (context.inputMap and ControllerPacket.PLAY_FLAG) != 0 &&
                        event.eventTime - context.startDownTime > START_DOWN_TIME_MOUSE_MODE_MS &&
                        prefConfig.mouseEmulation &&
                        !captured &&
                        !context.pendingExit &&
                        !context.menuChord.claims(ControllerPacket.PLAY_FLAG)
                ) {
                    context.toggleMouseEmulation()
                }
                context.inputMap = context.inputMap and ControllerPacket.PLAY_FLAG.inv()
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_SELECT ->
                context.inputMap = context.inputMap and ControllerPacket.BACK_FLAG.inv()
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap and ControllerPacket.LEFT_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap and ControllerPacket.RIGHT_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap and ControllerPacket.UP_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap and ControllerPacket.DOWN_FLAG.inv()
            }
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap and
                        (ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG).inv()
            }
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap and
                        (ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG).inv()
            }
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap and
                        (ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG).inv()
            }
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap and
                        (ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG).inv()
            }
            KeyEvent.KEYCODE_BUTTON_B ->
                context.inputMap = context.inputMap and ControllerPacket.B_FLAG.inv()
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A ->
                context.inputMap = context.inputMap and ControllerPacket.A_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_X ->
                context.inputMap = context.inputMap and ControllerPacket.X_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_Y ->
                context.inputMap = context.inputMap and ControllerPacket.Y_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_L1 -> {
                context.inputMap = context.inputMap and ControllerPacket.LB_FLAG.inv()
                context.lastLbUpTime = event.eventTime
            }
            KeyEvent.KEYCODE_BUTTON_R1 -> {
                context.inputMap = context.inputMap and ControllerPacket.RB_FLAG.inv()
                context.lastRbUpTime = event.eventTime
            }
            KeyEvent.KEYCODE_BUTTON_THUMBL ->
                context.inputMap = context.inputMap and ControllerPacket.LS_CLK_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_THUMBR ->
                context.inputMap = context.inputMap and ControllerPacket.RS_CLK_FLAG.inv()
            // Xbox Series X Share button
            KeyEvent.KEYCODE_MEDIA_RECORD ->
                context.inputMap = context.inputMap and ControllerPacket.MISC_FLAG.inv()
            // PS4/PS5 touchpad button (prior to 4.10)
            KeyEvent.KEYCODE_BUTTON_1 ->
                context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                if (context.leftTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true
                }
                context.leftTrigger = 0
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (context.rightTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true
                }
                context.rightTrigger = 0
            }
            KeyEvent.KEYCODE_UNKNOWN -> {
                // Paddles aren't mapped in any of the Android key layout files,
                // so we need to handle the evdev key codes directly.
                if (context.hasPaddles) {
                    when (event.scanCode) {
                        BTN_TRIGGER_HAPPY5 ->
                            context.inputMap =
                                context.inputMap and ControllerPacket.PADDLE1_FLAG.inv()
                        BTN_TRIGGER_HAPPY6 ->
                            context.inputMap =
                                context.inputMap and ControllerPacket.PADDLE2_FLAG.inv()
                        BTN_TRIGGER_HAPPY7 ->
                            context.inputMap =
                                context.inputMap and ControllerPacket.PADDLE3_FLAG.inv()
                        BTN_TRIGGER_HAPPY8 ->
                            context.inputMap =
                                context.inputMap and ControllerPacket.PADDLE4_FLAG.inv()
                        else -> return false
                    }
                } else {
                    return false
                }
            }
            else -> return false
        }

        // Check if we're emulating the select button
        if ((context.emulatingButtonFlags and EMULATING_SELECT) != 0) {
            // If either start or LB is up, select comes up too
            if (
                (context.inputMap and ControllerPacket.PLAY_FLAG) == 0 ||
                    (context.inputMap and ControllerPacket.LB_FLAG) == 0
            ) {
                context.inputMap = context.inputMap and ControllerPacket.BACK_FLAG.inv()

                context.emulatingButtonFlags =
                    context.emulatingButtonFlags and EMULATING_SELECT.inv()
            }
        }

        // Check if we're emulating the special button
        val special = GuideChord.onButtonUp(context.inputMap, context.emulatingButtonFlags)
        context.inputMap = special.inputMap
        context.emulatingButtonFlags = special.emulatingFlags

        // Check if we're emulating the touchpad button
        if ((context.emulatingButtonFlags and EMULATING_TOUCHPAD) != 0) {
            // If either select or LB is up, touchpad comes up too
            if (
                (context.inputMap and ControllerPacket.BACK_FLAG) == 0 ||
                    (context.inputMap and ControllerPacket.LB_FLAG) == 0
            ) {
                context.inputMap = context.inputMap and ControllerPacket.TOUCHPAD_FLAG.inv()

                context.emulatingButtonFlags =
                    context.emulatingButtonFlags and EMULATING_TOUCHPAD.inv()
            }
        }

        sendControllerInputPacket(context, event.eventTime)

        if (context.pendingExit && context.inputMap == 0) {
            // All buttons from the quit combo are lifted. Finish the activity now.
            activityContext.finish()
        }

        return true
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun handleButtonDown(event: KeyEvent): Boolean {
        val context = getContextForEvent(event) ?: return true

        // A key that went down while the chrome had the input does nothing until it is let go
        if (captureEnd.began(event.downTime)) {
            return true
        }

        var keyCode = handleRemapping(context, event)
        if (keyCode < 0) {
            return (keyCode == REMAP_CONSUME)
        }

        if (prefConfig.flipFaceButtons) {
            keyCode = handleFlipFaceButtons(keyCode)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_MODE -> {
                context.hasMode = true
                context.inputMap = context.inputMap or ControllerPacket.SPECIAL_BUTTON_FLAG
            }
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_MENU -> {
                if (event.repeatCount == 0) {
                    context.startDownTime = event.eventTime
                }
                context.inputMap = context.inputMap or ControllerPacket.PLAY_FLAG
            }
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_BUTTON_SELECT -> {
                context.hasSelect = true
                context.inputMap = context.inputMap or ControllerPacket.BACK_FLAG
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap or ControllerPacket.LEFT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap or ControllerPacket.RIGHT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap or ControllerPacket.UP_FLAG
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap = context.inputMap or ControllerPacket.DOWN_FLAG
            }
            KeyEvent.KEYCODE_DPAD_UP_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap or ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_UP_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap or ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_DOWN_LEFT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap or ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG
            }
            KeyEvent.KEYCODE_DPAD_DOWN_RIGHT -> {
                if (context.hatXAxisUsed && context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true
                }
                context.inputMap =
                    context.inputMap or ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG
            }
            KeyEvent.KEYCODE_BUTTON_B ->
                context.inputMap = context.inputMap or ControllerPacket.B_FLAG
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_BUTTON_A ->
                context.inputMap = context.inputMap or ControllerPacket.A_FLAG
            KeyEvent.KEYCODE_BUTTON_X ->
                context.inputMap = context.inputMap or ControllerPacket.X_FLAG
            KeyEvent.KEYCODE_BUTTON_Y ->
                context.inputMap = context.inputMap or ControllerPacket.Y_FLAG
            KeyEvent.KEYCODE_BUTTON_L1 ->
                context.inputMap = context.inputMap or ControllerPacket.LB_FLAG
            KeyEvent.KEYCODE_BUTTON_R1 ->
                context.inputMap = context.inputMap or ControllerPacket.RB_FLAG
            KeyEvent.KEYCODE_BUTTON_THUMBL ->
                context.inputMap = context.inputMap or ControllerPacket.LS_CLK_FLAG
            KeyEvent.KEYCODE_BUTTON_THUMBR ->
                context.inputMap = context.inputMap or ControllerPacket.RS_CLK_FLAG
            // Xbox Series X Share button
            KeyEvent.KEYCODE_MEDIA_RECORD ->
                context.inputMap = context.inputMap or ControllerPacket.MISC_FLAG
            // PS4/PS5 touchpad button (prior to 4.10)
            KeyEvent.KEYCODE_BUTTON_1 ->
                context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG
            KeyEvent.KEYCODE_BUTTON_L2 -> {
                if (context.leftTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true
                }
                context.leftTrigger = 0xFF.toByte()
            }
            KeyEvent.KEYCODE_BUTTON_R2 -> {
                if (context.rightTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true
                }
                context.rightTrigger = 0xFF.toByte()
            }
            KeyEvent.KEYCODE_UNKNOWN -> {
                // Paddles aren't mapped in any of the Android key layout files,
                // so we need to handle the evdev key codes directly.
                if (context.hasPaddles) {
                    when (event.scanCode) {
                        BTN_TRIGGER_HAPPY5 ->
                            context.inputMap = context.inputMap or ControllerPacket.PADDLE1_FLAG
                        BTN_TRIGGER_HAPPY6 ->
                            context.inputMap = context.inputMap or ControllerPacket.PADDLE2_FLAG
                        BTN_TRIGGER_HAPPY7 ->
                            context.inputMap = context.inputMap or ControllerPacket.PADDLE3_FLAG
                        BTN_TRIGGER_HAPPY8 ->
                            context.inputMap = context.inputMap or ControllerPacket.PADDLE4_FLAG
                        else -> return false
                    }
                } else {
                    return false
                }
            }
            else -> return false
        }

        // Start+Back+LB+RB is the quit combo
        if (
            context.inputMap ==
                (ControllerPacket.BACK_FLAG or
                    ControllerPacket.PLAY_FLAG or
                    ControllerPacket.LB_FLAG or
                    ControllerPacket.RB_FLAG)
        ) {
            // Wait for the combo to lift and then finish the activity
            context.pendingExit = true
        }

        // Start+LB acts like select for controllers with one button
        if (!context.hasSelect) {
            if (
                context.inputMap == (ControllerPacket.PLAY_FLAG or ControllerPacket.LB_FLAG) ||
                    (context.inputMap == ControllerPacket.PLAY_FLAG &&
                        event.eventTime - context.lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
            ) {
                context.inputMap =
                    context.inputMap and
                        (ControllerPacket.PLAY_FLAG or ControllerPacket.LB_FLAG).inv()
                context.inputMap = context.inputMap or ControllerPacket.BACK_FLAG

                context.emulatingButtonFlags = context.emulatingButtonFlags or EMULATING_SELECT
            }
        } else if (context.needsClickpadEmulation) {
            // Select+LB acts like the clickpad when we're faking a PS4 controller for motion
            // support
            if (
                context.inputMap == (ControllerPacket.BACK_FLAG or ControllerPacket.LB_FLAG) ||
                    (context.inputMap == ControllerPacket.BACK_FLAG &&
                        event.eventTime - context.lastLbUpTime <= MAXIMUM_BUMPER_UP_DELAY_MS)
            ) {
                context.inputMap =
                    context.inputMap and
                        (ControllerPacket.BACK_FLAG or ControllerPacket.LB_FLAG).inv()
                context.inputMap = context.inputMap or ControllerPacket.TOUCHPAD_FLAG

                context.emulatingButtonFlags = context.emulatingButtonFlags or EMULATING_TOUCHPAD
            }
        }

        // If there is a physical select button, we'll use Start+Select as the special button combo
        // otherwise we'll use Start+RB.
        val special =
            GuideChord.onButtonDown(
                ButtonState(context.inputMap, context.emulatingButtonFlags),
                context.hasSelect,
                context.hasMode,
                event.eventTime,
                context.lastRbUpTime,
            )
        context.inputMap = special.inputMap
        context.emulatingButtonFlags = special.emulatingFlags

        // We don't need to send repeat key down events, but the platform
        // sends us events that claim to be repeats but they're from different
        // devices, so we just send them all and deal with some duplicates.
        sendControllerInputPacket(context, event.eventTime)
        return true
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("LongParameterList")
    fun reportOscState(
        buttonFlags: Int,
        leftStickX: Short,
        leftStickY: Short,
        rightStickX: Short,
        rightStickY: Short,
        leftTrigger: Byte,
        rightTrigger: Byte,
    ) {
        if (captured) {
            return
        }

        defaultContext.leftStickX = leftStickX
        defaultContext.leftStickY = leftStickY

        defaultContext.rightStickX = rightStickX
        defaultContext.rightStickY = rightStickY

        defaultContext.leftTrigger = leftTrigger
        defaultContext.rightTrigger = rightTrigger

        defaultContext.inputMap = buttonFlags

        sendControllerInputPacket(defaultContext)
    }

    // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
    @Suppress("ComplexCondition")
    override fun reportControllerState(
        controllerId: Int,
        buttonFlags: Int,
        leftStickX: Float,
        leftStickY: Float,
        rightStickX: Float,
        rightStickY: Float,
        leftTrigger: Float,
        rightTrigger: Float,
    ) {
        val context: GenericControllerContext = usbDeviceContexts.get(controllerId) ?: return

        context.chromeInput.report(buttonFlags, leftStickX, leftStickY)
        val filteredButtonFlags = context.heldSinceCapture.filter(buttonFlags, captured)
        if (
            context.startHold.update(filteredButtonFlags, SystemClock.uptimeMillis()) &&
                !captured &&
                prefConfig.mouseEmulation &&
                !context.menuChord.claims(ControllerPacket.PLAY_FLAG) &&
                !context.quitCombo.pending()
        ) {
            // The USB driver reports from a thread without a looper, and the toggle shows a notice
            if (Looper.myLooper() == Looper.getMainLooper()) {
                context.toggleMouseEmulation()
            } else {
                mainThreadHandler.post { context.toggleMouseEmulation() }
            }
        }
        // While captured the filtered flags are 0, so this only clears an armed combo; the
        // !captured guard keeps a finish from racing setCaptured
        if (context.quitCombo.update(filteredButtonFlags) && !captured) {
            mainThreadHandler.post { activityContext.finish() }
        }
        if (captured) {
            return
        }

        val leftStickVector = populateCachedVector(leftStickX, leftStickY)

        handleDeadZone(leftStickVector, context.leftStickDeadzoneRadius)

        context.leftStickX = (leftStickVector.x * STICK_MAX).toInt().toShort()
        context.leftStickY = (-leftStickVector.y * STICK_MAX).toInt().toShort()

        val rightStickVector = populateCachedVector(rightStickX, rightStickY)

        handleDeadZone(rightStickVector, context.rightStickDeadzoneRadius)

        context.rightStickX = (rightStickVector.x * STICK_MAX).toInt().toShort()
        context.rightStickY = (-rightStickVector.y * STICK_MAX).toInt().toShort()

        val leftTriggerValue = if (leftTrigger <= context.triggerDeadzone) 0f else leftTrigger
        val rightTriggerValue = if (rightTrigger <= context.triggerDeadzone) 0f else rightTrigger

        context.leftTrigger = (leftTriggerValue * TRIGGER_MAX).toInt().toByte()
        context.rightTrigger = (rightTriggerValue * TRIGGER_MAX).toInt().toByte()

        context.inputMap = filteredButtonFlags

        sendControllerInputPacket(context)
    }

    override fun deviceRemoved(controller: AbstractController) {
        val context = usbDeviceContexts.get(controller.controllerId)
        if (context != null) {
            LimeLog.info("Removed controller: ${controller.controllerId}")
            releaseControllerNumber(context)
            context.destroy()
            usbDeviceContexts.remove(controller.controllerId)
        }
    }

    override fun deviceAdded(controller: AbstractController) {
        if (stopped) {
            return
        }

        val context = createUsbDeviceContextForDevice(controller)
        if (mouseEmulationEnabled) {
            context.setMouseEmulation(true, false)
        }
        usbDeviceContexts.put(controller.controllerId, context)
    }

    private open inner class GenericControllerContext {
        @JvmField var id = 0
        @JvmField var external = false

        @JvmField var vendorId = 0
        @JvmField var productId = 0

        @JvmField var leftStickDeadzoneRadius = 0f
        @JvmField var rightStickDeadzoneRadius = 0f
        @JvmField var triggerDeadzone = 0f

        @JvmField var assignedControllerNumber = false
        @JvmField var reservedControllerNumber = false
        @JvmField var controllerNumber: Short = 0

        @JvmField var inputMap = 0
        @JvmField var leftTrigger: Byte = 0x00
        @JvmField var rightTrigger: Byte = 0x00
        @JvmField var rightStickX: Short = 0x0000
        @JvmField var rightStickY: Short = 0x0000
        @JvmField var leftStickX: Short = 0x0000
        @JvmField var leftStickY: Short = 0x0000

        @JvmField var mouseEmulationActive = false
        @Volatile @JvmField var mouseEmulationLastInputMap = 0
        @JvmField val mouseEmulationReportPeriod = 16
        @JvmField var mouseEmulationLastTick = 0L

        // One per stick, whether it moves the pointer or scrolls
        @JvmField val leftStickMotion = PointerMotion()
        @JvmField val rightStickMotion = PointerMotion()

        @JvmField
        val mouseEmulationRunnable: Runnable =
            object : Runnable {
                // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13
                // backlog).
                @Suppress("LongMethod")
                override fun run() {
                    if (!mouseEmulationActive) {
                        return
                    }

                    val now = SystemClock.uptimeMillis()
                    val elapsedMs = now - mouseEmulationLastTick
                    mouseEmulationLastTick = now

                    // RB held slows the move; the buttons of a pad in mouse mode are not sent to
                    // the PC
                    val precision = (mouseEmulationLastInputMap and ControllerPacket.RB_FLAG) != 0

                    // Send mouse events from analog sticks
                    if (
                        prefConfig.analogStickForScrolling ==
                            PreferenceConfiguration.AnalogStickForScrolling.RIGHT
                    ) {
                        sendEmulatedMouseMove(
                            leftStickMotion,
                            leftStickX,
                            leftStickY,
                            elapsedMs,
                            precision,
                        )
                        sendEmulatedMouseScroll(
                            rightStickMotion,
                            rightStickX,
                            rightStickY,
                            elapsedMs,
                            precision,
                        )
                    } else if (
                        prefConfig.analogStickForScrolling ==
                            PreferenceConfiguration.AnalogStickForScrolling.LEFT
                    ) {
                        sendEmulatedMouseMove(
                            rightStickMotion,
                            rightStickX,
                            rightStickY,
                            elapsedMs,
                            precision,
                        )
                        sendEmulatedMouseScroll(
                            leftStickMotion,
                            leftStickX,
                            leftStickY,
                            elapsedMs,
                            precision,
                        )
                    } else {
                        sendEmulatedMouseMove(
                            leftStickMotion,
                            leftStickX,
                            leftStickY,
                            elapsedMs,
                            precision,
                        )
                        sendEmulatedMouseMove(
                            rightStickMotion,
                            rightStickX,
                            rightStickY,
                            elapsedMs,
                            precision,
                        )
                    }

                    // Requeue the callback
                    mainThreadHandler.postDelayed(this, mouseEmulationReportPeriod.toLong())
                }
            }

        fun toggleMouseEmulation() {
            setMouseEmulation(!mouseEmulationActive, true)
            mouseEmulationEnabled = mouseEmulationActive
        }

        fun setMouseEmulation(enabled: Boolean, notify: Boolean) {
            mainThreadHandler.removeCallbacks(mouseEmulationRunnable)
            mouseEmulationActive = enabled
            leftStickMotion.reset()
            rightStickMotion.reset()
            if (notify) {
                Toast.makeText(
                        activityContext,
                        "Mouse emulation is: " + (if (mouseEmulationActive) "ON" else "OFF"),
                        Toast.LENGTH_SHORT,
                    )
                    .show()
            }

            if (mouseEmulationActive) {
                mouseEmulationLastTick = SystemClock.uptimeMillis()
                mainThreadHandler.postDelayed(
                    mouseEmulationRunnable,
                    mouseEmulationReportPeriod.toLong(),
                )
            }
        }

        open fun destroy() {
            mouseEmulationActive = false
            mainThreadHandler.removeCallbacks(mouseEmulationRunnable)
            mainThreadHandler.removeCallbacks(menuChordCheck)
        }

        open fun sendControllerArrival() {}

        // Select+Start held for a second opens the menu; the logic is in MenuChordDetector
        @JvmField val menuChord = MenuChordDetector()
        @JvmField var tapButtons = 0

        // What the pad does to the chrome while it has the input; the logic is in UsbChromeInput.
        // It
        // has its own main-thread handler because the default context exists before
        // mainThreadHandler
        @JvmField
        val chromeInput =
            UsbChromeInput(Handler(Looper.getMainLooper())) {
                if (captured) chromeKeySink else null
            }

        // The Start hold that turns the gamepad mouse on or off, for pads that give no key events
        @JvmField val startHold = StartHold()

        // The Start+Back+LB+RB quit combo of a pad that gives no key events, as pendingExit is for
        // the others
        @JvmField val quitCombo = QuitCombo()

        // The buttons held since the chrome had the input stay unseen until they are let go
        @JvmField val heldSinceCapture = HeldSinceCapture()

        @JvmField
        val menuChordCheck = Runnable {
            if (!captured) {
                val due = menuChord.due(SystemClock.uptimeMillis())
                if (!due.silent) {
                    transmitControllerInput(this@GenericControllerContext)
                }
                if (due.fired) {
                    notifyMenuChord()
                }
            }
        }

        // Whether a lone Start or Select waits for its partner: only on pads that send them as
        // separate key events and have a Select button. A USB pad reports both in one report.
        open fun holdsLonePress(): Boolean {
            return false
        }

        // The buttons the PC gets: not the ones the chord holds back, and the short press of an
        // early chord
        fun sentInputMap(): Int {
            return (inputMap and menuChord.hidden().inv()) or tapButtons
        }

        // What an early chord sends the PC as a short press: Guide on pads that stand in for it,
        // Start and Select on the others. Pads that are not input devices have no Guide button.
        open fun chordTapButtons(update: MenuChordDetector.Update): Int {
            return GuideChord.tapButtons(update, true, true)
        }
    }

    private inner class InputDeviceContext : GenericControllerContext() {
        @JvmField var name: String? = null
        @JvmField var vibratorManager: VibratorManager? = null
        @JvmField var vibrator: Vibrator? = null
        @JvmField var quadVibrators = false
        @JvmField var lowFreqMotor: Short = 0
        @JvmField var highFreqMotor: Short = 0
        @JvmField var leftTriggerMotor: Short = 0
        @JvmField var rightTriggerMotor: Short = 0

        @JvmField var sensorManager: SensorManager? = null
        @JvmField var gyroListener: SensorEventListener? = null
        @JvmField var gyroReportRateHz: Short = 0
        @JvmField var accelListener: SensorEventListener? = null
        @JvmField var accelReportRateHz: Short = 0

        @JvmField var inputDevice: InputDevice? = null

        @JvmField var hasRgbLed = false
        @JvmField var lightsSession: LightsManager.LightsSession? = null
        @JvmField var ledArgbValue = 0

        // These are BatteryState values, not Moonlight values
        @JvmField var lastReportedBatteryStatus = 0
        @JvmField var lastReportedBatteryCapacity = 0f

        @JvmField var leftStickXAxis = -1
        @JvmField var leftStickYAxis = -1

        @JvmField var rightStickXAxis = -1
        @JvmField var rightStickYAxis = -1

        @JvmField var leftTriggerAxis = -1
        @JvmField var rightTriggerAxis = -1
        @JvmField var triggersIdleNegative = false
        @JvmField var leftTriggerAxisUsed = false
        @JvmField var rightTriggerAxisUsed = false

        @JvmField var hatXAxis = -1
        @JvmField var hatYAxis = -1
        @JvmField var hatXAxisUsed = false
        @JvmField var hatYAxisUsed = false

        @JvmField var touchpadXRange: InputDevice.MotionRange? = null
        @JvmField var touchpadYRange: InputDevice.MotionRange? = null
        @JvmField var touchpadPressureRange: InputDevice.MotionRange? = null

        @JvmField var isNonStandardDualShock4 = false
        @JvmField var usesLinuxGamepadStandardFaceButtons = false
        @JvmField var isNonStandardXboxBtController = false
        @JvmField var isServal = false
        @JvmField var backIsStart = false
        @JvmField var modeIsSelect = false
        @JvmField var searchIsMode = false
        @JvmField var ignoreBack = false
        @JvmField var hasJoystickAxes = false
        @JvmField var pendingExit = false
        @JvmField var isDualShockStandaloneTouchpad = false

        @JvmField var emulatingButtonFlags = 0
        @JvmField var hasSelect = false
        @JvmField var hasMode = false
        @JvmField var hasPaddles = false
        @JvmField var hasShare = false
        @JvmField var needsClickpadEmulation = false

        // Used for OUYA bumper state tracking since they force all buttons
        // up when the OUYA button goes down. We watch the last time we get
        // a bumper up and compare that to our maximum delay when we receive
        // a Start button press to see if we should activate one of our
        // emulated button combos.
        @JvmField var lastLbUpTime = 0L
        @JvmField var lastRbUpTime = 0L

        @JvmField var startDownTime = 0L

        override fun chordTapButtons(update: MenuChordDetector.Update): Int {
            return GuideChord.tapButtons(update, hasSelect, hasMode)
        }

        override fun holdsLonePress(): Boolean {
            return hasSelect
        }

        @JvmField
        val batteryStateUpdateRunnable: Runnable =
            object : Runnable {
                override fun run() {
                    sendControllerBatteryPacket(this@InputDeviceContext)

                    // Requeue the callback
                    backgroundThreadHandler.postDelayed(
                        this,
                        BATTERY_RECHECK_INTERVAL_MS.toLong(),
                    )
                }
            }

        @JvmField
        val enableSensorRunnable = Runnable {
            // Turn back on any sensors that should be reporting but are currently unregistered
            if (accelReportRateHz.toInt() != 0 && accelListener == null) {
                handleSetMotionEventState(
                    controllerNumber,
                    MoonBridge.LI_MOTION_TYPE_ACCEL,
                    accelReportRateHz,
                )
            }
            if (gyroReportRateHz.toInt() != 0 && gyroListener == null) {
                handleSetMotionEventState(
                    controllerNumber,
                    MoonBridge.LI_MOTION_TYPE_GYRO,
                    gyroReportRateHz,
                )
            }
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        // The run override carries the API level, which a lambda cannot.
        @Suppress("ObjectLiteralToLambda")
        @JvmField
        val setLedStateRunnable: Runnable =
            object : Runnable {
                @RequiresApi(Build.VERSION_CODES.S)
                override fun run() {
                    val lightState = LightState.Builder().setColor(ledArgbValue).build()

                    // Set the RGB value for each RGB-controllable LED on the device
                    var hasRgbLight = false
                    val lightsRequestBuilder = LightsRequest.Builder()
                    for (light in checkNotNull(inputDevice).lightsManager.lights) {
                        if (light.hasRgbControl()) {
                            lightsRequestBuilder.addLight(light, lightState)
                            hasRgbLight = true
                        }
                    }

                    if (hasRgbLight) {
                        // Create a new light session if one doesn't already exist
                        if (lightsSession == null) {
                            lightsSession = checkNotNull(inputDevice).lightsManager.openSession()
                        }

                        // Apply the LED changes
                        checkNotNull(lightsSession).requestLights(lightsRequestBuilder.build())
                    }
                }
            }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("TooGenericExceptionCaught")
        override fun destroy() {
            super.destroy()

            val contextVibratorManager = vibratorManager
            val contextVibrator = vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && contextVibratorManager != null) {
                contextVibratorManager.cancel()
            } else if (contextVibrator != null) {
                contextVibrator.cancel()
            }

            backgroundThreadHandler.removeCallbacks(enableSensorRunnable)
            backgroundThreadHandler.removeCallbacks(setLedStateRunnable)

            val gyro = gyroListener
            if (gyro != null) {
                checkNotNull(sensorManager).unregisterListener(gyro)
            }
            val accel = accelListener
            if (accel != null) {
                checkNotNull(sensorManager).unregisterListener(accel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val session = lightsSession
                if (session != null) {
                    try {
                        session.close()
                    } catch (e: RuntimeException) {
                        LimeLog.warning("Error closing LightsSession: ${e.message}")
                    }
                }
            }

            backgroundThreadHandler.removeCallbacks(batteryStateUpdateRunnable)
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("CyclomaticComplexMethod", "LongMethod")
        override fun sendControllerArrival() {
            val device = checkNotNull(inputDevice)
            val type: Byte
            when (device.vendorId) {
                VENDOR_MICROSOFT -> type = MoonBridge.LI_CTYPE_XBOX // Microsoft
                VENDOR_SONY -> type = MoonBridge.LI_CTYPE_PS // Sony
                VENDOR_NINTENDO -> type = MoonBridge.LI_CTYPE_NINTENDO // Nintendo
                // Consult SDL's controller type list to see if it knows
                else -> type = MoonBridge.guessControllerType(device.vendorId, device.productId)
            }

            var supportedButtonFlags = 0
            for (entry in ANDROID_TO_LI_BUTTON_MAP.entries) {
                if (device.hasKeys(entry.key)[0]) {
                    supportedButtonFlags = supportedButtonFlags or entry.value
                }
            }

            // Add non-standard button flags that may not be mapped in the Android kl file
            if (hasPaddles) {
                supportedButtonFlags =
                    supportedButtonFlags or
                        ControllerPacket.PADDLE1_FLAG or
                        ControllerPacket.PADDLE2_FLAG or
                        ControllerPacket.PADDLE3_FLAG or
                        ControllerPacket.PADDLE4_FLAG
            }
            if (hasShare) {
                supportedButtonFlags = supportedButtonFlags or ControllerPacket.MISC_FLAG
            }

            if (getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_HAT_X) != null) {
                supportedButtonFlags =
                    supportedButtonFlags or
                        ControllerPacket.LEFT_FLAG or
                        ControllerPacket.RIGHT_FLAG
            }
            if (getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_HAT_Y) != null) {
                supportedButtonFlags =
                    supportedButtonFlags or ControllerPacket.UP_FLAG or ControllerPacket.DOWN_FLAG
            }

            var capabilities: Short = 0

            // Most of the advanced InputDevice capabilities came in Android S
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (quadVibrators) {
                    capabilities =
                        (capabilities.toInt() or
                                MoonBridge.LI_CCAP_RUMBLE.toInt() or
                                MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt())
                            .toShort()
                } else if (vibratorManager != null || vibrator != null) {
                    capabilities =
                        (capabilities.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort()
                }

                // Calling InputDevice.getBatteryState() to see if a battery is present
                // performs a Binder transaction that can cause ANRs on some devices.
                // To avoid this, we will just claim we can report battery state for all
                // external gamepad devices on Android S. If it turns out that no battery
                // is actually present, we'll just report unknown battery state to the host.
                if (external) {
                    capabilities =
                        (capabilities.toInt() or MoonBridge.LI_CCAP_BATTERY_STATE.toInt()).toShort()
                }

                if (hasRgbLed) {
                    capabilities =
                        (capabilities.toInt() or MoonBridge.LI_CCAP_RGB_LED.toInt()).toShort()
                }
            }

            // Report analog triggers if we have at least one trigger axis
            if (leftTriggerAxis != -1 || rightTriggerAxis != -1) {
                capabilities =
                    (capabilities.toInt() or MoonBridge.LI_CCAP_ANALOG_TRIGGERS.toInt()).toShort()
            }

            // Report sensors if the input device has them or we're using built-in sensors for a
            // built-in controller
            if (sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_ACCEL.toInt()).toShort()
            }
            if (sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_GYRO.toInt()).toShort()
            }

            val reportedType: Byte
            if (type != MoonBridge.LI_CTYPE_PS && sensorManager != null) {
                // Override the detected controller type if we're emulating motion sensors on an
                // Xbox controller
                Toast.makeText(
                        activityContext,
                        activityContext.resources.getText(R.string.toast_controller_type_changed),
                        Toast.LENGTH_LONG,
                    )
                    .show()
                reportedType = MoonBridge.LI_CTYPE_UNKNOWN

                // Remember that we should enable the clickpad emulation combo (Select+LB) for this
                // device
                needsClickpadEmulation = true
            } else {
                // Report the true type to the host PC if we're not emulating motion sensors
                reportedType = type
            }

            // We can perform basic rumble with any vibrator
            if (vibrator != null) {
                capabilities = (capabilities.toInt() or MoonBridge.LI_CCAP_RUMBLE.toInt()).toShort()
            }

            // Shield controllers use special APIs for rumble and battery state
            if (sceManager.isRecognizedDevice(device)) {
                capabilities =
                    (capabilities.toInt() or
                            MoonBridge.LI_CCAP_RUMBLE.toInt() or
                            MoonBridge.LI_CCAP_BATTERY_STATE.toInt())
                        .toShort()
            }

            if ((device.sources and InputDevice.SOURCE_TOUCHPAD) == InputDevice.SOURCE_TOUCHPAD) {
                capabilities =
                    (capabilities.toInt() or MoonBridge.LI_CCAP_TOUCHPAD.toInt()).toShort()

                // Use the platform API or internal heuristics to determine if this has a clickpad
                if (hasButtonUnderTouchpad(device, type)) {
                    supportedButtonFlags = supportedButtonFlags or ControllerPacket.TOUCHPAD_FLAG
                }
            }

            conn.sendControllerArrivalEvent(
                controllerNumber.toByte(),
                getActiveControllerMask(),
                reportedType,
                supportedButtonFlags,
                capabilities,
            )

            // After reporting arrival to the host, send initial battery state and begin monitoring
            backgroundThreadHandler.post(batteryStateUpdateRunnable)
        }

        fun migrateContext(oldContext: InputDeviceContext) {
            // Take ownership of the sensor and light sessions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.lightsSession = oldContext.lightsSession
                oldContext.lightsSession = null
            }
            this.ledArgbValue = oldContext.ledArgbValue
            this.gyroReportRateHz = oldContext.gyroReportRateHz
            this.accelReportRateHz = oldContext.accelReportRateHz

            // Don't release the controller number, because we will carry it over if it is present.
            // We also want to make sure the change is invisible to the host PC to avoid an
            // add/remove cycle for the gamepad which may break some games.
            oldContext.destroy()

            // Copy over existing controller number state
            this.assignedControllerNumber = oldContext.assignedControllerNumber
            this.reservedControllerNumber = oldContext.reservedControllerNumber
            this.controllerNumber = oldContext.controllerNumber

            // We may have set this device to use the built-in sensor manager. If so, do that again.
            if (oldContext.sensorManager === deviceSensorManager) {
                this.sensorManager = deviceSensorManager
            }

            // Copy state initialized in reportControllerArrival()
            this.needsClickpadEmulation = oldContext.needsClickpadEmulation

            // Re-enable sensors on the new context
            enableSensors()

            // Refresh the LED state if it's set. We don't know if there was a pending LED state
            // callback when the context was migrated, so we'll reset it just to be safe.
            if (this.lightsSession != null) {
                backgroundThreadHandler.post(setLedStateRunnable)
            }

            // Refresh battery state and start the battery state polling again
            backgroundThreadHandler.post(batteryStateUpdateRunnable)
        }

        fun disableSensors() {
            // Stop any pending enablement
            backgroundThreadHandler.removeCallbacks(enableSensorRunnable)

            // Unregister all sensor listeners
            val gyro = gyroListener
            if (gyro != null) {
                checkNotNull(sensorManager).unregisterListener(gyro)
                gyroListener = null

                // Send a gyro event to ensure the virtual controller is stationary
                conn.sendControllerMotionEvent(
                    controllerNumber.toByte(),
                    MoonBridge.LI_MOTION_TYPE_GYRO,
                    0f,
                    0f,
                    0f,
                )
            }
            val accel = accelListener
            if (accel != null) {
                checkNotNull(sensorManager).unregisterListener(accel)
                accelListener = null

                // We leave the acceleration as-is to preserve the attitude of the controller
            }
        }

        fun enableSensors() {
            // We allow 1 second for the input device to settle before re-enabling sensors.
            // Pointer capture can cause the input device to change, which can cause
            // InputDeviceSensorManager to crash due to missing null checks on the InputDevice.
            backgroundThreadHandler.postDelayed(enableSensorRunnable, SENSOR_ENABLE_DELAY_MS)
        }
    }

    private inner class UsbDeviceContext : GenericControllerContext() {
        @JvmField var device: AbstractController? = null

        override fun destroy() {
            super.destroy()

            // Nothing for now
        }

        override fun sendControllerArrival() {
            val usbDevice = checkNotNull(device)
            conn.sendControllerArrivalEvent(
                controllerNumber.toByte(),
                getActiveControllerMask(),
                usbDevice.type,
                usbDevice.supportedButtonFlags,
                usbDevice.capabilities,
            )
        }
    }

    companion object {
        private const val MAXIMUM_BUMPER_UP_DELAY_MS = 100

        private const val START_DOWN_TIME_MOUSE_MODE_MS = 750

        private const val MINIMUM_BUTTON_DOWN_TIME_MS = 25

        private const val GUIDE_TAP_MS = 50

        private const val EMULATING_SELECT = 0x2
        private const val EMULATING_TOUCHPAD = 0x4

        private const val MAX_GAMEPADS: Short = 16 // Limited by bits in activeGamepadMask

        private const val BATTERY_RECHECK_INTERVAL_MS = 120 * 1000

        private const val REMAP_IGNORE = -1
        private const val REMAP_CONSUME = -2

        private const val SENSOR_ENABLE_DELAY_MS = 1000L
        private const val MAX_SENSOR_RATE_HZ = 200
        private const val MICROSECONDS_PER_SECOND = 1000000
        private const val RUMBLE_DURATION_MS = 60000L
        private const val RAD_TO_DEG = 57.2957795f
        private const val PERCENT = 100
        private const val QUAD_VIBRATOR_COUNT = 4
        private const val RED_SHIFT = 16
        private const val RED_MASK = 0xFF0000
        private const val GREEN_SHIFT = 8
        private const val GREEN_MASK = 0xFF00
        private const val BLUE_MASK = 0xFF
        private const val OPAQUE_ALPHA = 0xFF000000.toInt()
        private const val STICK_MAX = 0x7FFE
        private const val TRIGGER_MAX = 0xFF
        private const val HAT_THRESHOLD = 0.5
        private const val DEFAULT_TRIGGER_DEADZONE = 0.13f
        private const val MAX_TRIGGER_DEADZONE = 0.30f

        private const val VENDOR_MICROSOFT = 0x045e
        private const val VENDOR_SONY = 0x054c
        private const val VENDOR_NINTENDO = 0x057e
        private const val VENDOR_8BITDO = 0x2dc8
        private const val VENDOR_HORI = 0x0f0d
        private const val VENDOR_ASUS = 0x0b05
        private const val VENDOR_GOOGLE = 0x18d1
        private const val VENDOR_THRUSTMASTER = 0x044f

        private const val PRODUCT_SWITCH_PRO = 0x2009
        private const val PRODUCT_HORIPAD_SWITCH = 0x00c1
        private const val PRODUCT_KUNAI_USB = 0x7900
        private const val PRODUCT_KUNAI_BLUETOOTH = 0x7902
        private const val PRODUCT_ADT1 = 0x2c40
        private const val PRODUCT_SCORE_A = 0xb328

        // Linux evdev codes (linux/input-event-codes.h): the scan codes of the key events
        private const val KEY_RECORD = 167
        private const val KEY_MENU = 139
        private const val BTN_SOUTH = 0x130
        private const val BTN_EAST = 0x131
        private const val BTN_C = 0x132
        private const val BTN_NORTH = 0x133
        private const val BTN_WEST = 0x134
        private const val BTN_Z = 0x135
        private const val BTN_TL = 0x136
        private const val BTN_TR = 0x137
        private const val BTN_TL2 = 0x138
        private const val BTN_TR2 = 0x139
        private const val BTN_SELECT = 0x13A
        private const val BTN_START = 0x13B
        private const val BTN_MODE = 0x13C
        private const val BTN_THUMBL = 0x13D
        private const val BTN_TRIGGER_HAPPY1 = 0x2C0
        private const val BTN_TRIGGER_HAPPY2 = 0x2C1
        private const val BTN_TRIGGER_HAPPY3 = 0x2C2
        private const val BTN_TRIGGER_HAPPY4 = 0x2C3
        private const val BTN_TRIGGER_HAPPY5 = 0x2C4
        private const val BTN_TRIGGER_HAPPY6 = 0x2C5
        private const val BTN_TRIGGER_HAPPY7 = 0x2C6
        private const val BTN_TRIGGER_HAPPY8 = 0x2C7

        // The special buttons of the ASUS ROG Kunai that map to Start and Select
        private const val KUNAI_START_1 = 264
        private const val KUNAI_SELECT_1 = 265
        private const val KUNAI_START_2 = 266
        private const val KUNAI_SELECT_2 = 267

        @SuppressLint("InlinedApi")
        private val ANDROID_TO_LI_BUTTON_MAP =
            mapOf(
                KeyEvent.KEYCODE_BUTTON_A to ControllerPacket.A_FLAG,
                KeyEvent.KEYCODE_BUTTON_B to ControllerPacket.B_FLAG,
                KeyEvent.KEYCODE_BUTTON_X to ControllerPacket.X_FLAG,
                KeyEvent.KEYCODE_BUTTON_Y to ControllerPacket.Y_FLAG,
                KeyEvent.KEYCODE_DPAD_UP to ControllerPacket.UP_FLAG,
                KeyEvent.KEYCODE_DPAD_DOWN to ControllerPacket.DOWN_FLAG,
                KeyEvent.KEYCODE_DPAD_LEFT to ControllerPacket.LEFT_FLAG,
                KeyEvent.KEYCODE_DPAD_RIGHT to ControllerPacket.RIGHT_FLAG,
                KeyEvent.KEYCODE_DPAD_UP_LEFT to
                    (ControllerPacket.UP_FLAG or ControllerPacket.LEFT_FLAG),
                KeyEvent.KEYCODE_DPAD_UP_RIGHT to
                    (ControllerPacket.UP_FLAG or ControllerPacket.RIGHT_FLAG),
                KeyEvent.KEYCODE_DPAD_DOWN_LEFT to
                    (ControllerPacket.DOWN_FLAG or ControllerPacket.LEFT_FLAG),
                KeyEvent.KEYCODE_DPAD_DOWN_RIGHT to
                    (ControllerPacket.DOWN_FLAG or ControllerPacket.RIGHT_FLAG),
                KeyEvent.KEYCODE_BUTTON_L1 to ControllerPacket.LB_FLAG,
                KeyEvent.KEYCODE_BUTTON_R1 to ControllerPacket.RB_FLAG,
                KeyEvent.KEYCODE_BUTTON_THUMBL to ControllerPacket.LS_CLK_FLAG,
                KeyEvent.KEYCODE_BUTTON_THUMBR to ControllerPacket.RS_CLK_FLAG,
                KeyEvent.KEYCODE_BUTTON_START to ControllerPacket.PLAY_FLAG,
                KeyEvent.KEYCODE_MENU to ControllerPacket.PLAY_FLAG,
                KeyEvent.KEYCODE_BUTTON_SELECT to ControllerPacket.BACK_FLAG,
                KeyEvent.KEYCODE_BACK to ControllerPacket.BACK_FLAG,
                KeyEvent.KEYCODE_BUTTON_MODE to ControllerPacket.SPECIAL_BUTTON_FLAG,

                // This is the Xbox Series X Share button
                KeyEvent.KEYCODE_MEDIA_RECORD to ControllerPacket.MISC_FLAG,

                // This is a weird one, but it's what Android does prior to 4.10 kernels
                // where DualShock/DualSense touchpads weren't mapped as separate devices.
                // https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Vendor_054c_Product_0ce6_fallback.kl
                // https://android.googlesource.com/platform/frameworks/base/+/master/data/keyboards/Vendor_054c_Product_09cc.kl
                KeyEvent.KEYCODE_BUTTON_1 to ControllerPacket.TOUCHPAD_FLAG,

                // FIXME Paddles?
            )

        // A SparseArray loop that reads size() at each step, as the for loops of the Java did
        private inline fun <T> SparseArray<T>.forEachValue(action: (T) -> Unit) {
            var i = 0
            while (i < size) {
                action(valueAt(i))
                i++
            }
        }

        private fun getMotionRangeForJoystickAxis(
            dev: InputDevice,
            axis: Int,
        ): InputDevice.MotionRange? {
            // First get the axis for SOURCE_JOYSTICK
            var range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK)
            if (range == null) {
                // Now try the axis for SOURCE_GAMEPAD
                range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD)
            }

            return range
        }

        private fun hasJoystickAxes(device: InputDevice): Boolean {
            // The rule is in Kotlin: an alphabetic keyboard with joystick axes is no pad.
            return device.hasControllerJoystickAxes()
        }

        private fun hasGamepadButtons(device: InputDevice): Boolean {
            return (device.sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
        }

        @JvmStatic
        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("ReturnCount")
        fun isGameControllerDevice(device: InputDevice?): Boolean {
            if (device == null) {
                return true
            }

            if (hasJoystickAxes(device) || hasGamepadButtons(device)) {
                // Has real joystick axes or gamepad buttons
                return true
            }

            // HACK for https://issuetracker.google.com/issues/163120692
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
                if (device.id == -1) {
                    // This "virtual" device could be input from any of the attached devices.
                    // Look to see if any gamepads are connected.
                    val ids = InputDevice.getDeviceIds()
                    for (id in ids) {
                        val dev = InputDevice.getDevice(id)
                        if (dev == null) {
                            // This device was removed during enumeration
                            continue
                        }

                        // If there are any gamepad devices connected, we'll
                        // report that this virtual device is a gamepad.
                        if (hasJoystickAxes(dev) || hasGamepadButtons(dev)) {
                            return true
                        }
                    }
                }
            }

            // Otherwise, we'll try anything that claims to be a non-alphabetic keyboard
            return device.keyboardType != InputDevice.KEYBOARD_TYPE_ALPHABETIC
        }

        @JvmStatic
        fun getAttachedControllerMask(context: Context): Short {
            return getAttachedControllerMask(
                context,
                PreferenceConfiguration.readPreferences(context),
            )
        }

        private fun getAttachedControllerMask(
            context: Context,
            prefConfig: PreferenceConfiguration,
        ): Short {
            var count = 0
            var mask: Short = 0

            // Count all input devices that are gamepads
            val im = context.getSystemService(Context.INPUT_SERVICE) as InputManager
            for (id in im.inputDeviceIds) {
                val dev = im.getInputDevice(id)
                if (dev == null) {
                    continue
                }

                if (hasJoystickAxes(dev)) {
                    LimeLog.info("Counting InputDevice: ${dev.name}")
                    mask = (mask.toInt() or (1 shl count)).toShort()
                    count++
                }
            }

            // Count all USB devices that match our drivers
            if (prefConfig.usbDriver) {
                val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager?
                if (usbManager != null) {
                    for (dev in usbManager.deviceList.values) {
                        // We explicitly check not to claim devices that appear as InputDevices
                        // otherwise we will double count them.
                        if (
                            UsbDriverService.shouldClaimDevice(dev, false) &&
                                !UsbDriverService.isRecognizedInputDevice(dev)
                        ) {
                            LimeLog.info("Counting UsbDevice: ${dev.deviceName}")
                            mask = (mask.toInt() or (1 shl count)).toShort()
                            count++
                        }
                    }
                }
            }

            if (prefConfig.onscreenController) {
                LimeLog.info("Counting OSC gamepad")
                mask = (mask.toInt() or 1).toShort()
            }

            LimeLog.info("Enumerated $count gamepads")
            return mask
        }

        private fun hasButtonUnderTouchpad(dev: InputDevice, type: Byte): Boolean {
            // It has to have a touchpad to have a button under it
            if ((dev.sources and InputDevice.SOURCE_TOUCHPAD) != InputDevice.SOURCE_TOUCHPAD) {
                return false
            }

            // Landroid/view/InputDevice;->hasButtonUnderPad()Z is blocked after O
            // We can't use the platform API, so we'll have to just guess based on the gamepad type.
            // If this is a PlayStation controller with a touchpad, we know it has a clickpad.
            return type == MoonBridge.LI_CTYPE_PS
        }

        // Converted as is from the inherited Java; refactor in a later sprint (Sprint 13 backlog).
        @Suppress("ComplexCondition", "PrintStackTrace", "ReturnCount")
        private fun isExternal(dev: InputDevice): Boolean {
            // The ASUS Tinker Board inaccurately reports Bluetooth gamepads as internal,
            // causing shouldIgnoreBack() to believe it should pass through back as a
            // navigation event for any attached gamepads.
            if (Build.MODEL == "Tinker Board") {
                return true
            }

            val deviceName = dev.name
            if (
                // This is the back button on Shield portable consoles
                deviceName.contains("gpio") ||
                    // These are the gamepad buttons on the Archos Gamepad 2
                    deviceName.contains("joy_key") ||
                    // These are gamepad buttons on the XPERIA Play
                    deviceName.contains("keypad") ||
                    // Gamepad on Shield Portable
                    deviceName.equals("NVIDIA Corporation NVIDIA Controller v01.01", true) ||
                    // Gamepad on Shield Portable (?)
                    deviceName.equals("NVIDIA Corporation NVIDIA Controller v01.02", true) ||
                    // Gamepad on Logitech G Cloud
                    deviceName.equals("GR0006", true)
            ) {
                LimeLog.info("${dev.name} is internal by hardcoded mapping")
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Landroid/view/InputDevice;->isExternal()Z is officially public on Android Q
                return dev.isExternal
            } else {
                try {
                    // Landroid/view/InputDevice;->isExternal()Z is on the light graylist in Android
                    // P
                    return dev.javaClass.getMethod("isExternal").invoke(dev) as Boolean
                } catch (e: NoSuchMethodException) {
                    e.printStackTrace()
                } catch (e: IllegalAccessException) {
                    e.printStackTrace()
                } catch (e: InvocationTargetException) {
                    e.printStackTrace()
                } catch (e: ClassCastException) {
                    e.printStackTrace()
                }
            }

            // Answer true if we don't know
            return true
        }

        private fun areBatteryCapacitiesEqual(first: Float, second: Float): Boolean {
            // With no NaNs involved, it is a simple equality comparison.
            if (!first.isNaN() && !second.isNaN()) {
                return first == second
            } else {
                // If we have a NaN in one or both positions, compare NaN-ness instead.
                // Equality comparisons will always return false for NaN.
                return first.isNaN() == second.isNaN()
            }
        }
    }
}
