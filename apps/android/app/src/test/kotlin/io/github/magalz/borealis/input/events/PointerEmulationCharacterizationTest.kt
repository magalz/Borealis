package io.github.magalz.borealis.input.events

import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    // The same at the scroll rate, 14.34 a tick
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
}
