package io.github.magalz.borealis.input

import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.view.InputDevice
import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.KeyboardTranslator
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers

/**
 * Before Android 13 a keyboard cannot be asked where its keys are (`getKeyCodeForKeyLocation` is
 * new in API 33), so the translator never builds a layout: it must not call it, and every key is
 * sent as the key code it came with.
 */
@RunWith(RobolectricTestRunner::class)
// Android 12, an API level that other tests already run on, so no further android-all jar is loaded.
@Config(sdk = [Build.VERSION_CODES.S])
class KeyboardTranslatorBeforeTiramisuCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    /**
     * An alphabetic keyboard that Android has. The fixture of the design system builds devices with
     * `InputDevice.Builder`, which only Android 13 has, so the device is made and filled in
     * the way Robolectric's own tests do.
     */
    @Suppress("DEPRECATION") // Robolectric's only way to make a device before Android 13.
    private fun alphabeticKeyboard() {
        val device = ShadowInputDevice.makeInputDeviceNamed("keyboard")
        ReflectionHelpers.setField(device, "mId", KEYBOARD)
        ReflectionHelpers.setField(device, "mKeyboardType", InputDevice.KEYBOARD_TYPE_ALPHABETIC)
        shadowOf(context.getSystemService(InputManager::class.java)).addInputDevice(device)
    }

    @Test
    fun `an alphabetic keyboard that is there when the translator is made has no layout`() {
        alphabeticKeyboard()

        val translator = KeyboardTranslator()

        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, KEYBOARD)).isFalse()
        assertThat(translator.translate(KeyEvent.KEYCODE_A, KEYBOARD)).isEqualTo(0x8041.toShort())
    }

    @Test
    fun `an alphabetic keyboard that is added or changed gets no layout either`() {
        val translator = KeyboardTranslator()
        alphabeticKeyboard()

        translator.onInputDeviceAdded(KEYBOARD)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, KEYBOARD)).isFalse()

        translator.onInputDeviceChanged(KEYBOARD)
        assertThat(translator.hasNormalizedMapping(KeyEvent.KEYCODE_A, KEYBOARD)).isFalse()
        translator.onInputDeviceRemoved(KEYBOARD)
        assertThat(translator.translate(KeyEvent.KEYCODE_A, KEYBOARD)).isEqualTo(0x8041.toShort())
    }

    private companion object {
        const val KEYBOARD = 7
    }
}
