package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Whether the app's USB driver holds a pad. Android has no input device for such a pad, so this is
 * the only way for the stream to know it is there.
 */
@RunWith(RobolectricTestRunner::class)
class UsbPadHeldTest {
    @get:Rule val rig = ControllerHandlerRig()

    @Test
    fun `no pad is held before the driver adds one`() {
        assertThat(rig.handler.hasUsbPad()).isFalse()
    }

    @Test
    fun `a pad the driver adds is held`() {
        rig.handler.deviceAdded(FakeUsbController(1, rig.handler))

        assertThat(rig.handler.hasUsbPad()).isTrue()
    }

    @Test
    fun `a pad the driver lets go is no longer held`() {
        val pad = FakeUsbController(1, rig.handler)
        rig.handler.deviceAdded(pad)

        rig.handler.deviceRemoved(pad)

        assertThat(rig.handler.hasUsbPad()).isFalse()
    }

    @Test
    fun `a pad is still held while another one goes`() {
        val first = FakeUsbController(1, rig.handler)
        rig.handler.deviceAdded(first)
        rig.handler.deviceAdded(FakeUsbController(2, rig.handler))

        rig.handler.deviceRemoved(first)

        assertThat(rig.handler.hasUsbPad()).isTrue()
    }

    @Test
    fun `a pad on the system, which the handler reaches through Android, is not a USB pad`() {
        rig.useSystemPad()

        assertThat(rig.handler.hasUsbPad()).isFalse()
    }
}
