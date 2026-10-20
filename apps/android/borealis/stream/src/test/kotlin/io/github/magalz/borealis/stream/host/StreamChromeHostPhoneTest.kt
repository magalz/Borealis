package io.github.magalz.borealis.stream.host

import android.view.InputDevice
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.disconnect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Whether the keyboard that opens now docks under the stream: on a phone with no game controller,
 * Android's or the app's USB driver's.
 */
@RunWith(RobolectricTestRunner::class)
class StreamChromeHostPhoneTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    private fun docks(usbPadHeld: Boolean = false) =
        StreamChromeHost.dockedKeyboard(context, usbPadHeld)

    @Test
    fun `a touch screen of phone width gets the docked keyboard`() {
        assertThat(docks()).isTrue()
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-notouch")
    fun `a wide device without a touch screen does not`() {
        assertThat(docks()).isFalse()
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `a TV does not`() {
        assertThat(docks()).isFalse()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-nonav")
    fun `a wide touch phone gets the docked keyboard without a gamepad`() {
        assertThat(docks()).isTrue()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-dpad")
    fun `a wide touch phone whose keyboard reports a D-pad still gets the docked keyboard`() {
        assertThat(docks()).isTrue()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-nonav")
    fun `a wide touch phone with a gamepad does not`() {
        connectGamepad()

        assertThat(docks()).isFalse()
    }

    // A pad cannot reach a docked keyboard, so a pad covers it wherever the phone is held.
    @Test
    @Config(qualifiers = "w360dp-h780dp-finger-nonav")
    fun `a narrow touch phone with a gamepad does not either`() {
        connectGamepad()

        assertThat(docks()).isFalse()
    }

    // Android has no input device for a pad that the app's USB driver holds, so the form factor
    // never sees it.
    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-nonav")
    fun `a wide touch phone with a pad on the USB driver does not`() {
        assertThat(docks(usbPadHeld = true)).isFalse()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-finger-nonav")
    fun `a narrow touch phone with a pad on the USB driver does not`() {
        assertThat(docks(usbPadHeld = true)).isFalse()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-nonav")
    fun `a gamepad that is gone again leaves the docked keyboard`() {
        connectGamepad()
        assertThat(docks()).isFalse()

        disconnect(context, PAD_ID)

        assertThat(docks()).isTrue()
    }

    private fun connectGamepad() {
        val sources =
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        connect(context, inputDevice(id = PAD_ID, sources = sources))
    }

    private companion object {
        const val PAD_ID = 3
    }
}
