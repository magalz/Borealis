package io.github.magalz.borealis.input.ch2

import android.view.InputDevice
import android.view.KeyEvent.KEYCODE_BUTTON_A
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.devices.DevicesTestBase
import io.github.magalz.borealis.input.devices.pad
import org.junit.Test

/**
 * `assignControllerNumberIfNeeded` asks first whether the pad is a built-in one, and only then
 * whether it has joystick axes. A built-in device without axes is controller 0 on its own: it looks
 * for no joystick beside it, which an external one without axes does.
 */
class BuiltInPadNumberTest : DevicesTestBase() {
    @Test
    fun `a built-in device without joystick axes is controller 0 and looks for no joystick`() {
        val builtIn =
            pad(4) {
                name = "pad Touchpad"
                external = false
                sources = InputDevice.SOURCE_MOUSE
                axes = emptyList()
                descriptor = "same"
            }
        val joystick =
            pad(5) {
                name = "pad"
                descriptor = "same"
            }
        val rig = rig(multiController = true)
        listOf(builtIn, joystick).forEach(rig::connect)

        rig.press(builtIn, KEYCODE_BUTTON_A)

        assertThat(rig.context(builtIn).short("controllerNumber")).isEqualTo(0)
        assertThat(rig.context(builtIn).bool("assignedControllerNumber")).isTrue()
        assertThat(rig.contextOf(joystick)).isNull()
        assertThat(rig.connection.arrivals).hasSize(1)
    }
}
