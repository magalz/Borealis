package io.github.magalz.borealis.input.events

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_X
import android.view.KeyEvent.KEYCODE_DPAD_DOWN
import android.view.KeyEvent.KEYCODE_DPAD_LEFT
import android.view.KeyEvent.KEYCODE_DPAD_RIGHT
import android.view.KeyEvent.KEYCODE_DPAD_UP
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.B_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.X_FLAG
import com.limelight.nvstream.input.ControllerPacket.Y_FLAG
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * `transmitControllerInputLocked`: what the handler sends when several contexts share a controller
 * number (a pad that is several devices, a pad and the on-screen controller), how it picks the
 * strongest stick and trigger, the gamepad mask of the packet, the mouse a pad in mouse mode makes
 * of its buttons, and `reportOscState` of the on-screen controller.
 *
 * Pads here are system pads with no axes: an empty motion event sends the packet of a pad, which
 * takes its stick and trigger values from the fields a test sets.
 */
@RunWith(RobolectricTestRunner::class)
class CombiningCharacterizationTest {
    private val rig = EventsRig { gamepadMouseSpeed = 100; analogStickForScrolling = AnalogStickForScrolling.RIGHT }

    /** Sends the packet of a pad without changing anything of it. */
    private fun Pad.send(): Sent {
        motion(mapOf())
        return rig.packets.last()
    }

    // ----- Contexts that share a controller number -----

    @Test
    fun `two pads on one controller number send the buttons of both in one packet`() {
        val first = rig.addPad(7, controllerNumber = 1)
        val second = rig.addPad(8, controllerNumber = 1)

        first.press(KEYCODE_BUTTON_A)
        second.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets)
            .containsExactly(Sent(1, 1, A_FLAG), Sent(1, 1, A_FLAG or B_FLAG))
            .inOrder()
    }

    @Test
    fun `each pad's release leaves the buttons of the other`() {
        val first = rig.addPad(7, controllerNumber = 1)
        val second = rig.addPad(8, controllerNumber = 1)
        first.press(KEYCODE_BUTTON_A)
        second.press(KEYCODE_BUTTON_B)
        rig.advanceBy(100)

        first.release(KEYCODE_BUTTON_A)

        assertThat(rig.packets.last()).isEqualTo(Sent(1, 1, B_FLAG))
    }

    @Test
    fun `pads on different controller numbers do not share a packet`() {
        val first = rig.addPad(7, controllerNumber = 1)
        val second = rig.addPad(8, controllerNumber = 2)

        first.press(KEYCODE_BUTTON_A)
        second.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets).containsExactly(Sent(1, 1, A_FLAG), Sent(2, 1, B_FLAG)).inOrder()
    }

    @Test
    fun `a pad with no controller number yet is left out of the packet of the others`() {
        val assigned = rig.addPad(7, controllerNumber = 1)
        rig.addPad(
            8,
            controllerNumber = 1,
            fields = mapOf("assignedControllerNumber" to false, "inputMap" to B_FLAG, "leftStickX" to 500.toShort()),
        )

        assertThat(assigned.send()).isEqualTo(Sent(1, 1, 0))
    }

    @Test
    fun `the on-screen controller is part of controller number 0, the buttons of a pad on that number join it`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        rig.handler.reportOscState(A_FLAG, 100, 0, 0, 0, 0, 0)

        pad.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG or B_FLAG, leftStickX = 100))
    }

    @Test
    fun `the on-screen controller stays out of the packets of the other controller numbers`() {
        val pad = rig.addPad(7, controllerNumber = 2)
        rig.handler.reportOscState(A_FLAG, 100, 0, 0, 0, 5, 0)

        pad.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets.last()).isEqualTo(Sent(2, 1, B_FLAG))
    }

    @Test
    fun `the on-screen controller takes the number of its context, which a test can move`() {
        rig.default.set("controllerNumber", 2.toShort())
        val pad = rig.addPad(7, controllerNumber = 2)

        rig.handler.reportOscState(X_FLAG, 0, 0, 0, 0, 0, 0)
        pad.press(KEYCODE_BUTTON_B)

        assertThat(rig.packets.map { it.controller to it.buttons })
            .containsExactly(2 to X_FLAG, 2 to (X_FLAG or B_FLAG))
            .inOrder()
    }

    @Test
    fun `the on-screen controller is added even for a pad in mouse mode, which then clicks the mouse for it`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        rig.handler.setMouseEmulation(true)
        rig.handler.reportOscState(A_FLAG, 0, 0, 0, 0, 0, 0)

        pad.send()

        assertThat(rig.connection.mouse).containsExactly("down 1")
    }

    @Test
    fun `a pad in mouse mode and a pad that is not do not share a packet, apart from the on-screen controller`() {
        val gamepad = rig.addPad(7, controllerNumber = 0)
        val mouse =
            rig.addPad(
                8,
                controllerNumber = 0,
                fields = mapOf("mouseEmulationActive" to true, "inputMap" to X_FLAG, "leftStickX" to 9.toShort()),
            )

        gamepad.press(KEYCODE_BUTTON_A)

        // Only the gamepad's own buttons: the pad in mouse mode is not part of its packet.
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG))
        assertThat(mouse.send()).isEqualTo(Sent(0, 1, 0))
    }

    @Test
    fun `a system pad and a USB pad on one controller number add up`() {
        val system = rig.addPad(7, controllerNumber = 0)
        val usb = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }

        system.press(KEYCODE_BUTTON_A)
        usb.report(B_FLAG, leftStickX = 1f)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG or B_FLAG, leftStickX = 32766))
        system.press(KEYCODE_BUTTON_X)
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG or B_FLAG or X_FLAG, leftStickX = 32766))
    }

    // ----- The strongest stick and trigger -----

    private fun twoPads(
        first: Map<String, Any?>,
        second: Map<String, Any?>,
    ): Pair<Pad, Pad> =
        rig.addPad(7, controllerNumber = 1, fields = first) to
            rig.addPad(8, controllerNumber = 1, fields = second)

    @Test
    fun `a stick that only one of the pads moves is sent as it is`() {
        val (first, second) =
            twoPads(
                mapOf("leftStickX" to 1000.toShort(), "rightStickY" to (-777).toShort()),
                mapOf(),
            )

        assertThat(first.send()).isEqualTo(Sent(1, 1, 0, leftStickX = 1000, rightStickY = -777))
        assertThat(second.send()).isEqualTo(Sent(1, 1, 0, leftStickX = 1000, rightStickY = -777))
    }

    @Test
    fun `two pads with the same stick value send that value`() {
        val (first, _) = twoPads(mapOf("leftStickY" to 100.toShort()), mapOf("leftStickY" to 100.toShort()))

        assertThat(first.send().leftStickY).isEqualTo(100)
    }

    @Test
    fun `the strongest of two stick values is ORed into the packet, so the sum of bits can differ from both`() {
        // 1000 then -3000: the second is stronger, and the packet holds 1000 OR -3000, not -3000.
        val (first, _) =
            twoPads(
                mapOf("leftStickX" to 1000.toShort(), "leftStickY" to (-3000).toShort(), "rightStickX" to 1000.toShort(), "rightStickY" to (-1000).toShort()),
                mapOf("leftStickX" to (-3000).toShort(), "leftStickY" to 1000.toShort(), "rightStickX" to (-1000).toShort(), "rightStickY" to 1000.toShort()),
            )

        val sent = first.send()

        assertThat(sent.leftStickX).isEqualTo(-2072)
        // The other order gives the stronger value back: it is the first that is ORed in.
        assertThat(sent.leftStickY).isEqualTo(-3000)
        // Equal strength takes the later pad's value, ORed with the earlier one.
        assertThat(sent.rightStickX).isEqualTo(-8)
        assertThat(sent.rightStickY).isEqualTo(-1000 or 1000)
    }

    @Test
    fun `the strongest of two triggers is ORed the same way`() {
        val (first, _) =
            twoPads(
                mapOf("leftTrigger" to 1.toByte(), "rightTrigger" to (-1).toByte()),
                mapOf("leftTrigger" to 2.toByte(), "rightTrigger" to 127.toByte()),
            )

        val sent = first.send()

        // 1 then 2: 1 OR 2.
        assertThat(sent.leftTrigger).isEqualTo(3)
        // -1 is 255 as an unsigned byte but the weakest magnitude of the two; the OR is -1 anyway.
        assertThat(sent.rightTrigger).isEqualTo(-1)
    }

    @Test
    fun `a stick at the lowest short has the strongest magnitude`() {
        val (first, _) =
            twoPads(mapOf("leftStickX" to 32767.toShort()), mapOf("leftStickX" to Short.MIN_VALUE))

        assertThat(first.send().leftStickX).isEqualTo(-1)
    }

    @Test
    fun `two triggers of equal strength take the later pad's, ORed with the earlier one`() {
        val (first, _) =
            twoPads(
                mapOf("leftTrigger" to 5.toByte(), "rightTrigger" to (-100).toByte()),
                mapOf("leftTrigger" to (-5).toByte(), "rightTrigger" to 100.toByte()),
            )

        val sent = first.send()

        assertThat(sent.leftTrigger).isEqualTo(-1)
        assertThat(sent.rightTrigger).isEqualTo(-100 or 100)
    }

    @Test
    fun `two system pads, a USB pad and the on-screen controller on one number are ORed in that order`() {
        rig.addPad(
            7,
            controllerNumber = 0,
            fields =
                mapOf(
                    "inputMap" to A_FLAG,
                    "leftTrigger" to 5.toByte(),
                    "rightTrigger" to 3.toByte(),
                    "leftStickX" to 400.toShort(),
                    "leftStickY" to (-300).toShort(),
                    "rightStickX" to 250.toShort(),
                    "rightStickY" to 900.toShort(),
                ),
        )
        rig.addPad(
            8,
            controllerNumber = 0,
            fields =
                mapOf(
                    "inputMap" to B_FLAG,
                    "leftTrigger" to 20.toByte(),
                    "rightTrigger" to 9.toByte(),
                    "leftStickX" to 800.toShort(),
                    "leftStickY" to (-700).toShort(),
                    "rightStickX" to 500.toShort(),
                    "rightStickY" to (-1200).toShort(),
                ),
        )
        rig.handler.deviceAdded(FakeUsbController(1, rig.handler))
        // The USB values are floats: 0.15 is 38 and 0.2 is 51 as triggers, 0.05 is 1638 as a stick.
        rig.handler.reportControllerState(1, X_FLAG, 0.05f, -0.06f, 0.07f, 0.08f, 0.15f, 0.2f)

        rig.handler.reportOscState(Y_FLAG, 3000, -2500, -4000, 5000, 100, 70)

        assertThat(rig.packets.last())
            .isEqualTo(
                Sent(
                    controller = 0,
                    activeMask = 1,
                    buttons = A_FLAG or B_FLAG or X_FLAG or Y_FLAG,
                    leftTrigger = 119,
                    rightTrigger = 127,
                    leftStickX = 4094,
                    leftStickY = -3,
                    rightStickX = -1537,
                    rightStickY = -33,
                )
            )
    }

    // ----- The gamepad mask of the packet -----

    @Test
    fun `without multiple controllers the mask is always player 1`() {
        val pad = rig.addPad(7, controllerNumber = 3)
        rig.handler.javaClass.getDeclaredField("currentControllers").apply { isAccessible = true }.setShort(rig.handler, 6)

        assertThat(pad.send().activeMask).isEqualTo(1)
    }

    @Test
    fun `with multiple controllers the mask is the controllers in use, the ones at the start and the on-screen one`() {
        val multi = EventsRig(multiController = true)
        val pad = multi.addPad(7, controllerNumber = 3)
        fun setMask(name: String, value: Int) =
            multi.handler.javaClass.getDeclaredField(name).apply { isAccessible = true }.setShort(multi.handler, value.toShort())
        fun mask(): Int {
            pad.motion(mapOf())
            return multi.packets.last().activeMask
        }
        setMask("currentControllers", 0b0110)
        setMask("initialControllers", 0b1000)

        assertThat(mask()).isEqualTo(0b1110)

        multi.prefs.onscreenController = true
        assertThat(mask()).isEqualTo(0b1111)

        setMask("currentControllers", 0x4000)
        setMask("initialControllers", 0x8000)
        assertThat(mask()).isEqualTo(0xC001.toShort().toInt())
    }

    // ----- A pad in mouse mode -----

    private fun mouseModePad(): Pad {
        val pad = rig.addPad(7, controllerNumber = 0)
        rig.handler.setMouseEmulation(true)
        return pad
    }

    @Test
    fun `A and B are the left and right mouse buttons, down and up`() {
        val pad = mouseModePad()

        pad.press(KEYCODE_BUTTON_A)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_A)
        pad.press(KEYCODE_BUTTON_B)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_B)

        assertThat(rig.connection.mouse)
            .containsExactly("down 1", "up 1", "down 3", "up 3")
            .inOrder()
    }

    @Test
    fun `a button that is held does not click again, and each one goes up on its own`() {
        val pad = mouseModePad()

        pad.press(KEYCODE_BUTTON_A)
        pad.press(KEYCODE_BUTTON_A, repeat = 1)
        pad.press(KEYCODE_BUTTON_B)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_A)

        assertThat(rig.connection.mouse).containsExactly("down 1", "down 3", "up 1").inOrder()
    }

    @Test
    fun `the D-pad scrolls one click when it goes down, up and left being the positive directions`() {
        val pad = mouseModePad()

        for (keyCode in listOf(KEYCODE_DPAD_UP, KEYCODE_DPAD_DOWN, KEYCODE_DPAD_RIGHT, KEYCODE_DPAD_LEFT)) {
            pad.press(keyCode)
            rig.advanceBy(100)
            pad.release(keyCode)
        }

        assertThat(rig.connection.mouse)
            .containsExactly("scroll 1", "scroll -1", "hscroll 1", "hscroll -1")
            .inOrder()
    }

    @Test
    fun `the other buttons do nothing to the mouse and none of the buttons reach the game`() {
        val pad = mouseModePad()

        pad.press(KEYCODE_BUTTON_X)
        pad.press(KEYCODE_BUTTON_L1)
        pad.press(KEYCODE_BUTTON_A)

        assertThat(rig.connection.mouse).containsExactly("down 1")
        assertThat(rig.packets).hasSize(3)
        assertThat(rig.packets.all { it == Sent(0, 1, 0) }).isTrue()
    }

    @Test
    fun `a pad in mouse mode sends a neutral packet, whatever its sticks and triggers hold`() {
        val pad = mouseModePad()
        pad.set("leftStickX", 4000.toShort())
        pad.set("rightTrigger", 9.toByte())

        assertThat(pad.send()).isEqualTo(Sent(0, 1, 0))
    }

    @Test
    fun `releasing the buttons that were held before the mouse mode came on sends no click`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        pad.press(KEYCODE_BUTTON_A)
        rig.handler.setMouseEmulation(true)
        rig.advanceBy(100)

        pad.release(KEYCODE_BUTTON_A)

        // The mouse side saw no press, so it sees no change in A: the A that is up is the first it knows.
        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a button held when the mouse mode came on clicks at the next event of the pad`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        pad.press(KEYCODE_BUTTON_A)
        rig.handler.setMouseEmulation(true)
        assertThat(rig.connection.mouse).isEmpty()

        pad.press(KEYCODE_BUTTON_X)

        assertThat(rig.connection.mouse).containsExactly("down 1")
    }

    // ----- The report period of the gamepad mouse -----

    @Test
    fun `the stick moves the pointer on a tick every 16 ms, the first one 16 ms after the mouse came on`() {
        val pad = rig.addPad(7, controllerNumber = 0, fields = mapOf("leftStickX" to 32766.toShort()))
        rig.handler.setMouseEmulation(true)

        rig.advanceBy(15)
        val before = rig.connection.mouse.size
        rig.advanceBy(1)
        val first = rig.connection.mouse.size
        rig.advanceBy(15)
        val before2 = rig.connection.mouse.size
        rig.advanceBy(1)
        val second = rig.connection.mouse.size
        rig.advanceBy(128)
        val tenth = rig.connection.mouse.size

        assertThat(before).isEqualTo(0)
        assertThat(first).isEqualTo(1)
        assertThat(before2).isEqualTo(1)
        assertThat(second).isEqualTo(2)
        assertThat(tenth).isEqualTo(10)
        assertThat(pad.getBoolean("mouseEmulationActive")).isTrue()
    }

    @Test
    fun `the ticks stop when the mouse mode goes off`() {
        rig.addPad(7, controllerNumber = 0, fields = mapOf("leftStickX" to 32766.toShort()))
        rig.handler.setMouseEmulation(true)
        rig.advanceBy(64)
        val moves = rig.connection.mouse.size

        rig.handler.setMouseEmulation(false)
        rig.advanceBy(160)

        assertThat(moves).isEqualTo(4)
        assertThat(rig.connection.mouse).hasSize(moves)
    }

    @Test
    fun `a stick that is not held moves nothing on the ticks`() {
        rig.addPad(7, controllerNumber = 0)
        rig.handler.setMouseEmulation(true)

        rig.advanceBy(160)

        assertThat(rig.connection.mouse).isEmpty()
    }

    // ----- The on-screen controller -----

    @Test
    fun `the on-screen controller state goes to the packet as it was reported`() {
        rig.handler.reportOscState(
            A_FLAG or Y_FLAG,
            Short.MIN_VALUE,
            Short.MAX_VALUE,
            1,
            (-2).toShort(),
            (-1).toByte(),
            127,
        )

        assertThat(rig.packets)
            .containsExactly(
                Sent(
                    controller = 0,
                    activeMask = 1,
                    buttons = A_FLAG or Y_FLAG,
                    leftTrigger = -1,
                    rightTrigger = 127,
                    leftStickX = -32768,
                    leftStickY = 32767,
                    rightStickX = 1,
                    rightStickY = -2,
                )
            )
    }

    @Test
    fun `each report replaces the state, it is not added to it`() {
        rig.handler.reportOscState(A_FLAG, 100, 200, 300, 400, 5, 6)

        rig.handler.reportOscState(B_FLAG, 0, 0, 0, 0, 0, 0)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, B_FLAG))
    }

    @Test
    fun `a report while the chrome has the input is dropped and not kept`() {
        rig.handler.setCaptured(true)
        rig.connection.packets.clear()

        rig.handler.reportOscState(A_FLAG, 100, 200, 300, 400, 5, 6)

        assertThat(rig.packets).isEmpty()
        assertThat(rig.default.inputMap).isEqualTo(0)
        assertThat(rig.default.getShort("leftStickX")).isEqualTo(0)
        assertThat(rig.default.getByte("rightTrigger")).isEqualTo(0)
        rig.handler.setCaptured(false)
        rig.advanceBy(1)
        rig.default.motion(mapOf())
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, 0))
    }

    @Test
    fun `the on-screen controller state stays while a pad sends`() {
        val pad = rig.addPad(7, controllerNumber = 0)
        rig.handler.reportOscState(A_FLAG, 0, 0, 0, 0, 7, 0)

        pad.press(KEYCODE_BUTTON_B)
        pad.press(KEYCODE_BUTTON_X)

        assertThat(rig.packets.map { it.buttons })
            .containsExactly(A_FLAG, A_FLAG or B_FLAG, A_FLAG or B_FLAG or X_FLAG)
            .inOrder()
        assertThat(rig.packets.last().leftTrigger).isEqualTo(7)
    }

    @Test
    fun `the default context has its controller number from the start, so a report never asks for one`() {
        // The default context has its number from the start, which is why a report never reaches the
        // native arrival code that a new pad does.
        assertThat(rig.default.getBoolean("assignedControllerNumber")).isTrue()
        assertThat(rig.default.getShort("controllerNumber")).isEqualTo(0)

        rig.handler.reportOscState(0, 0, 0, 0, 0, 0, 0)

        assertWithMessage("packets").that(rig.packets).containsExactly(Sent(0, 1, 0))
    }
}
