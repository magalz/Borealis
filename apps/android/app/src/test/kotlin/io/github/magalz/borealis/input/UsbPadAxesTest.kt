package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The triggers of a pad on the USB driver, which reports floats: a value at or below the trigger
 * deadzone of 13% is zero, and a larger one is its value times 255 in a byte.
 */
@RunWith(RobolectricTestRunner::class)
class UsbPadAxesTest {
    private val rig = ControllerHandlerRig()

    init {
        rig.handler.deviceAdded(FakeUsbController(PAD_ID, rig.handler))
    }

    private fun report(leftTrigger: Float, rightTrigger: Float): Packet {
        rig.handler.reportControllerState(PAD_ID, 0, 0f, 0f, 0f, 0f, leftTrigger, rightTrigger)
        return rig.connection.packets.last()
    }

    @Test
    fun `a trigger at the deadzone is zero and one just above it is sent`() {
        assertThat(report(0.13f, 0.13f)).isEqualTo(Packet(0, 0))

        assertThat(report(0.14f, 0.5f))
            .isEqualTo(Packet(0, 0, leftTrigger = 35, rightTrigger = 127))
    }

    private companion object {
        const val PAD_ID = 1
    }
}
