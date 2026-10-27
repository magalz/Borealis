package io.github.magalz.borealis.input.devices

import android.view.InputDevice
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.testing.ANDROID_10
import io.github.magalz.borealis.testing.ANDROID_9
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild

/**
 * Whether a device is external decides how it gets a controller number and whether its Back button
 * is let through to the system: the handler asks the framework, except for a few devices it knows
 * to report themselves wrong. The Back button of a pad is a Select button, so the handler keeps it
 * unless the device looks like a phone's or a remote's own key.
 */
class ExternalAndBackTest : DevicesTestBase() {
    private fun external(device: Pad): Boolean =
        rig(devices = listOf(device)).touch(device).bool("external")

    private fun ignoresBack(device: Pad, others: List<Pad> = emptyList()): Boolean =
        rig(devices = listOf(device) + others).touch(device).bool("ignoreBack")

    @Test
    fun `an external device is external`() {
        assertThat(external(pad(1) { external = true })).isTrue()
    }

    @Test
    fun `an internal device is internal`() {
        assertThat(external(pad(1) { external = false })).isFalse()
    }

    @Test
    fun `a device the framework calls external is internal by the name of the Shield portable keys`() {
        assertThat(
                external(
                    pad(1) {
                        name = "gpio-keys"
                        external = true
                    }
                )
            )
            .isFalse()
    }

    @Test
    fun `a device with joy_key in its name is internal`() {
        assertThat(
                external(
                    pad(1) {
                        name = "pad joy_key 2"
                        external = true
                    }
                )
            )
            .isFalse()
    }

    @Test
    fun `a device with keypad in its name is internal`() {
        assertThat(
                external(
                    pad(1) {
                        name = "xperia keypad"
                        external = true
                    }
                )
            )
            .isFalse()
    }

    @Test
    fun `those names are matched with their case`() {
        assertThat(
                external(
                    pad(1) {
                        name = "GPIO-KEYS"
                        external = true
                    }
                )
            )
            .isTrue()
        assertThat(
                external(
                    pad(2) {
                        name = "Joy_Key"
                        external = true
                    }
                )
            )
            .isTrue()
        assertThat(
                external(
                    pad(3) {
                        name = "Keypad"
                        external = true
                    }
                )
            )
            .isTrue()
    }

    @Test
    fun `the Shield portable controller is internal whatever its case`() {
        assertThat(external(pad(1) { name = "NVIDIA Corporation NVIDIA Controller v01.01" }))
            .isFalse()
        assertThat(external(pad(2) { name = "nvidia corporation nvidia controller V01.02" }))
            .isFalse()
    }

    @Test
    fun `the Logitech G Cloud controller is internal whatever its case`() {
        assertThat(external(pad(1) { name = "GR0006" })).isFalse()
        assertThat(external(pad(2) { name = "gr0006" })).isFalse()
    }

    @Test
    fun `other Nvidia controllers are what the framework says`() {
        assertThat(external(pad(1) { name = "NVIDIA Controller v01.03" })).isTrue()
        assertThat(external(pad(2) { name = "NVIDIA Corporation NVIDIA Controller v01.0" }))
            .isTrue()
    }

    @Test
    fun `on the Tinker Board every device is external`() {
        ShadowBuild.setModel("Tinker Board")

        assertThat(external(pad(1) { external = false })).isTrue()
        assertThat(
                external(
                    pad(2) {
                        name = "gpio-keys"
                        external = false
                    }
                )
            )
            .isTrue()
    }

    @Test
    fun `another model asks the framework`() {
        ShadowBuild.setModel("Tinker board")

        assertThat(external(pad(1) { external = false })).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_9])
    fun `on Android 9 the framework is asked through reflection`() {
        assertThat(external(pad(1) { external = true })).isTrue()
        assertThat(external(pad(2) { external = false })).isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_9])
    fun `on Android 9 a device the framework cannot answer for counts as external`() {
        // The reflected call throws, the Java prints the stack trace and answers true
        DeviceHardware.externalFails = true

        assertThat(external(pad(1) { external = false })).isTrue()
        assertThat(external(pad(2) { external = true })).isTrue()
    }

    @Test
    @Config(sdk = [ANDROID_9])
    fun `on Android 9 the known names still win`() {
        assertThat(
                external(
                    pad(1) {
                        name = "gpio-keys"
                        external = true
                    }
                )
            )
            .isFalse()
    }

    @Test
    @Config(sdk = [ANDROID_10])
    fun `on Android 10 the framework answers directly`() {
        assertThat(external(pad(1) { external = true })).isTrue()
        assertThat(external(pad(2) { external = false })).isFalse()
    }

    @Test
    fun `the Razer Serval keeps its Back button out of the system`() {
        assertThat(ignoresBack(pad(1) { name = "Razer Serval Gamepad" })).isTrue()
    }

    @Test
    fun `a remote without joystick axes ignores Back by its name`() {
        // The gamepad source would make the external rule keep Back
        val remote =
            pad(1) {
                name = "Smart Remote"
                sources = GAMEPAD
                axes = emptyList()
            }
        val pad =
            pad(2) {
                name = "Smart Pad"
                sources = GAMEPAD
                axes = emptyList()
            }

        assertThat(ignoresBack(remote)).isTrue()
        assertThat(ignoresBack(pad)).isFalse()
    }

    @Test
    fun `the remote name is matched in lower case`() {
        val remote =
            pad(1) {
                name = "BT REMOTE"
                sources = GAMEPAD
                axes = emptyList()
            }

        assertThat(ignoresBack(remote)).isTrue()
    }

    @Test
    fun `a pad with joystick axes is not a remote whatever its name`() {
        assertThat(ignoresBack(pad(1) { name = "Remote Pad" })).isFalse()
    }

    @Test
    fun `an external device with no gamepad buttons or axes lets Back through`() {
        val keyboard =
            pad(1) {
                sources = KEYBOARD
                axes = emptyList()
            }
        val mouse =
            pad(2) {
                sources = InputDevice.SOURCE_MOUSE
                axes = emptyList()
            }

        assertThat(ignoresBack(keyboard)).isTrue()
        assertThat(ignoresBack(mouse)).isTrue()
    }

    @Test
    fun `an external device with gamepad buttons or joystick axes keeps Back`() {
        val buttons =
            pad(1) {
                sources = GAMEPAD
                axes = emptyList()
            }
        val axes =
            pad(2) {
                sources = JOYSTICK
                axes = LEFT_STICK
            }

        assertThat(ignoresBack(buttons)).isFalse()
        assertThat(ignoresBack(axes)).isFalse()
    }

    @Test
    fun `an internal device on a device with no internal gamepad lets Back through`() {
        assertThat(
                ignoresBack(
                    pad(1) {
                        sources = KEYBOARD
                        axes = emptyList()
                        external = false
                    }
                )
            )
            .isTrue()
    }

    @Test
    fun `an internal gamepad without a Select button keeps Back`() {
        assertThat(
                ignoresBack(
                    pad(1) {
                        sources = GAMEPAD
                        external = false
                    }
                )
            )
            .isFalse()
    }

    @Test
    fun `an internal gamepad with a Select button lets Back through`() {
        val gamepad =
            pad(1) {
                sources = GAMEPAD
                external = false
                keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_SELECT)
            }

        assertThat(ignoresBack(gamepad)).isTrue()
    }

    @Test
    fun `a Select button on another internal device is enough`() {
        val gamepad =
            pad(1) {
                sources = GAMEPAD
                external = false
            }
        val keys =
            pad(2) {
                sources = KEYBOARD
                external = false
                keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_SELECT)
            }

        assertThat(ignoresBack(gamepad, listOf(keys))).isTrue()
    }

    @Test
    fun `an external gamepad is not an internal one`() {
        val phoneKeys =
            pad(1) {
                sources = KEYBOARD
                axes = emptyList()
                external = false
            }
        val external = pad(2) { sources = GAMEPAD }

        assertThat(ignoresBack(phoneKeys, listOf(external))).isTrue()
    }

    @Test
    fun `a Select button on an external device does not count`() {
        val gamepad =
            pad(1) {
                sources = GAMEPAD
                external = false
            }
        val external =
            pad(2) {
                sources = GAMEPAD
                keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_SELECT)
            }

        assertThat(ignoresBack(gamepad, listOf(external))).isFalse()
    }

    @Test
    fun `a device that is internal by its name counts as an internal gamepad`() {
        val phoneKeys =
            pad(1) {
                sources = KEYBOARD
                axes = emptyList()
                external = false
            }
        val portable =
            pad(2) {
                name = "gpio-keys"
                sources = GAMEPAD
                external = true
            }

        assertThat(ignoresBack(phoneKeys, listOf(portable))).isFalse()
    }

    @Test
    fun `on the Tinker Board an internal pad is judged as an external one`() {
        ShadowBuild.setModel("Tinker Board")

        assertThat(
                ignoresBack(
                    pad(1) {
                        sources = GAMEPAD
                        external = false
                    }
                )
            )
            .isFalse()
        assertThat(
                ignoresBack(
                    pad(2) {
                        sources = KEYBOARD
                        axes = emptyList()
                        external = false
                    }
                )
            )
            .isTrue()
    }

    @Test
    fun `the default context ignores Back`() {
        assertThat(rig().defaultContext().bool("ignoreBack")).isTrue()
    }

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val GAMEPAD = InputDevice.SOURCE_GAMEPAD
        const val JOYSTICK = InputDevice.SOURCE_JOYSTICK
    }
}
