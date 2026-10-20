package io.github.magalz.borealis.stream

import android.app.Activity
import android.view.InputDevice
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The keyboard of the session the stream activity gets: docked on a phone with no pad, covering as
 * soon as a pad is there when it opens. The session is made before any pad, as a stream starts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w780dp-h360dp-land-finger-nonav")
class StreamSessionFactoryKeyboardTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).create().get()

    /** Whether the USB driver holds a pad; the factory asks it when the keyboard opens. */
    private var usbPadHeld = false

    private val session: StreamSession =
        StreamSessionFactory.create(
                activity,
                PreferenceConfiguration(),
                "Desktop",
                "Den PC",
                RecordingStreamActions(),
            ) {
                usbPadHeld
            }
            .also {
                it.onConnectionStarted(SessionInfo(1920, 1080, 60, "HEVC", false, null, 20_000))
                it.onFirstFrame()
            }

    private fun keyboardMode(): ChromeMode {
        session.openKeyboard()
        return session.chromeMode
    }

    private fun connectGamepad() {
        val sources =
            InputDevice.SOURCE_KEYBOARD or InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK
        connect(activity, inputDevice(id = 3, sources = sources))
    }

    @Test
    fun `a phone with no pad opens the docked keyboard`() {
        assertThat(keyboardMode()).isEqualTo(ChromeMode.DOCKED)
    }

    @Test
    fun `a pad that Android knows, connected after the session was created, opens the covering keyboard`() {
        connectGamepad()

        assertThat(keyboardMode()).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a pad that the USB driver holds opens the covering keyboard`() {
        usbPadHeld = true

        assertThat(keyboardMode()).isEqualTo(ChromeMode.MODAL)
    }

    @Test
    fun `a pad that the USB driver let go of leaves the next keyboard docked`() {
        usbPadHeld = true
        session.openKeyboard()
        session.closePanel()

        usbPadHeld = false

        assertThat(keyboardMode()).isEqualTo(ChromeMode.DOCKED)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `a TV never opens the docked keyboard`() {
        assertThat(keyboardMode()).isEqualTo(ChromeMode.MODAL)
    }
}
