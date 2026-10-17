package io.github.magalz.borealis.stream.host

import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowInputDevice
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
class StreamChromeHostPhoneTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `a touch screen of phone width gets the docked keyboard`() {
        assertThat(StreamChromeHost.isPhone(context)).isTrue()
    }

    @Test
    @Config(qualifiers = "w900dp-h500dp-notouch-dpad")
    fun `a handheld with a D-pad and no touch screen does not`() {
        assertThat(StreamChromeHost.isPhone(context)).isFalse()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-nonav")
    fun `a wide touch phone gets the docked keyboard without a gamepad`() {
        assertThat(StreamChromeHost.isPhone(context)).isTrue()
    }

    @Test
    @Config(qualifiers = "w780dp-h360dp-finger-nonav")
    fun `a wide touch phone with a gamepad does not`() {
        connectGamepad()

        assertThat(StreamChromeHost.isPhone(context)).isFalse()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-finger-nonav")
    fun `a narrow touch phone with a gamepad still gets the docked keyboard`() {
        connectGamepad()

        assertThat(StreamChromeHost.isPhone(context)).isTrue()
    }

    private fun connectGamepad() {
        val device = ShadowInputDevice.makeInputDeviceNamed("test-pad")
        ReflectionHelpers.setField(device, "mId", 3)
        ReflectionHelpers.setField(
            device,
            "mSources",
            InputDevice.SOURCE_KEYBOARD or
                InputDevice.SOURCE_GAMEPAD or
                InputDevice.SOURCE_JOYSTICK,
        )
        shadowOf(context.getSystemService(InputManager::class.java)).addInputDevice(device)
    }
}
