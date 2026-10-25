package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.inputDeviceListeners
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The life of the handler: it starts a thread for the work that can block (batteries, sensors,
 * LEDs), listens for input devices, and gives all of it back on `stop` and `destroy`. `stop` is
 * the end of the input and is safe to repeat; `destroy` also ends the thread.
 */
@RunWith(RobolectricTestRunner::class)
class HandlerLifecycleTest : FeedbackTestBase() {
    @Test
    fun `the handler works on a thread of its own with a name`() {
        val rig = rig()

        assertThat(rig.backgroundThread.name).isEqualTo("ControllerHandler")
        assertThat(rig.backgroundThread.isAlive).isTrue()
        assertThat(rig.backgroundThread.isDaemon).isFalse()
        assertThat(rig.backgroundThread).isNotSameInstanceAs(Thread.currentThread())
    }

    @Test
    fun `each handler has a thread of its own`() {
        val first = rig()
        val second = rig()

        assertThat(first.backgroundThread).isNotSameInstanceAs(second.backgroundThread)
    }

    @Test
    fun `the handler listens for input devices from the start`() {
        val rig = rig()

        assertThat(inputDeviceListeners().any { it === rig.handler }).isTrue()
    }

    @Test
    fun `stopping ends the listening`() {
        val rig = rig()

        rig.handler.stop()

        assertThat(inputDeviceListeners().any { it === rig.handler }).isFalse()
    }

    @Test
    fun `stopping leaves the thread running`() {
        val rig = rig()

        rig.handler.stop()

        assertThat(rig.backgroundThread.isAlive).isTrue()
    }

    @Test
    fun `stopping cancels the vibration of the phone`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)

        rig.handler.stop()

        assertThat(Hardware.calls(phone)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `stopping twice cancels once`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)

        rig.handler.stop()
        rig.handler.stop()

        assertThat(Hardware.calls(phone)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `stopping cancels the vibrator of a pad`() {
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = own)))
        rig.rumble(0x6400, 0x6400)

        rig.handler.stop()

        assertThat(Hardware.calls(own).last()).isEqualTo(VibratorCall.Cancel)
    }

    @Test
    fun `stopping cancels the vibrator manager of a pad and not its vibrators`() {
        val a = Hardware.vibrator()
        val b = Hardware.vibrator()
        val manager = Hardware.vibratorManager(listOf(a, b))
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.handler.stop()

        assertThat(Hardware.manager(manager).cancels).isEqualTo(1)
        assertThat(Hardware.calls(a)).isEmpty()
        assertThat(Hardware.calls(b)).isEmpty()
    }

    @Test
    fun `a pad that goes away cancels its vibration`() {
        val own = Hardware.vibrator()
        val rig = rig()
        val pad = rig.connectPad(features = DeviceFeatures(vibrator = own))
        rig.wake(pad)

        rig.handler.onInputDeviceRemoved(pad.id)

        assertThat(Hardware.calls(own)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `stopping cancels the vibrators of every pad`() {
        val first = Hardware.vibrator()
        val second = Hardware.vibrator()
        val rig = rig { multiController = true }
        rig.wake(rig.connectPad(id = 5, features = DeviceFeatures(vibrator = first)))
        rig.wake(rig.connectPad(id = 6, features = DeviceFeatures(vibrator = second)))

        rig.handler.stop()

        assertThat(Hardware.calls(first)).containsExactly(VibratorCall.Cancel)
        assertThat(Hardware.calls(second)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `a stopped handler sends nothing for a pad`() {
        val rig = rig()
        val pad = rig.connectPad()
        rig.handler.stop()

        rig.press(pad, android.view.KeyEvent.KEYCODE_BUTTON_A)

        assertThat(rig.connection.packets).isEmpty()
        assertThat(rig.connection.arrivals).isEmpty()
    }

    @Test
    fun `destroying a handler that was not stopped stops it first`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)

        rig.destroy()

        assertThat(Hardware.calls(phone)).containsExactly(VibratorCall.Cancel)
        assertThat(inputDeviceListeners().any { it === rig.handler }).isFalse()
    }

    @Test
    fun `destroying a handler that was stopped does not stop it again`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)
        rig.handler.stop()

        rig.destroy()

        assertThat(Hardware.calls(phone)).containsExactly(VibratorCall.Cancel)
    }

    @Test
    fun `a device that Android adds is not touched until it sends input`() {
        val own = Hardware.vibrator()
        val rig = rig()
        val pad = rig.connectPad(features = DeviceFeatures(vibrator = own))

        rig.handler.onInputDeviceAdded(pad.id)
        rig.rumble(0x6400, 0x6400)

        assertThat(rig.connection.arrivals).isEmpty()
        assertThat(Hardware.calls(own)).isEmpty()
    }

    @Test
    fun `destroying ends the thread`() {
        val rig = rig()
        val thread = rig.backgroundThread

        rig.destroy()

        assertThat(thread.isAlive).isFalse()
    }
}
