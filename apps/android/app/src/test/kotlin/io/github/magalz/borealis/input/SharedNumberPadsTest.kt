package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Two USB pads on one controller number (multiple controllers are off): the host gets the value of
 * larger magnitude of each trigger and each stick, not the bits of both pads ORed.
 */
@RunWith(RobolectricTestRunner::class)
class SharedNumberPadsTest {
    @get:Rule val rig = ControllerHandlerRig()

    init {
        rig.handler.deviceAdded(FakeUsbController(FIRST, rig.handler))
        rig.handler.deviceAdded(FakeUsbController(SECOND, rig.handler))
    }

    private fun reportTriggers(pad: Int, leftTrigger: Float, rightTrigger: Float): Packet {
        rig.handler.reportControllerState(pad, 0, 0f, 0f, 0f, 0f, leftTrigger, rightTrigger)
        return rig.connection.packets.last()
    }

    @Test
    fun `the stronger trigger of two pads goes to the host, whichever pad reported last`() {
        // As trigger bytes: 51 and 76 from the first pad, 102 and 153 from the second. 51 and 102
        // are 119 as bits, and 76 and 153 are 221.
        reportTriggers(FIRST, 0.2f, 0.3f)
        val second = reportTriggers(SECOND, 0.4f, 0.6f)

        assertThat(second)
            .isEqualTo(Packet(0, 0, leftTrigger = 102, rightTrigger = 153.toByte().toInt()))

        // The first pad presses harder on the left and lets go of the right: 229 beats 102, and the
        // second pad's 153 stays.
        val first = reportTriggers(FIRST, 0.9f, 0f)

        assertThat(first)
            .isEqualTo(
                Packet(
                    0,
                    0,
                    leftTrigger = 229.toByte().toInt(),
                    rightTrigger = 153.toByte().toInt(),
                )
            )
    }

    private fun reportSticks(
        pad: Int,
        leftX: Float,
        leftY: Float,
        rightX: Float,
        rightY: Float,
    ): Packet {
        rig.handler.reportControllerState(pad, 0, leftX, leftY, rightX, rightY, 0f, 0f)
        return rig.connection.packets.last()
    }

    @Test
    fun `the stick of larger magnitude of two pads goes to the host, axis by axis`() {
        // As stick shorts 0.25 is 8191 and 0.5 is 16383, and Y is upside down: 0.5 up is -16383.
        // 8191 and -16383 are -8193 as bits, and -8191 and 16383 are -1.
        reportSticks(FIRST, 0.25f, 0.5f, -0.25f, 0.5f)
        val packet = reportSticks(SECOND, -0.5f, 0.25f, 0.5f, -0.25f)

        // Left X and right X: the second pad is the stronger. Left Y and right Y: the first is.
        assertThat(packet)
            .isEqualTo(
                Packet(
                    0,
                    0,
                    leftStickX = -16383,
                    leftStickY = -16383,
                    rightStickX = 16383,
                    rightStickY = -16383,
                )
            )
    }

    private companion object {
        const val FIRST = 1
        const val SECOND = 2
    }
}
