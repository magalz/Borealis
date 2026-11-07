package io.github.magalz.borealis.input.mouserelease

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.ControllerHandlerRig
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A system pad in mouse mode holds the PC's left button with A and its right button with B. When
 * Android takes the pad away with one of them down, the PC gets the button up, once.
 */
@RunWith(RobolectricTestRunner::class)
class AndroidPadLeavesTest {
    @get:Rule val rig = ControllerHandlerRig()

    @Before
    fun connectPadInMouseMode() {
        rig.useSystemPad()
        rig.handler.setMouseEmulation(true)
    }

    private fun padLeaves() = rig.handler.onInputDeviceRemoved(rig.padDeviceId)

    @Test
    fun `a pad that leaves holding A lets the left button go`() {
        rig.press(KEYCODE_BUTTON_A)

        padLeaves()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }

    @Test
    fun `a pad that leaves holding B lets the right button go`() {
        rig.press(KEYCODE_BUTTON_B)

        padLeaves()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 3", "up 3").inOrder()
    }

    @Test
    fun `a pad that leaves holding both lets each button go once`() {
        rig.press(KEYCODE_BUTTON_A)
        rig.press(KEYCODE_BUTTON_B)

        padLeaves()

        val events = rig.connection.buttonEvents()
        assertThat(events.filter { it == "up 1" }).hasSize(1)
        assertThat(events.filter { it == "up 3" }).hasSize(1)
    }

    @Test
    fun `a pad that leaves with no button held sends no mouse event`() {
        padLeaves()

        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a pad that let go of its button before it left sends nothing more`() {
        rig.press(KEYCODE_BUTTON_A)
        rig.release(KEYCODE_BUTTON_A)
        val before = rig.connection.mouse.toList()

        padLeaves()

        assertThat(rig.connection.mouse).isEqualTo(before)
    }

    @Test
    fun `a pad that holds A as a gamepad button leaves without a mouse event`() {
        rig.handler.setMouseEmulation(false)
        rig.press(KEYCODE_BUTTON_A)

        padLeaves()

        assertThat(rig.connection.mouse).isEmpty()
    }

    @Test
    fun `a button the capture already released is not released again`() {
        rig.press(KEYCODE_BUTTON_A)
        rig.handler.setCaptured(true)

        padLeaves()

        assertThat(rig.connection.buttonEvents()).containsExactly("down 1", "up 1").inOrder()
    }
}
