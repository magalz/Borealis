package io.github.magalz.borealis.input.events

import com.google.common.truth.Truth.assertThat
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/**
 * What the 16 ms ticks of a pad in mouse mode send for its sticks: the pointer moves, or scrolls in
 * high resolution, by the stick that the setting gives to scrolling. A system pad holds its sticks in
 * the handler's own range, where up and right are positive. A full stick at 100 percent speed is
 * 1280 pixels a second, so a tick is 20.48 pixels, which the motion rounds down and carries over.
 */
@RunWith(RobolectricTestRunner::class)
class PointerEmulationCharacterizationTest {
    private val full = 32766.toShort()

    private fun rigWith(scrolling: AnalogStickForScrolling?, speed: Int = 100) =
        EventsRig {
            gamepadMouseSpeed = speed
            analogStickForScrolling = scrolling
        }

    private fun EventsRig.mousePad(vararg sticks: Pair<String, Short>): Pad {
        val pad = addPad(7, fields = sticks.toMap())
        handler.setMouseEmulation(true)
        return pad
    }

    private fun EventsRig.mouseAfter(millis: Long): List<String> {
        advanceBy(millis)
        return connection.mouse
    }

    // The pixels of ten ticks: 20.48 each, carried over
    private val pixels = listOf(20, 20, 21, 20, 21, 20, 21, 20, 21, 20)

    // A stick pushed into a corner is held to the unit circle, so each axis of it is 20.48 / sqrt(2) = 14.48 a tick
    private val scrolls = listOf(14, 14, 15, 14, 15, 14, 15, 14, 15, 14)

    @Test
    fun `with the right stick for scrolling the left moves the pointer and the right scrolls`() {
        val rig = rigWith(AnalogStickForScrolling.RIGHT)
        rig.mousePad("leftStickX" to full, "rightStickY" to full, "rightStickX" to (-32766).toShort())

        assertThat(rig.mouseAfter(160))
            .containsExactlyElementsIn(
                pixels.indices.flatMap { tick ->
                    listOf("move ${pixels[tick]} 0", "hires scroll ${scrolls[tick]}", "hires hscroll -${scrolls[tick]}")
                }
            )
            .inOrder()
    }

    @Test
    fun `with the left stick for scrolling the right moves the pointer and the left scrolls`() {
        val rig = rigWith(AnalogStickForScrolling.LEFT)
        rig.mousePad("rightStickX" to full, "leftStickY" to full, "leftStickX" to (-32766).toShort())

        assertThat(rig.mouseAfter(32))
            .containsExactly(
                "move 20 0",
                "hires scroll 14",
                "hires hscroll -14",
                "move 20 0",
                "hires scroll 14",
                "hires hscroll -14",
            )
            .inOrder()
    }

    @Test
    fun `with no stick for scrolling both sticks move the pointer, the left one first`() {
        val rig = rigWith(null)
        rig.mousePad("leftStickX" to full, "rightStickY" to full)

        assertThat(rig.mouseAfter(32))
            .containsExactly("move 20 0", "move 0 -20", "move 20 0", "move 0 -20")
            .inOrder()
    }

    @Test
    fun `a stick pushed up moves the pointer up the screen and down moves it down`() {
        val rig = rigWith(null)
        rig.mousePad("leftStickY" to full, "rightStickY" to (-32766).toShort())

        assertThat(rig.mouseAfter(16)).containsExactly("move 0 -20", "move 0 20").inOrder()
    }

    @Test
    fun `the session pointer speed scales the move, and a speed of zero stops it`() {
        // The rigs share the main looper, so all of them exist before any pad starts its ticks.
        val half = rigWith(null)
        val zero = rigWith(null)
        val double = rigWith(null)
        half.handler.setPointerSpeed(0.5f)
        zero.handler.setPointerSpeed(0f)
        double.handler.setPointerSpeed(2f)
        half.mousePad("leftStickX" to full)
        zero.mousePad("leftStickX" to full)
        double.mousePad("leftStickX" to full)

        half.advanceBy(32)

        assertThat(half.connection.mouse).containsExactly("move 10 0", "move 10 0").inOrder()
        assertThat(zero.connection.mouse).isEmpty()
        assertThat(double.connection.mouse).containsExactly("move 40 0", "move 41 0").inOrder()
    }

    @Test
    fun `the speed of the setting scales the move`() {
        val rig = rigWith(null, speed = 50)
        rig.mousePad("leftStickX" to full)

        assertThat(rig.mouseAfter(32)).containsExactly("move 10 0", "move 10 0").inOrder()
    }

    @Test
    fun `the ticks send nothing while captured, though the stick is held`() {
        val rig = rigWith(null)
        val pad = rig.mousePad()
        rig.handler.setCaptured(true)
        pad.set("leftStickX", full)

        assertThat(rig.mouseAfter(160)).isEmpty()
        // The pad stays in mouse mode, and moves again when the capture ends.
        assertThat(pad.getBoolean("mouseEmulationActive")).isTrue()
        rig.handler.setCaptured(false)
        assertThat(rig.mouseAfter(16)).containsExactly("move 20 0")
    }

    @Test
    fun `the stick for scrolling sends nothing while captured either, and scrolls again when the capture ends`() {
        val right = rigWith(AnalogStickForScrolling.RIGHT)
        val rightPad = right.mousePad()
        right.handler.setCaptured(true)
        rightPad.set("leftStickX", full)
        rightPad.set("rightStickY", full)

        assertThat(right.mouseAfter(160)).isEmpty()
        right.handler.setCaptured(false)
        assertThat(right.mouseAfter(16)).containsExactly("move 20 0", "hires scroll 20").inOrder()
    }

    @Test
    fun `the left stick for scrolling is silent while captured too`() {
        val left = rigWith(AnalogStickForScrolling.LEFT)
        val leftPad = left.mousePad()
        left.handler.setCaptured(true)
        leftPad.set("rightStickX", full)
        leftPad.set("leftStickY", full)

        assertThat(left.mouseAfter(160)).isEmpty()
        left.handler.setCaptured(false)
        assertThat(left.mouseAfter(16)).containsExactly("move 20 0", "hires scroll 20").inOrder()
    }

    // ----- Switching it on and off -----

    @Test
    fun `turning the mouse on reaches every system pad but not the default context, and shows one notice`() {
        val rig = rigWith(null)
        val one = rig.addPad(7)
        val two = rig.addPad(8)

        rig.handler.setMouseEmulation(true)

        assertThat(one.getBoolean("mouseEmulationActive")).isTrue()
        assertThat(two.getBoolean("mouseEmulationActive")).isTrue()
        assertThat(rig.default.getBoolean("mouseEmulationActive")).isFalse()
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isTrue()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: ON")

        rig.handler.setMouseEmulation(false)

        assertThat(one.getBoolean("mouseEmulationActive")).isFalse()
        assertThat(two.getBoolean("mouseEmulationActive")).isFalse()
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(2)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: OFF")
    }

    @Test
    fun `turning the mouse off and on again before a tick leaves one chain of ticks`() {
        val rig = rigWith(null)
        rig.mousePad("leftStickX" to full)
        rig.advanceBy(5)

        rig.handler.setMouseEmulation(false)
        rig.handler.setMouseEmulation(true)

        // The old chain would tick 11 ms from now; the new one ticks at 16 ms, once.
        assertThat(rig.mouseAfter(11)).isEmpty()
        assertThat(rig.mouseAfter(5)).hasSize(1)
    }

    @Test
    fun `turning the mouse off and on drops what the right stick had carried over`() {
        val rig = rigWith(AnalogStickForScrolling.RIGHT)
        rig.mousePad("rightStickY" to full)
        rig.advanceBy(32)
        rig.connection.mouse.clear()

        rig.handler.setMouseEmulation(false)
        rig.handler.setMouseEmulation(true)

        // 20.48 a tick: the first tick sends 20, and a carried 0.96 would make it 21.
        assertThat(rig.mouseAfter(16)).containsExactly("hires scroll 20")
    }

    @Test
    fun `a pad that already has the mouse on is left alone and the notice goes to the first pad that changes`() {
        val rig = rigWith(null)
        rig.addPad(7, fields = mapOf("mouseEmulationActive" to true))
        val off = rig.addPad(8)

        rig.handler.setMouseEmulation(true)

        assertThat(off.getBoolean("mouseEmulationActive")).isTrue()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
    }

    @Test
    fun `the mouse that a Start hold turned on is what a pad that connects later gets`() {
        val rig = rigWith(null)
        val pad = rig.addPad(7)
        pad.press(KEYCODE_BUTTON_START)
        rig.advanceBy(800)
        pad.release(KEYCODE_BUTTON_START)
        assertThat(pad.getBoolean("mouseEmulationActive")).isTrue()

        val usb = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
        usb.report(A_FLAG)

        assertThat(rig.connection.mouse).contains("down 1")
    }

    @Test
    fun `without the mouse a pad that connects later is a gamepad`() {
        val rig = rigWith(null)
        rig.addPad(7)

        val usb = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
        usb.report(A_FLAG)

        assertThat(rig.connection.mouse).isEmpty()
        assertThat(rig.packets.last().buttons).isEqualTo(A_FLAG)
    }

    @Test
    fun `a button of a pad in mouse mode is a click and of a pad that is not is a button`() {
        val rig = rigWith(null)
        val mouse = rig.addPad(7, fields = mapOf("mouseEmulationActive" to true))
        val gamepad = rig.addPad(8, controllerNumber = 1)

        mouse.press(KEYCODE_BUTTON_A)
        gamepad.press(KEYCODE_BUTTON_A)

        assertThat(rig.connection.mouse).containsExactly("down 1")
        assertThat(rig.packets.map { it.controller to it.buttons })
            .containsExactly(0 to 0, 1 to A_FLAG)
            .inOrder()
    }
}
