package io.github.magalz.borealis.input.events

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A pad that comes after another on the same controller number, a USB pad or the on-screen
 * controller: when its trigger or stick is the stronger, the host gets that value as it is and not
 * the bits of both ORed. The fusing block of `transmitControllerInputLocked` has one copy for
 * system pads, one for USB pads and one for the on-screen controller, so each gets its own tests;
 * the system pads' own are in [CombiningCharacterizationTest].
 */
@RunWith(RobolectricTestRunner::class)
class SharedNumberStrongestTest {
    @get:Rule val rig = EventsRig()

    @Test
    fun `a stick of a USB pad that is stronger than the one of a system pad is sent as it is`() {
        rig.addPad(
            7,
            controllerNumber = 0,
            fields =
                mapOf(
                    "leftStickX" to 430.toShort(),
                    "leftStickY" to (-300).toShort(),
                    "rightStickX" to (-243).toShort(),
                    "rightStickY" to (-4000).toShort(),
                ),
        )
        rig.handler.deviceAdded(FakeUsbController(1, rig.handler))

        // The USB values are floats. As sticks 0.09 is 2948, -0.04 is 1310 (Y is upside down),
        // 0.03 is 982 and -0.1 is 3276. The USB pad is the stronger on the first three, and the
        // system pad on right Y.
        rig.handler.reportControllerState(1, 0, 0.09f, -0.04f, 0.03f, -0.1f, 0f, 0f)

        assertThat(rig.packets.last())
            .isEqualTo(
                Sent(
                    0,
                    1,
                    0,
                    leftStickX = 2948,
                    leftStickY = 1310,
                    rightStickX = 982,
                    rightStickY = -4000,
                )
            )
    }

    @Test
    fun `a stick of the on-screen controller that is stronger than the one of a pad is sent as it is`() {
        rig.addPad(
            7,
            controllerNumber = 0,
            fields =
                mapOf(
                    "leftStickX" to 1000.toShort(),
                    "leftStickY" to (-300).toShort(),
                    "rightStickX" to 243.toShort(),
                    "rightStickY" to 4000.toShort(),
                ),
        )

        // The on-screen controller is the stronger on the first three, and the pad on right Y.
        rig.handler.reportOscState(0, 3016, 1841, -1986, -183, 0, 0)

        assertThat(rig.packets.last())
            .isEqualTo(
                Sent(
                    0,
                    1,
                    0,
                    leftStickX = 3016,
                    leftStickY = 1841,
                    rightStickX = -1986,
                    rightStickY = 4000,
                )
            )
    }

    @Test
    fun `a trigger of a USB pad that is stronger than the one of a system pad is sent as it is`() {
        rig.addPad(
            7,
            controllerNumber = 0,
            fields = mapOf("leftTrigger" to 17.toByte(), "rightTrigger" to 34.toByte()),
        )
        rig.handler.deviceAdded(FakeUsbController(1, rig.handler))

        // The USB values are floats: 0.4 is 102 and 0.8 is 204 as a trigger byte. 17 and 102 are
        // 119 as bits, 34 and 204 are 238.
        rig.handler.reportControllerState(1, 0, 0f, 0f, 0f, 0f, 0.4f, 0.8f)

        assertThat(rig.packets.last())
            .isEqualTo(Sent(0, 1, 0, leftTrigger = 102, rightTrigger = 204.toByte().toInt()))
    }

    @Test
    fun `a trigger of the on-screen controller that is stronger than the one of a pad is sent as it is`() {
        rig.addPad(
            7,
            controllerNumber = 0,
            fields = mapOf("leftTrigger" to 17.toByte(), "rightTrigger" to 34.toByte()),
        )

        rig.handler.reportOscState(0, 0, 0, 0, 0, 102, 204.toByte())

        assertThat(rig.packets.last())
            .isEqualTo(Sent(0, 1, 0, leftTrigger = 102, rightTrigger = 204.toByte().toInt()))
    }
}
