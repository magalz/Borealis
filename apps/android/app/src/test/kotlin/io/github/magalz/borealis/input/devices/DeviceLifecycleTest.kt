package io.github.magalz.borealis.input.devices

import android.os.Vibrator
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

/**
 * The handler makes the context of a device when its first event arrives, keeps it while the device
 * is there, replaces it when Android says the device changed (keeping the controller number) and
 * drops it, with its controller number, when the device goes. USB-driver pads have contexts of their
 * own, made when the driver reports the pad.
 */
class DeviceLifecycleTest : DevicesTestBase() {
    private val a = ControllerPacket.A_FLAG
    private val b = ControllerPacket.B_FLAG
    private val hat = listOf(MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y).map { Axis(it) }

    @Test
    fun `the handler listens to the input manager until it stops`() {
        val rig = rig()
        assertThat(rig.registeredListeners()).contains(rig.handler)

        rig.handler.stop()

        assertThat(rig.registeredListeners()).doesNotContain(rig.handler)
    }

    @Test
    fun `a device that was added makes no context until it sends input`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))

        rig.handler.onInputDeviceAdded(device.id)

        assertThat(rig.contexts().size()).isEqualTo(0)
        assertThat(rig.connection.packets).isEmpty()
        assertThat(rig.connection.arrivals).isEmpty()
    }

    @Test
    fun `the first event of a device makes its context`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))

        val handled = rig.press(device, KEYCODE_BUTTON_A)

        assertThat(handled).isTrue()
        assertThat(rig.contexts().size()).isEqualTo(1)
        assertThat(rig.contexts().keyAt(0)).isEqualTo(1)
    }

    @Test
    fun `a device keeps its context`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        val first = rig.touch(device).context

        rig.press(device, KEYCODE_BUTTON_A)

        assertThat(rig.context(device).context).isSameInstanceAs(first)
        assertThat(rig.contexts().size()).isEqualTo(1)
    }

    @Test
    fun `a key the handler does not know is not handled but still makes the context`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))

        val handled = rig.press(device, KEYCODE_A)

        assertThat(handled).isFalse()
        assertThat(rig.contextOf(device)).isNotNull()
        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    fun `a device that is no controller still gets a context when it sends an event to the handler`() {
        val keyboard =
            pad(
                1,
                sources = android.view.InputDevice.SOURCE_KEYBOARD,
                keyboardType = android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC,
                axes = emptyList(),
            )
        val rig = rig(devices = listOf(keyboard))

        val context = rig.touch(keyboard)

        assertThat(context.bool("hasJoystickAxes")).isFalse()
    }

    @Test
    fun `an event with no device id goes to the default context`() {
        val noDevice = pad(0)
        val rig = rig(devices = listOf(noDevice))

        rig.press(noDevice, KEYCODE_BUTTON_A)

        assertThat(rig.contexts().size()).isEqualTo(0)
        // The default context has its number from the start, so it reports no arrival
        assertThat(rig.connection.arrivals).isEmpty()
        assertThat(rig.connection.packets.single())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = a))
    }

    @Test
    fun `an event of a device that has gone is consumed and makes no context`() {
        val device = pad(1)
        val rig = rig()

        // The framework is no longer listing the device and the event carries none
        val handled = rig.press(device, KEYCODE_BUTTON_A, withDevice = false)

        assertThat(handled).isTrue()
        assertThat(rig.contexts().size()).isEqualTo(0)
        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    fun `a stopped handler consumes events and makes no context`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        rig.handler.stop()

        val handled = rig.press(device, KEYCODE_BUTTON_A)

        assertThat(handled).isTrue()
        assertThat(rig.contexts().size()).isEqualTo(0)
        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 the virtual device goes to the default context`() {
        val virtual = pad(-1)
        val rig = rig(devices = listOf(virtual))

        rig.press(virtual, KEYCODE_BUTTON_A)

        assertThat(rig.contexts().size()).isEqualTo(0)
        assertThat(rig.connection.packets.single().buttons).isEqualTo(a)
        assertThat(rig.connection.arrivals).isEmpty()
    }

    @Test
    @Config(sdk = [ANDROID_12])
    fun `after Android 11 the virtual device has a context of its own`() {
        val virtual = pad(-1)
        val rig = rig(devices = listOf(virtual))

        rig.press(virtual, KEYCODE_BUTTON_A)

        assertThat(rig.contexts().size()).isEqualTo(1)
        assertThat(rig.contexts().keyAt(0)).isEqualTo(-1)
        assertThat(rig.connection.arrivals).hasSize(1)
    }

    @Test
    fun `a context made while the gamepad mouse is on has it on`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        rig.handler.setMouseEmulation(true)

        val context = rig.touch(device)

        assertThat(context.bool("mouseEmulationActive")).isTrue()
    }

    @Test
    fun `a context made while the gamepad mouse is off has it off`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        rig.handler.setMouseEmulation(true)
        rig.handler.setMouseEmulation(false)

        assertThat(rig.touch(device).bool("mouseEmulationActive")).isFalse()
    }

    @Test
    fun `removing a device drops its context`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        val old = rig.touch(device).context

        rig.disconnect(device)

        assertThat(rig.contextOf(device)).isNull()
        connectPad(device)
        assertThat(rig.touch(device).context).isNotSameInstanceAs(old)
    }

    @Test
    fun `removing a device that sent no input sends nothing`() {
        val device = pad(1)
        val rig = rig(multiController = true, devices = listOf(device))
        rig.touch(device)

        rig.disconnect(device)

        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    fun `removing a device the handler never heard of does nothing`() {
        val rig = rig()

        rig.handler.onInputDeviceRemoved(42)

        assertThat(rig.connection.packets).isEmpty()
        assertThat(rig.contexts().size()).isEqualTo(0)
    }

    @Test
    fun `removing a device cancels the vibrators of its manager`() {
        val device = pad(1)
        DeviceHardware.set(device.id, Hardware(vibrators = 2))
        val rig = rig(devices = listOf(device))
        rig.touch(device)

        rig.disconnect(device)

        assertThat(DeviceHardware.vibratorManagerCancels).containsExactly(device.id)
    }

    @Test
    fun `removing a device cancels its own vibrator`() {
        val device = pad(1, hasVibrator = true)
        val rig = rig(devices = listOf(device))
        val vibrator = checkNotNull(rig.touch(device)["vibrator"])

        rig.disconnect(device)

        assertThat(Shadow.extract<ShadowDeviceVibrator>(vibrator).cancels).isEqualTo(1)
        assertThat(DeviceHardware.vibratorManagerCancels).isEmpty()
    }

    @Test
    fun `removing an internal pad cancels the vibrator of the phone`() {
        val device = pad(1, external = false)
        val rig = rig(devices = listOf(device))
        rig.touch(device)
        val phone = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
        assertThat(shadowOf(phone).isCancelled).isFalse()

        rig.disconnect(device)

        assertThat(shadowOf(phone).isCancelled).isTrue()
    }

    @Test
    fun `removing a pad without a vibrator leaves the phone's alone`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        rig.touch(device)

        rig.disconnect(device)

        val phone = RuntimeEnvironment.getApplication().getSystemService(Vibrator::class.java)
        assertThat(shadowOf(phone).isCancelled).isFalse()
    }

    @Test
    fun `stopping cancels the vibrators of every device`() {
        val first = pad(1)
        val second = pad(2)
        DeviceHardware.set(first.id, Hardware(vibrators = 2))
        DeviceHardware.set(second.id, Hardware(vibrators = 4))
        val rig = rig(devices = listOf(first, second))
        rig.touch(first)
        rig.touch(second)

        rig.handler.stop()

        assertThat(DeviceHardware.vibratorManagerCancels).containsExactly(1, 2)
    }

    @Test
    fun `a change of a device the framework no longer lists does nothing`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        val old = rig.touch(device).context
        disconnectPad(device)

        rig.change(device)

        assertThat(rig.context(device).context).isSameInstanceAs(old)
    }

    @Test
    fun `a change of a device without a context makes none`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))

        rig.change(device)

        assertThat(rig.contexts().size()).isEqualTo(0)
    }

    @Test
    fun `a change replaces the context with one made from the device as it is now`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        val old = rig.touch(before)
        assertThat(old.axes().hatX).isEqualTo(NO_AXIS)

        // The device gains a hat, as one that has a touchpad does when it changes mode
        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        val replaced = rig.context(after)
        assertThat(replaced.context).isNotSameInstanceAs(old.context)
        assertThat(replaced.axes().hatX).isEqualTo(MotionEvent.AXIS_HAT_X)
        assertThat(replaced["inputDevice"]).isSameInstanceAs(after.device)
    }

    @Test
    fun `a change keeps the controller number and tells the host nothing`() {
        val before = pad(1)
        val other = pad(2)
        val rig = rig(multiController = true, devices = listOf(before, other))
        rig.press(other, KEYCODE_BUTTON_A)
        rig.press(before, KEYCODE_BUTTON_A)
        val packets = rig.connection.packets.size
        val arrivals = rig.connection.arrivals.size

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        val replaced = rig.context(after)
        assertThat(replaced.short("controllerNumber")).isEqualTo(1)
        assertThat(replaced.bool("assignedControllerNumber")).isTrue()
        assertThat(replaced.bool("reservedControllerNumber")).isTrue()
        assertThat(rig.connection.packets).hasSize(packets)
        assertThat(rig.connection.arrivals).hasSize(arrivals)
    }

    @Test
    fun `a change forgets the buttons that were held`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        rig.press(before, KEYCODE_BUTTON_A)

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)
        rig.press(after, KEYCODE_BUTTON_B)

        // The A button was still held; the new context never heard of it
        assertThat(rig.connection.packets.last().buttons).isEqualTo(b)
    }

    @Test
    fun `a change turns the gamepad mouse off for the device`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        rig.touch(before)
        rig.handler.setMouseEmulation(true)
        assertThat(rig.context(before).bool("mouseEmulationActive")).isTrue()

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        assertThat(rig.context(after).bool("mouseEmulationActive")).isFalse()
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `a change carries over the state the host was told about`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        val old = rig.touch(before)
        setField(old.context, "ledArgbValue", 0x112233)
        setField(old.context, "gyroReportRateHz", 100.toShort())
        setField(old.context, "accelReportRateHz", 50.toShort())
        setField(old.context, "needsClickpadEmulation", true)

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        val replaced = rig.context(after)
        assertThat(replaced.int("ledArgbValue")).isEqualTo(0x112233)
        assertThat(replaced.short("gyroReportRateHz")).isEqualTo(100)
        assertThat(replaced.short("accelReportRateHz")).isEqualTo(50)
        assertThat(replaced.bool("needsClickpadEmulation")).isTrue()
    }

    @Test
    fun `a change keeps the sensors of the phone a pad was given`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before)) { gamepadMotionSensorsFallbackToDevice = true }
        rig.press(before, KEYCODE_BUTTON_A)
        val sensors = field(rig.handler, "deviceSensorManager")
        assertThat(rig.context(before)["sensorManager"]).isSameInstanceAs(sensors)

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        assertThat(rig.context(after)["sensorManager"]).isSameInstanceAs(sensors)
    }

    @Test
    fun `a change does not give the phone's sensors to a pad that had none`() {
        val before = pad(1)
        val rig = rig(devices = listOf(before))
        rig.press(before, KEYCODE_BUTTON_A)

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        assertThat(rig.context(after)["sensorManager"]).isNull()
    }

    @Test
    fun `a change cancels the vibrators of the old context only`() {
        val before = pad(1)
        DeviceHardware.set(before.id, Hardware(vibrators = 2))
        val rig = rig(devices = listOf(before))
        rig.touch(before)

        val after = pad(1, axes = STICKS_AND_Z + hat)
        disconnectPad(before)
        connectPad(after)
        rig.change(after)

        assertThat(DeviceHardware.vibratorManagerCancels).containsExactly(1)
    }

    @Test
    fun `a USB pad has a context from the moment the driver reports it`() {
        val rig = rig { deadzonePercentage = 20 }
        assertThat(rig.handler.hasUsbPad()).isFalse()

        val usb = FakeUsbController(5, rig.handler)
        rig.handler.deviceAdded(usb)

        assertThat(rig.handler.hasUsbPad()).isTrue()
        val context = ContextView(checkNotNull(rig.usbContexts().get(5)))
        assertThat(context.int("id")).isEqualTo(5)
        assertThat(context["device"]).isSameInstanceAs(usb)
        assertThat(context.bool("external")).isTrue()
        assertThat(context.int("vendorId")).isEqualTo(0x045e)
        assertThat(context.int("productId")).isEqualTo(0x028e)
        assertThat(context.float("leftStickDeadzoneRadius")).isEqualTo(0.2f)
        assertThat(context.float("rightStickDeadzoneRadius")).isEqualTo(0.2f)
        assertThat(context.float("triggerDeadzone")).isEqualTo(0.13f)
        assertThat(context.bool("assignedControllerNumber")).isFalse()
    }

    @Test
    fun `a USB pad made while the gamepad mouse is on has it on`() {
        val rig = rig()
        rig.handler.setMouseEmulation(true)

        rig.handler.deviceAdded(FakeUsbController(5, rig.handler))

        assertThat(ContextView(checkNotNull(rig.usbContexts().get(5))).bool("mouseEmulationActive"))
            .isTrue()
    }

    @Test
    fun `a stopped handler makes no context for a USB pad`() {
        val rig = rig()
        rig.handler.stop()

        rig.handler.deviceAdded(FakeUsbController(5, rig.handler))

        assertThat(rig.handler.hasUsbPad()).isFalse()
    }

    @Test
    fun `removing a USB pad drops its context`() {
        val rig = rig()
        val usb = FakeUsbController(5, rig.handler)
        rig.handler.deviceAdded(usb)
        usb.report(a)

        rig.handler.deviceRemoved(usb)

        assertThat(rig.handler.hasUsbPad()).isFalse()
        assertThat(rig.connection.packets.last())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = 0))
    }

    @Test
    fun `removing a USB pad that was never added does nothing`() {
        val rig = rig()

        rig.handler.deviceRemoved(FakeUsbController(5, rig.handler))

        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    fun `a report of a USB pad the handler does not have is ignored`() {
        val rig = rig()

        FakeUsbController(5, rig.handler).report(a)

        assertThat(rig.connection.packets).isEmpty()
        assertThat(rig.connection.arrivals).isEmpty()
    }

    @Test
    fun `a USB pad and a system pad of the same id do not share a context`() {
        val device = pad(5)
        val rig = rig(devices = listOf(device))
        val usb = FakeUsbController(5, rig.handler)
        rig.handler.deviceAdded(usb)

        rig.touch(device)

        assertThat(rig.contexts().size()).isEqualTo(1)
        assertThat(rig.usbContexts().size()).isEqualTo(1)
    }

    private companion object {
        const val ANDROID_11 = 30
        const val ANDROID_12 = 31
    }
}
