package io.github.magalz.borealis.input

import android.view.KeyEvent.KEYCODE_BUTTON_R1
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.RB_FLAG
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import io.github.magalz.borealis.testing.OwnedResources
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The gamepad mouse moves by the speed of the setting, scaled for the session and by the RB
 * precision hold, on a pad on the USB driver as on a system pad. A full stick moves 1280 pixels a
 * second at 100 percent.
 */
@RunWith(RobolectricTestRunner::class)
class GamepadMouseSpeedTest {
    @get:Rule val owned = OwnedResources()
    private val rig = rigWithSpeed100()
    private val pad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }

    private fun rigWithSpeed100() =
        owned.own(
            ControllerHandlerRig().apply {
                prefs.gamepadMouseSpeed = 100
                prefs.analogStickForScrolling = AnalogStickForScrolling.RIGHT
            }
        )

    private fun pointerMoved(from: ControllerHandlerRig = rig): Int =
        from.connection.mouse.filter { it.startsWith("move") }.sumOf { it.split(" ")[1].toInt() }

    private fun scrolled(): Int =
        rig.connection.mouse
            .filter { it.startsWith("hires scroll") }
            .sumOf { it.split(" ")[2].toInt() }

    private fun hScrolled(): Int =
        rig.connection.mouse
            .filter { it.startsWith("hires hscroll") }
            .sumOf { it.split(" ")[2].toInt() }

    private fun movesSent(): Int = rig.connection.mouse.count { it.startsWith("move") }

    @Test
    fun `a stick held at full moves the pointer 1280 pixels a second`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(pointerMoved()).isWithin(20).of(1_280)
    }

    @Test
    fun `the pointer moves in small steps, not in jumps of 50 ms`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(movesSent()).isAtLeast(55)
    }

    @Test
    fun `the speed of the setting scales the move`() {
        rig.prefs.gamepadMouseSpeed = 50
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(pointerMoved()).isWithin(20).of(640)
    }

    @Test
    fun `the session factor scales the move`() {
        rig.handler.setPointerSpeed(2f)
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(pointerMoved()).isWithin(30).of(2_560)
    }

    @Test
    fun `a USB stick held up moves the pointer up the screen`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickY = -1f)

        rig.advanceBy(200)

        val moves = rig.connection.mouse.filter { it.startsWith("move") }.map { it.split(" ") }
        assertThat(moves).isNotEmpty()
        assertThat(moves.all { it[1].toInt() == 0 && it[2].toInt() < 0 }).isTrue()
    }

    @Test
    fun `a USB stick held down moves the pointer down the screen`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickY = 1f)

        rig.advanceBy(200)

        val moves = rig.connection.mouse.filter { it.startsWith("move") }.map { it.split(" ") }
        assertThat(moves).isNotEmpty()
        assertThat(moves.all { it[1].toInt() == 0 && it[2].toInt() > 0 }).isTrue()
    }

    @Test
    fun `a system pad stick held up moves the pointer up the screen`() {
        val system = rigWithSpeed100()
        system.useSystemPad()
        system.handler.setMouseEmulation(true)
        system.holdSystemPadSticks(leftY = 32766)

        system.advanceBy(200)

        val moves = system.connection.mouse.filter { it.startsWith("move") }.map { it.split(" ") }
        assertThat(moves).isNotEmpty()
        assertThat(moves.all { it[1].toInt() == 0 && it[2].toInt() < 0 }).isTrue()
    }

    @Test
    fun `a stick held at a quarter still moves the pointer`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 0.25f)

        rig.advanceBy(1_000)

        assertThat(pointerMoved()).isWithin(3).of(20)
    }

    @Test
    fun `holding RB moves at a quarter and letting go restores the speed`() {
        rig.handler.setMouseEmulation(true)
        pad.report(RB_FLAG, leftStickX = 1f)

        rig.advanceBy(1_000)
        assertThat(pointerMoved()).isWithin(10).of(320)

        pad.report(0, leftStickX = 1f)
        val before = pointerMoved()
        rig.advanceBy(1_000)

        assertThat(pointerMoved() - before).isWithin(20).of(1_280)
    }

    @Test
    fun `RB held in mouse mode does not reach the PC as a gamepad button`() {
        rig.handler.setMouseEmulation(true)

        pad.report(RB_FLAG)

        assertThat(rig.connection.packets.last().buttons).isEqualTo(0)
    }

    @Test
    fun `RB held while the mouse is off does nothing to the pad`() {
        pad.report(RB_FLAG, leftStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(movesSent()).isEqualTo(0)
        assertThat(rig.connection.packets.last().buttons).isEqualTo(RB_FLAG)
    }

    @Test
    fun `turning the mouse off stops the moves`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f)
        rig.advanceBy(200)

        rig.handler.setMouseEmulation(false)
        val sent = movesSent()
        rig.advanceBy(1_000)

        assertThat(sent).isGreaterThan(0)
        assertThat(movesSent()).isEqualTo(sent)
    }

    @Test
    fun `turning the mouse off drops the part of a pixel that was left over`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 0.3f)
        rig.advanceBy(16)
        assertThat(movesSent()).isEqualTo(0)

        rig.handler.setMouseEmulation(false)
        rig.handler.setMouseEmulation(true)
        rig.advanceBy(16)

        assertThat(movesSent()).isEqualTo(0)
    }

    @Test
    fun `the right stick scrolls at the same speed, in high resolution units`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, rightStickY = -1f)

        rig.advanceBy(1_000)

        assertThat(scrolled()).isWithin(20).of(1_280)
        assertThat(pointerMoved()).isEqualTo(0)
    }

    @Test
    fun `the right stick scrolls sideways`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, rightStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(hScrolled()).isWithin(20).of(1_280)
    }

    @Test
    fun `scrolling follows the speed of the setting and the session factor`() {
        rig.prefs.gamepadMouseSpeed = 50
        rig.handler.setPointerSpeed(0.5f)
        rig.handler.setMouseEmulation(true)
        pad.report(0, rightStickY = -1f)

        rig.advanceBy(1_000)

        assertThat(scrolled()).isWithin(10).of(320)
    }

    @Test
    fun `with no stick assigned to scrolling both sticks move the pointer`() {
        rig.prefs.analogStickForScrolling = AnalogStickForScrolling.NONE
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f, rightStickX = 1f)

        rig.advanceBy(1_000)

        assertThat(pointerMoved()).isWithin(40).of(2_560)
        assertThat(scrolled()).isEqualTo(0)
    }

    @Test
    fun `nothing moves while the chrome has the input`() {
        rig.handler.setMouseEmulation(true)
        pad.report(0, leftStickX = 1f)
        rig.advanceBy(100)

        rig.handler.setCaptured(true)
        val sent = movesSent()
        rig.advanceBy(1_000)

        assertThat(sent).isGreaterThan(0)
        assertThat(movesSent()).isEqualTo(sent)
    }

    @Test
    fun `a system pad moves the pointer too, and RB slows it`() {
        val system = rigWithSpeed100()
        system.useSystemPad()
        system.handler.setMouseEmulation(true)
        system.holdSystemPadSticks(leftX = 32766)

        system.advanceBy(1_000)
        val free = pointerMoved(system)
        assertThat(free).isWithin(20).of(1_280)

        system.press(KEYCODE_BUTTON_R1)
        system.advanceBy(1_000)

        assertThat(pointerMoved(system) - free).isWithin(20).of(320)
    }
}
