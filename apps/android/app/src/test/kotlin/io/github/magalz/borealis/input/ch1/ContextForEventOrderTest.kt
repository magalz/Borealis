package io.github.magalz.borealis.input.ch1

import android.view.KeyEvent.KEYCODE_BUTTON_A
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import io.github.magalz.borealis.input.devices.DevicesTestBase
import io.github.magalz.borealis.input.devices.Sent
import io.github.magalz.borealis.input.devices.pad
import io.github.magalz.borealis.testing.ANDROID_11
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The handler picks the context of an event by the first rule that fits: a stopped handler has
 * none, then device 0 is the default context, then an event with no device has none, then Android
 * 11's virtual device is the default context, and any other device has its own, made on its first
 * event. The order decides the cases where two rules fit, which these tests pin.
 */
class ContextForEventOrderTest : DevicesTestBase() {
    private val a = ControllerPacket.A_FLAG

    @Test
    fun `a stopped handler consumes the events of device 0 too`() {
        val noDevice = pad(0)
        val rig = rig()
        rig.handler.stop()

        val handled = rig.press(noDevice, KEYCODE_BUTTON_A)

        assertThat(handled).isTrue()
        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    fun `an event of device 0 goes to the default context though no device is attached`() {
        val noDevice = pad(0)
        val rig = rig()

        rig.press(noDevice, KEYCODE_BUTTON_A, withDevice = false)

        assertThat(rig.contexts().size()).isEqualTo(0)
        assertThat(rig.connection.packets.single())
            .isEqualTo(Sent(controller = 0, activeMask = 1, buttons = a))
    }

    @Test
    @Config(sdk = [ANDROID_11])
    fun `on Android 11 an event of the virtual device with no device attached is consumed`() {
        val virtual = pad(-1)
        val rig = rig()

        val handled = rig.press(virtual, KEYCODE_BUTTON_A, withDevice = false)

        assertThat(handled).isTrue()
        assertThat(rig.contexts().size()).isEqualTo(0)
        assertThat(rig.connection.packets).isEmpty()
    }

    @Test
    fun `a device keeps the context its first event made`() {
        val device = pad(1)
        val rig = rig(devices = listOf(device))
        val first = rig.touch(device).context

        val second = rig.touch(device).context

        assertThat(second).isSameInstanceAs(first)
        assertThat(rig.contexts().size()).isEqualTo(1)
    }
}
