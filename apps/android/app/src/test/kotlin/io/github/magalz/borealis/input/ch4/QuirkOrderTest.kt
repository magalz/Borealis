package io.github.magalz.borealis.input.ch4

import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.devices.Axis
import io.github.magalz.borealis.input.devices.DevicesTestBase
import io.github.magalz.borealis.input.devices.LEFT_STICK
import io.github.magalz.borealis.input.devices.Pad
import io.github.magalz.borealis.input.devices.Quirks
import io.github.magalz.borealis.input.devices.STICKS_AND_Z
import io.github.magalz.borealis.input.devices.pad
import io.github.magalz.borealis.input.devices.quirks
import org.junit.Test

/**
 * The rules `createInputDeviceContextForDevice` applies by vendor, product and name, in the order
 * it applies them: the ADT-1 first, then the name rules (the first name that matches), then the
 * Thrustmaster Score A. `VendorQuirksTest` has each rule on its own; these have what one rule
 * leaves for the next, and the ids and names that must match whole.
 */
class QuirkOrderTest : DevicesTestBase() {
    private val google = 0x18d1
    private val microsoft = 0x045e
    private val thrustmaster = 0x044f
    private val adt1 = 0x2c40
    private val scoreA = 0xb328
    private val modeKey = intArrayOf(KeyEvent.KEYCODE_BUTTON_MODE)

    private fun quirksOf(device: Pad): Quirks = rig(devices = listOf(device)).touch(device).quirks()

    @Test
    fun `the ADT-1 is known by its vendor and its product`() {
        val device =
            pad(1) {
                vendorId = microsoft
                productId = adt1
                keys = modeKey
            }

        val context = rig(devices = listOf(device)).touch(device)

        assertThat(context.quirks()).isEqualTo(Quirks(hasMode = true))
        assertThat(context.float("triggerDeadzone")).isEqualTo(0f)
    }

    @Test
    fun `the trigger deadzone of the ADT-1 is the one it sets, not the one its triggers report`() {
        val triggers =
            listOf(MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_RTRIGGER).map {
                Axis(it, flat = 0.05f)
            }
        val device =
            pad(1) {
                vendorId = google
                productId = adt1
                axes = LEFT_STICK + triggers
            }

        val context = rig(devices = listOf(device)).touch(device)

        assertThat(context.float("triggerDeadzone")).isEqualTo(0.3f)
    }

    @Test
    fun `a name rule comes after the ADT-1 and has the last word on Select and Mode`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = google
                    productId = adt1
                    name = "Razer Serval Gamepad"
                }
            )

        assertThat(quirks)
            .isEqualTo(
                Quirks(
                    isServal = true,
                    backIsStart = true,
                    modeIsSelect = true,
                    hasSelect = true,
                    hasMode = true,
                )
            )
    }

    @Test
    fun `the Thrustmaster Score A comes after the name rules and has the last word on Mode`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = thrustmaster
                    productId = scoreA
                    name = "Razer Serval Gamepad"
                }
            )

        assertThat(quirks).isEqualTo(Quirks(isServal = true, hasSelect = true, hasMode = false))
    }

    @Test
    fun `the Thrustmaster Score A is known by its vendor and its product`() {
        val quirks =
            quirksOf(
                pad(1) {
                    vendorId = microsoft
                    productId = scoreA
                    keys = modeKey
                }
            )

        assertThat(quirks).isEqualTo(Quirks(hasMode = true))
    }

    @Test
    fun `a name that has SHIELD or NVIDIA Controller in it is a Shield and not a Serval`() {
        val shield = quirksOf(pad(1) { name = "SHIELD Razer Serval" })
        val nvidia = quirksOf(pad(2) { name = "NVIDIA Controller Razer Serval" })

        assertThat(shield).isEqualTo(Quirks())
        assertThat(nvidia).isEqualTo(Quirks())
    }

    @Test
    fun `the new firmware of the Xbox One S is known by its GAS axis alone`() {
        val gasOnly =
            quirksOf(
                pad(1) {
                    name = "Xbox Wireless Controller"
                    axes = STICKS_AND_Z + Axis(MotionEvent.AXIS_GAS)
                }
            )
        val brakeOnly =
            quirksOf(
                pad(2) {
                    name = "Xbox Wireless Controller"
                    axes = STICKS_AND_Z + Axis(MotionEvent.AXIS_BRAKE)
                }
            )

        assertThat(gasOnly).isEqualTo(Quirks())
        assertThat(brakeOnly)
            .isEqualTo(
                Quirks(isNonStandardXboxBtController = true, hasSelect = true, hasMode = true)
            )
    }
}
