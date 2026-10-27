package io.github.magalz.borealis.input.devices

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Some pads report buttons the Android key layout files get wrong, or none at all, and the handler
 * knows them by vendor and product id, by name, or by the axes and keys they report. This is what
 * it decides about each when it makes the context of a device. The flags are read from the context;
 * `ControllerHandler` uses them while it translates the events of the pad.
 */
class VendorQuirksTest : DevicesTestBase() {
    private val sony = 0x054c
    private val withRxRy =
        STICKS_AND_Z + listOf(MotionEvent.AXIS_RX, MotionEvent.AXIS_RY).map { Axis(it) }

    private fun quirksOf(device: Pad): Quirks = rig(devices = listOf(device)).touch(device).quirks()

    @Test
    fun `an ordinary pad has no quirks and no Select or Mode button without keys`() {
        assertThat(quirksOf(pad(1))).isEqualTo(Quirks())
    }

    @Test
    fun `the key layout says whether a pad has a Mode button`() {
        val quirks = quirksOf(pad(1) { keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_MODE) })

        assertThat(quirks).isEqualTo(Quirks(hasMode = true))
    }

    @Test
    fun `the key layout says whether a pad has a Select button`() {
        val select = quirksOf(pad(1) { keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_SELECT) })

        assertThat(select).isEqualTo(Quirks(hasSelect = true))
    }

    @Test
    fun `a Back key makes a Select button`() {
        val back = quirksOf(pad(1) { keys = intArrayOf(KeyEvent.KEYCODE_BACK) })

        assertThat(back).isEqualTo(Quirks(hasSelect = true))
    }

    @Test
    fun `the paddles and the share button come from the native list`() {
        NativePads.paddles(0x045e, 0x0b00)
        NativePads.shares(0x045e, 0x0b00)

        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = 0x045e
                    productId = 0x0b00
                }
            )

        assertThat(quirks).isEqualTo(Quirks(hasPaddles = true, hasShare = true))
    }

    @Test
    fun `the native list is asked for the vendor and product of the device`() {
        quirksOf(
            pad(1) {
                vendorId = 0x1234
                productId = 0x5678
            }
        )

        assertThat(NativePads.queries)
            .containsExactly(
                "guessControllerHasPaddles ${0x1234} ${0x5678}",
                "guessControllerHasShareButton ${0x1234} ${0x5678}",
            )
            .inOrder()
    }

    @Test
    fun `a DualShock 4 with the Linux mapping uses the standard face buttons`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = sony
                    axes = withRxRy
                }
            )

        assertThat(quirks).isEqualTo(Quirks(usesLinuxGamepadStandardFaceButtons = true))
    }

    @Test
    fun `a DualShock 4 with a C button has the old mapping and both Select and Mode`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = sony
                    axes = withRxRy
                    keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_C)
                }
            )

        assertThat(quirks)
            .isEqualTo(Quirks(isNonStandardDualShock4 = true, hasSelect = true, hasMode = true))
    }

    @Test
    fun `a Sony pad without the RX and RY axes is neither`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = sony
                    keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_C)
                }
            )

        assertThat(quirks).isEqualTo(Quirks())
    }

    @Test
    fun `a Sony pad with analog triggers on LTRIGGER and RTRIGGER is neither`() {
        val triggers = listOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER).map { Axis(it) }

        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = sony
                    axes = withRxRy + triggers
                }
            )

        assertThat(quirks).isEqualTo(Quirks())
    }

    @Test
    fun `a pad of another vendor with the RX and RY axes has no DualShock quirk`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = 0x045e
                    axes = withRxRy
                    keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_C)
                }
            )

        assertThat(quirks).isEqualTo(Quirks())
    }

    @Test
    fun `the ADT-1 uses Back as Start and Mode as Select`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = 0x18d1
                    productId = 0x2c40
                    keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_MODE)
                }
            )

        assertThat(quirks)
            .isEqualTo(
                Quirks(backIsStart = true, modeIsSelect = true, hasSelect = true, hasMode = false)
            )
    }

    @Test
    fun `another Google pad is not an ADT-1`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = 0x18d1
                    productId = 0x2c41
                }
            )

        assertThat(quirks).isEqualTo(Quirks())
    }

    @Test
    fun `the ASUS Gamepad without a Start key uses Back as Start`() {
        val quirks =
            quirksOf(
                pad(1) {
                    name = "ASUS Gamepad"
                    keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_MODE)
                }
            )

        assertThat(quirks)
            .isEqualTo(
                Quirks(backIsStart = true, modeIsSelect = true, hasSelect = true, hasMode = false)
            )
    }

    @Test
    fun `the ASUS Gamepad with a Start key keeps its buttons`() {
        val start =
            quirksOf(
                pad(1) {
                    name = "ASUS Gamepad"
                    keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_START)
                }
            )
        val menu =
            quirksOf(
                pad(2) {
                    name = "ASUS Gamepad"
                    keys = intArrayOf(KeyEvent.KEYCODE_MENU)
                }
            )

        assertThat(start).isEqualTo(Quirks())
        assertThat(menu).isEqualTo(Quirks())
    }

    @Test
    fun `the Shield controllers with a search button use it as Mode`() {
        for ((id, version) in listOf(1 to "v01.03", 2 to "v01.04")) {
            val quirks = quirksOf(pad(id) { name = "NVIDIA Controller $version" })

            assertThat(quirks).isEqualTo(Quirks(searchIsMode = true, hasMode = true))
        }
    }

    @Test
    fun `the other Shield controllers have no quirk`() {
        val older = quirksOf(pad(1) { name = "NVIDIA Controller v01.01" })
        val named = quirksOf(pad(2) { name = "SHIELD Controller" })

        assertThat(older).isEqualTo(Quirks())
        assertThat(named).isEqualTo(Quirks())
    }

    @Test
    fun `the Razer Serval has Select and Mode behind unknown keys`() {
        val quirks = quirksOf(pad(1) { name = "Razer Serval Gamepad" })

        assertThat(quirks).isEqualTo(Quirks(isServal = true, hasSelect = true, hasMode = true))
    }

    @Test
    fun `an Xbox One S over Bluetooth with the old firmware has non-standard buttons`() {
        // The new firmware adds the GAS and BRAKE axes
        val quirks = quirksOf(pad(1) { name = "Xbox Wireless Controller" })

        assertThat(quirks)
            .isEqualTo(
                Quirks(isNonStandardXboxBtController = true, hasSelect = true, hasMode = true)
            )
    }

    @Test
    fun `an Xbox One S with the new firmware only needs its Select button ignored`() {
        val axes =
            STICKS_AND_Z + listOf(MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_GAS).map { Axis(it) }

        val quirks =
            quirksOf(
                pad(1) {
                    name = "Xbox Wireless Controller"
                    this.axes = axes
                }
            )

        assertThat(quirks).isEqualTo(Quirks())
    }

    @Test
    fun `the Xbox name is matched whole and with its case`() {
        assertThat(quirksOf(pad(1) { name = "Xbox Wireless Controller " })).isEqualTo(Quirks())
        assertThat(quirksOf(pad(2) { name = "xbox wireless controller" })).isEqualTo(Quirks())
        assertThat(quirksOf(pad(3) { name = "Xbox Wireless Controller v2" })).isEqualTo(Quirks())
    }

    @Test
    fun `the first name that matches wins`() {
        val asusAndServal = quirksOf(pad(1) { name = "ASUS Gamepad Razer Serval" })
        val shieldAndServal =
            quirksOf(pad(2) { name = "SHIELD NVIDIA Controller v01.04 Razer Serval" })

        // The ASUS branch comes first, then the Shield, then the Serval, then the Xbox
        assertThat(asusAndServal)
            .isEqualTo(Quirks(backIsStart = true, modeIsSelect = true, hasSelect = true))
        assertThat(shieldAndServal).isEqualTo(Quirks(searchIsMode = true, hasMode = true))
    }

    @Test
    fun `the Thrustmaster Score A has no Mode button on this channel`() {
        val keys = intArrayOf(KeyEvent.KEYCODE_BUTTON_MODE)

        val score =
            quirksOf(
                pad(1) {
                    vendorId = 0x044f
                    productId = 0xb328
                    this.keys = keys
                }
            )
        val other =
            quirksOf(
                pad(2) {
                    vendorId = 0x044f
                    productId = 0xb329
                    this.keys = keys
                }
            )

        assertThat(score).isEqualTo(Quirks(hasMode = false))
        assertThat(other).isEqualTo(Quirks(hasMode = true))
    }

    @Test
    fun `the touchpad of a DualShock 4 on the Shield is a keyboard and mouse that ends in Touchpad`() {
        val touchpad =
            pad(1) {
                name = "Wireless Controller Touchpad"
                vendorId = sony
                sources = KEYBOARD or MOUSE
                axes = emptyList()
            }

        assertThat(quirksOf(touchpad)).isEqualTo(Quirks(isDualShockStandaloneTouchpad = true))
    }

    @Test
    fun `that touchpad needs the Sony vendor the name and exactly those sources`() {
        fun touchpad(id: Int, name: String, vendor: Int, sources: Int) =
            quirksOf(
                    pad(id) {
                        this.name = name
                        vendorId = vendor
                        this.sources = sources
                        axes = emptyList()
                    }
                )
                .isDualShockStandaloneTouchpad

        val name = "Wireless Controller Touchpad"
        assertThat(touchpad(1, name, 0x045e, KEYBOARD or MOUSE)).isFalse()
        assertThat(touchpad(2, "Touchpad", sony, KEYBOARD or MOUSE)).isFalse()
        assertThat(touchpad(3, "$name 2", sony, KEYBOARD or MOUSE)).isFalse()
        assertThat(touchpad(4, name, sony, KEYBOARD or MOUSE or InputDevice.SOURCE_TOUCHPAD))
            .isFalse()
        assertThat(touchpad(5, name, sony, MOUSE)).isFalse()
    }

    @Test
    fun `the context keeps what identifies the device`() {
        val device =
            pad(7) {
                name = "Some Pad"
                vendorId = 0x1234
                productId = 0x5678
            }
        val context = rig(devices = listOf(device)).touch(device)

        assertThat(context.string("name")).isEqualTo("Some Pad")
        assertThat(context.int("id")).isEqualTo(7)
        assertThat(context.int("vendorId")).isEqualTo(0x1234)
        assertThat(context.int("productId")).isEqualTo(0x5678)
        assertThat(context["inputDevice"]).isSameInstanceAs(device.device)
    }

    private companion object {
        const val KEYBOARD = InputDevice.SOURCE_KEYBOARD
        const val MOUSE = InputDevice.SOURCE_MOUSE
    }
}
