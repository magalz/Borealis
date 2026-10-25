package io.github.magalz.borealis.input

import android.content.Context
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.KeyboardTranslator
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.disconnect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The host takes the key codes of a QWERTY keyboard. A keyboard with another layout (AZERTY,
 * QWERTZ) sends the key code of the letter on the key, so the translator learns, per alphabetic
 * keyboard, which QWERTY key sits where each of its keys sits, and sends the QWERTY key. The layout
 * is read when the keyboard appears (when the translator is made, or when Android tells it that a
 * keyboard was added or changed) and dropped when the keyboard goes away. The layouts come from
 * [ShadowKeyLayoutInputDevice].
 */
@RunWith(RobolectricTestRunner::class)
@Config(shadows = [ShadowKeyLayoutInputDevice::class])
class KeyboardTranslatorLayoutCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun resetLayouts() = ShadowKeyLayoutInputDevice.reset()

    private fun keyboard(
        id: Int,
        layout: Map<Int, Int> = AZERTY,
        keyboardType: Int = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
    ) {
        ShadowKeyLayoutInputDevice.layouts[id] = layout
        connect(context, inputDevice(id, InputDevice.SOURCE_KEYBOARD, keyboardType = keyboardType))
    }

    @Test
    fun `a keyboard that is there when the translator is made has its layout from the start`() {
        keyboard(KEYBOARD)

        val translator = KeyboardTranslator()

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isTrue()
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_A)
    }

    @Test
    fun `a key of the layout is sent as the QWERTY key at its place`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        assertThat(translator.translate(KeyEvent.KEYCODE_A, KEYBOARD)).isEqualTo(VK_Q)
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_A)
        assertThat(translator.translate(KeyEvent.KEYCODE_W, KEYBOARD)).isEqualTo(VK_Z)
        assertThat(translator.translate(KeyEvent.KEYCODE_Z, KEYBOARD)).isEqualTo(VK_W)
    }

    @Test
    fun `a key that the layout does not place is sent as it is, and it is not normalized`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        assertThat(translator.translate(KeyEvent.KEYCODE_B, KEYBOARD)).isEqualTo(VK_B)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_B, KEYBOARD)).isFalse()
        assertThat(translator.translate(KeyEvent.KEYCODE_ENTER, KEYBOARD)).isEqualTo(VK_ENTER)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_ENTER, KEYBOARD)).isFalse()
        // A key that has no code at all stays without one.
        assertThat(translator.translate(KeyEvent.KEYCODE_BACK, KEYBOARD)).isEqualTo(0.toShort())
    }

    @Test
    fun `an event without a device, or from a device with no layout, is sent as it is`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        assertThat(translator.translate(KeyEvent.KEYCODE_Q, -1)).isEqualTo(VK_Q)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, -1)).isFalse()
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, OTHER_DEVICE)).isEqualTo(VK_Q)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, OTHER_DEVICE)).isFalse()
    }

    @Test
    fun `device id 0 is a device, only a negative id means none`() {
        keyboard(0)
        val translator = KeyboardTranslator()

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, 0)).isTrue()
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, 0)).isEqualTo(VK_A)
    }

    @Test
    fun `each keyboard has its own layout`() {
        keyboard(KEYBOARD)
        keyboard(OTHER_DEVICE, layout = QWERTZ)
        val translator = KeyboardTranslator()

        assertThat(translator.translate(KeyEvent.KEYCODE_Z, KEYBOARD)).isEqualTo(VK_W)
        assertThat(translator.translate(KeyEvent.KEYCODE_Z, OTHER_DEVICE)).isEqualTo(VK_Y)
        assertThat(translator.translate(KeyEvent.KEYCODE_Y, OTHER_DEVICE)).isEqualTo(VK_Z)
        assertThat(translator.translate(KeyEvent.KEYCODE_Y, KEYBOARD)).isEqualTo(VK_Y)
    }

    @Test
    fun `when two places send the same key the last of them is the one it is sent as`() {
        // The places are read upward from key code 0: C is 31 and V is 50.
        keyboard(
            KEYBOARD,
            layout =
                mapOf(
                    KeyEvent.KEYCODE_C to KeyEvent.KEYCODE_X,
                    KeyEvent.KEYCODE_V to KeyEvent.KEYCODE_X,
                ),
        )

        assertThat(KeyboardTranslator().translate(KeyEvent.KEYCODE_X, KEYBOARD)).isEqualTo(VK_V)
    }

    @Test
    fun `the highest key code of the platform can be placed, and one above it is never looked up`() {
        val highest = KeyEvent.getMaxKeyCode()
        keyboard(KEYBOARD, layout = mapOf(KeyEvent.KEYCODE_A to highest))
        val translator = KeyboardTranslator()

        assertThat(translator.hasNormalizedMapping(highest, KEYBOARD)).isTrue()
        assertThat(translator.translate(highest, KEYBOARD)).isEqualTo(VK_A)
        for (aboveHighest in listOf(highest + 1, Int.MAX_VALUE)) {
            assertThat(translator.hasNormalizedMapping(aboveHighest, KEYBOARD)).isFalse()
            assertThat(translator.translate(aboveHighest, KEYBOARD)).isEqualTo(0.toShort())
        }
    }

    @Test
    fun `a negative key code from a keyboard with a layout is an array index error`() {
        // A key event never has a negative key code. This pins what the translator does with one.
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            translator.translate(-1, KEYBOARD)
        }
        assertThrows(ArrayIndexOutOfBoundsException::class.java) {
            translator.hasNormalizedMapping(-1, KEYBOARD)
        }
    }

    @Test
    fun `a keyboard that is not alphabetic has no layout`() {
        keyboard(KEYBOARD, keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        keyboard(OTHER_DEVICE, keyboardType = InputDevice.KEYBOARD_TYPE_NONE)
        val translator = KeyboardTranslator()

        for (id in listOf(KEYBOARD, OTHER_DEVICE)) {
            assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, id)).isFalse()
            assertThat(translator.translate(KeyEvent.KEYCODE_Q, id)).isEqualTo(VK_Q)
            translator.onInputDeviceAdded(id)
            assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, id)).isFalse()
        }
    }

    @Test
    fun `a keyboard that comes later has its layout once the translator is told it was added`() {
        val translator = KeyboardTranslator()
        keyboard(KEYBOARD)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isFalse()
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_Q)

        translator.onInputDeviceAdded(KEYBOARD)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isTrue()
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_A)
    }

    @Test
    fun `a device that is not there when it is added gets no layout`() {
        val translator = KeyboardTranslator()

        translator.onInputDeviceAdded(OTHER_DEVICE)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, OTHER_DEVICE)).isFalse()
    }

    @Test
    fun `a keyboard that is removed loses its layout`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        translator.onInputDeviceRemoved(KEYBOARD)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isFalse()
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_Q)
    }

    @Test
    fun `removing a device that has no layout changes nothing`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        translator.onInputDeviceRemoved(OTHER_DEVICE)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isTrue()
    }

    @Test
    fun `the layout is read once, and a changed keyboard is read again`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        // The keyboard now sends QWERTZ, but nobody told the translator.
        ShadowKeyLayoutInputDevice.layouts[KEYBOARD] = QWERTZ
        assertThat(translator.translate(KeyEvent.KEYCODE_Y, KEYBOARD)).isEqualTo(VK_Y)
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_A)

        translator.onInputDeviceChanged(KEYBOARD)

        assertThat(translator.translate(KeyEvent.KEYCODE_Y, KEYBOARD)).isEqualTo(VK_Z)
        assertThat(translator.translate(KeyEvent.KEYCODE_Q, KEYBOARD)).isEqualTo(VK_Q)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isFalse()
    }

    @Test
    fun `a device that changes to a keyboard gets its layout`() {
        keyboard(KEYBOARD, keyboardType = InputDevice.KEYBOARD_TYPE_NONE)
        val translator = KeyboardTranslator()
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isFalse()

        disconnect(context, KEYBOARD)
        keyboard(KEYBOARD)
        translator.onInputDeviceChanged(KEYBOARD)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isTrue()
    }

    @Test
    fun `a keyboard that changes to not alphabetic loses its layout`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isTrue()

        disconnect(context, KEYBOARD)
        keyboard(KEYBOARD, keyboardType = InputDevice.KEYBOARD_TYPE_NON_ALPHABETIC)
        translator.onInputDeviceChanged(KEYBOARD)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isFalse()
    }

    @Test
    fun `a keyboard that changes and is gone loses its layout`() {
        keyboard(KEYBOARD)
        val translator = KeyboardTranslator()

        disconnect(context, KEYBOARD)
        translator.onInputDeviceChanged(KEYBOARD)

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_Q, KEYBOARD)).isFalse()
    }

    private companion object {
        const val KEYBOARD = 7
        const val OTHER_DEVICE = 8

        // The Windows virtual-key codes behind the prefix, as signed shorts.
        val VK_A = 0x8041.toShort()
        val VK_B = 0x8042.toShort()
        val VK_Q = 0x8051.toShort()
        val VK_V = 0x8056.toShort()
        val VK_W = 0x8057.toShort()
        val VK_Y = 0x8059.toShort()
        val VK_Z = 0x805A.toShort()
        val VK_ENTER = 0x800D.toShort()

        /** The location of QWERTY's key sends the key on the right: AZERTY swaps A and Q, W and Z. */
        val AZERTY =
            mapOf(
                KeyEvent.KEYCODE_A to KeyEvent.KEYCODE_Q,
                KeyEvent.KEYCODE_Q to KeyEvent.KEYCODE_A,
                KeyEvent.KEYCODE_W to KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_Z to KeyEvent.KEYCODE_W,
            )

        /** QWERTZ swaps Y and Z. */
        val QWERTZ =
            mapOf(
                KeyEvent.KEYCODE_Y to KeyEvent.KEYCODE_Z,
                KeyEvent.KEYCODE_Z to KeyEvent.KEYCODE_Y,
            )
    }
}
