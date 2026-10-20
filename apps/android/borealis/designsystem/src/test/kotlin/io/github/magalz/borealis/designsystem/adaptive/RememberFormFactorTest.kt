package io.github.magalz.borealis.designsystem.adaptive

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.disconnect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.designsystem.testing.inputDeviceListeners
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class RememberFormFactorTest {
    @get:Rule val composeRule = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `a touch phone is a phone`() {
        assertThat(detected()).isEqualTo(FormFactor.Phone)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `the leanback feature makes a TV`() {
        shadowOf(context.packageManager).setSystemFeature(PackageManager.FEATURE_LEANBACK, true)

        assertThat(detected()).isEqualTo(FormFactor.Tv)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `the television UI mode makes a TV`() {
        val uiModeManager = context.getSystemService(UiModeManager::class.java)
        shadowOf(uiModeManager).setCurrentModeType(Configuration.UI_MODE_TYPE_TELEVISION)

        assertThat(detected()).isEqualTo(FormFactor.Tv)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-notouch")
    fun `a wide device without a touch screen is a handheld`() {
        assertThat(detected()).isEqualTo(FormFactor.Handheld)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-finger-dpad")
    fun `a wide touch phone whose keyboard reports a D-pad is a phone`() {
        assertThat(detected()).isEqualTo(FormFactor.Phone)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-notouch")
    fun `the very first composition already sees a wide handheld`() {
        val seen = mutableListOf<FormFactor>()
        composeRule.setContent { seen += rememberFormFactor() }
        composeRule.waitForIdle()

        assertThat(seen.first()).isEqualTo(FormFactor.Handheld)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-finger-nonav")
    fun `a wide touch device without a gamepad is a phone`() {
        assertThat(detected()).isEqualTo(FormFactor.Phone)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-finger-nonav")
    fun `a gamepad connected at composition makes the very first composition a handheld`() {
        connect(context, inputDevice(id = 3, sources = KEYBOARD or GAMEPAD or JOYSTICK))
        val seen = mutableListOf<FormFactor>()
        composeRule.setContent { seen += rememberFormFactor() }
        composeRule.waitForIdle()

        assertThat(seen.first()).isEqualTo(FormFactor.Handheld)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-finger-nonav")
    fun `a gamepad connected later flips the window to a handheld and back`() {
        var formFactor: FormFactor? = null
        composeRule.setContent { formFactor = rememberFormFactor() }
        composeRule.waitForIdle()
        assertThat(formFactor).isEqualTo(FormFactor.Phone)

        connect(context, inputDevice(id = 3, sources = KEYBOARD or GAMEPAD or JOYSTICK))
        notifyListeners { onInputDeviceAdded(3) }
        composeRule.waitForIdle()
        assertThat(formFactor).isEqualTo(FormFactor.Handheld)

        disconnect(context, deviceId = 3)
        notifyListeners { onInputDeviceRemoved(3) }
        composeRule.waitForIdle()
        assertThat(formFactor).isEqualTo(FormFactor.Phone)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-finger-nonav")
    fun `a changed device is looked at again`() {
        var formFactor: FormFactor? = null
        composeRule.setContent { formFactor = rememberFormFactor() }
        composeRule.waitForIdle()
        connect(context, inputDevice(id = 3, sources = KEYBOARD))
        notifyListeners { onInputDeviceAdded(3) }
        composeRule.waitForIdle()
        assertThat(formFactor).isEqualTo(FormFactor.Phone)

        // The same device now reports a gamepad source, as a pad does after it finishes pairing.
        disconnect(context, deviceId = 3)
        connect(context, inputDevice(id = 3, sources = KEYBOARD or GAMEPAD))
        notifyListeners { onInputDeviceChanged(3) }
        composeRule.waitForIdle()

        assertThat(formFactor).isEqualTo(FormFactor.Handheld)
    }

    @Test
    @Config(qualifiers = "w700dp-h400dp-xhdpi-finger-nonav")
    fun `the device listener is unregistered when the composition leaves`() {
        var shown by mutableStateOf(true)
        composeRule.setContent { if (shown) rememberFormFactor() }
        composeRule.waitForIdle()
        assertThat(inputDeviceListeners()).hasSize(1)

        shown = false
        composeRule.waitForIdle()

        assertThat(inputDeviceListeners()).isEmpty()
    }

    private fun notifyListeners(call: InputManager.InputDeviceListener.() -> Unit) {
        composeRule.runOnUiThread { inputDeviceListeners().forEach { it.call() } }
    }

    private fun detected(): FormFactor {
        var formFactor: FormFactor? = null
        composeRule.setContent { formFactor = rememberFormFactor() }
        composeRule.waitForIdle()
        return checkNotNull(formFactor) { "rememberFormFactor did not compose" }
    }

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
    }
}
