package io.github.magalz.borealis.input.events

import android.os.Looper
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
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.X_FLAG
import com.limelight.nvstream.input.ControllerPacket.Y_FLAG
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock

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

    @Test
    fun `a USB pad on another controller number is left out of the packet of a system pad`() {
        val system = rig.addPad(7, controllerNumber = 3)
        val usb = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
        usb.report(B_FLAG, leftStickX = 1f)
        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, B_FLAG, leftStickX = 32766))

        system.press(KEYCODE_BUTTON_A)

        assertThat(rig.packets.last()).isEqualTo(Sent(3, 1, A_FLAG))
    }

    @Test
    fun `a USB pad in mouse mode is left out of the packet of a system pad that is not`() {
        rig.handler.setMouseEmulation(true)
        val usb = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
        val system = rig.addPad(7, controllerNumber = 0)
        usb.report(B_FLAG, leftStickX = 1f)
        assertThat(rig.connection.mouse).containsExactly("down 3")

        system.press(KEYCODE_BUTTON_A)

        assertThat(rig.packets.last()).isEqualTo(Sent(0, 1, A_FLAG))
    }

    @Test
    fun `every controller number that sent input gets a neutral packet when the capture starts`() {
        val first = rig.addPad(7, controllerNumber = 0)
        val second = rig.addPad(8, controllerNumber = 2)
        first.press(KEYCODE_BUTTON_A)
        second.press(KEYCODE_BUTTON_B)
        val before = rig.packets.size

        rig.handler.setCaptured(true)

        assertThat(rig.packets.drop(before)).containsExactly(Sent(0, 1, 0), Sent(2, 1, 0)).inOrder()
    }

    @Test
    fun `a chord that a USB driver thread completes is reported on the main thread`() {
        val usb = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
        usb.report(PLAY_FLAG or BACK_FLAG)
        // The clock moves, but the looper does not run the check that was scheduled for the chord.
        ShadowSystemClock.advanceBy(Duration.ofMillis(1_000))

        val driver = Thread { usb.report(PLAY_FLAG or BACK_FLAG or A_FLAG) }
        driver.start()
        driver.join()

        assertThat(rig.chords).isEqualTo(0)
        rig.advanceBy(0)
        assertThat(rig.chords).isEqualTo(1)
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
                mapOf(
                    "leftStickX" to 1000.toShort(),
                    "leftStickY" to (-3000).toShort(),
                    "rightStickX" to 1000.toShort(),
                    "rightStickY" to (-1000).toShort(),
                ),
                mapOf(
                    "leftStickX" to (-3000).toShort(),
                    "leftStickY" to 1000.toShort(),
                    "rightStickX" to (-1000).toShort(),
                    "rightStickY" to 1000.toShort(),
                ),
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
                    "leftTrigger" to 23.toByte(),
                    "rightTrigger" to 6.toByte(),
                    "leftStickX" to 430.toShort(),
                    "leftStickY" to (-1356).toShort(),
                    "rightStickX" to (-243).toShort(),
                    "rightStickY" to (-697).toShort(),
                ),
        )
        rig.addPad(
            8,
            controllerNumber = 0,
            fields =
                mapOf(
                    "inputMap" to B_FLAG,
                    "leftTrigger" to 33.toByte(),
                    "rightTrigger" to 32.toByte(),
                    "leftStickX" to 2356.toShort(),
                    "leftStickY" to 2687.toShort(),
                    "rightStickX" to 3298.toShort(),
                    "rightStickY" to 3712.toShort(),
                ),
        )
        rig.handler.deviceAdded(FakeUsbController(1, rig.handler))
        // The USB values are floats. As sticks 0.09 is 2948, -0.04 is 1310 (Y is upside down),
        // 0.03 is 982 and -0.1 is 3276; as triggers 0.22 is 56 and 0.2 is 51.
        rig.handler.reportControllerState(1, X_FLAG, 0.09f, -0.04f, 0.03f, -0.1f, 0.22f, 0.2f)

        rig.handler.reportOscState(Y_FLAG, 3016, 1841, -1986, -183, 68, 66)

        assertThat(rig.packets.last())
            .isEqualTo(
                Sent(
                    controller = 0,
                    activeMask = 1,
                    buttons = A_FLAG or B_FLAG or X_FLAG or Y_FLAG,
                    leftTrigger = 127,
                    rightTrigger = 119,
                    leftStickX = 3070,
                    leftStickY = -1,
                    rightStickX = -1,
                    rightStickY = -49,
                )
            )
    }

    // ----- The gamepad mask of the packet -----

    @Test
    fun `without multiple controllers the mask is always player 1`() {
        val pad = rig.addPad(7, controllerNumber = 3)
        rig.setHandlerField("currentControllers", 6.toShort())

        assertThat(pad.send().activeMask).isEqualTo(1)
    }

    @Test
    fun `with multiple controllers the mask is the controllers in use, the ones at the start and the on-screen one`() {
        val multi = EventsRig(multiController = true)
        val pad = multi.addPad(7, controllerNumber = 3)
        fun setMask(name: String, value: Int) = multi.setHandlerField(name, value.toShort())
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
    fun `the D-pad scrolls one click when it goes down, up and right being the positive directions`() {
        val pad = mouseModePad()

        val clicks =
            listOf(
                KEYCODE_DPAD_UP to "scroll 1",
                KEYCODE_DPAD_DOWN to "scroll -1",
                KEYCODE_DPAD_RIGHT to "hscroll 1",
                KEYCODE_DPAD_LEFT to "hscroll -1",
            )
        val expected = mutableListOf<String>()
        for ((keyCode, click) in clicks) {
            pad.press(keyCode)
            expected += click
            // The click goes out with the key down ...
            assertThat(rig.connection.mouse).containsExactlyElementsIn(expected).inOrder()
            rig.advanceBy(100)
            pad.release(keyCode)
            // ... and the key up sends none.
            assertThat(rig.connection.mouse).containsExactlyElementsIn(expected).inOrder()
        }
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
    fun `the first tick is scheduled 16 ms after the mouse came on and the next one 16 ms after that`() {
        rig.addPad(7, controllerNumber = 0)
        val looper = shadowOf(Looper.getMainLooper())

        val enabledAt = rig.now
        rig.handler.setMouseEmulation(true)
        val first = looper.nextScheduledTaskTime
        rig.advanceBy(16)
        val tickedAt = rig.now
        val second = looper.nextScheduledTaskTime

        // The scheduled time is on the clock of the looper, which is the clock of the events.
        assertThat(first).isEqualTo(Duration.ofMillis(enabledAt + 16))
        assertThat(second).isEqualTo(Duration.ofMillis(tickedAt + 16))
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
