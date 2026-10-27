package io.github.magalz.borealis.input

import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import io.github.magalz.borealis.stream.GamepadMouseSync
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.streamingSession
import io.github.magalz.borealis.testing.unconfinedScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The "Gamepad mouse" row of the menu shows what the pads really do, after the Start hold has
 * switched the mode on or off on a pad of either kind.
 */
@RunWith(RobolectricTestRunner::class)
class MouseRowFollowsStartHoldTest {
    private val rig = ControllerHandlerRig(multiController = true)
    private val usbPad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val secondUsbPad =
        FakeUsbController(2, rig.handler).also { rig.handler.deviceAdded(it) }
    private val session = streamingSession(gamepadMouse = false)
    private val scope = unconfinedScope()

    init {
        GamepadMouseSync.bind(scope, session, rig.handler::isMouseEmulationOnAnyPad)
    }

    @After fun tearDown() = scope.cancel()

    private fun row() = (session.state.value.phase as StreamPhase.Streaming).gamepadMouse

    private fun menuShows(): Boolean? {
        session.openMenu()
        val shown = row()
        session.closePanel()
        return shown
    }

    private fun holdStartOnUsbPad() {
        usbPad.report(PLAY_FLAG)
        rig.advanceBy(800)
        usbPad.report(0)
    }

    private fun holdStartOnSystemPad() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(800)
        rig.release(KEYCODE_BUTTON_START)
    }

    @Test
    fun `the row shows On after a USB pad switched the mouse on, and Off after it switched it off`() {
        holdStartOnUsbPad()
        assertThat(menuShows()).isTrue()

        holdStartOnUsbPad()
        assertThat(menuShows()).isFalse()
    }

    private fun holdStartOnSecondUsbPad() {
        secondUsbPad.report(PLAY_FLAG)
        rig.advanceBy(800)
        secondUsbPad.report(0)
    }

    @Test
    fun `the row shows On while one pad emulates the mouse, though another pad switched it off`() {
        holdStartOnUsbPad()
        holdStartOnSecondUsbPad()
        holdStartOnSecondUsbPad()

        assertThat(menuShows()).isTrue()
    }

    @Test
    fun `the row shows Off when no pad emulates the mouse`() {
        holdStartOnUsbPad()
        holdStartOnSecondUsbPad()
        holdStartOnUsbPad()
        holdStartOnSecondUsbPad()

        assertThat(menuShows()).isFalse()
    }

    @Test
    fun `the row shows On after a system pad switched the mouse on, and Off after it switched it off`() {
        rig.useSystemPad()
        holdStartOnSystemPad()
        assertThat(menuShows()).isTrue()

        holdStartOnSystemPad()
        assertThat(menuShows()).isFalse()
    }
}
