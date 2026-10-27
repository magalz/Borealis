package io.github.magalz.borealis.input.events

import android.view.KeyEvent.FLAG_SOFT_KEYBOARD
import android.view.KeyEvent.KEYCODE_A
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_B
import android.view.KeyEvent.KEYCODE_BUTTON_C
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_MODE
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.KeyEvent.KEYCODE_MENU
import android.view.KeyEvent.KEYCODE_SEARCH
import android.view.KeyEvent.KEYCODE_UNKNOWN
import android.view.KeyEvent.META_ALT_ON
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.B_FLAG
import com.limelight.nvstream.input.ControllerPacket.DOWN_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.LEFT_FLAG
import com.limelight.nvstream.input.ControllerPacket.LS_CLK_FLAG
import com.limelight.nvstream.input.ControllerPacket.MISC_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.RB_FLAG
import com.limelight.nvstream.input.ControllerPacket.RIGHT_FLAG
import com.limelight.nvstream.input.ControllerPacket.RS_CLK_FLAG
import com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG
import com.limelight.nvstream.input.ControllerPacket.TOUCHPAD_FLAG
import com.limelight.nvstream.input.ControllerPacket.UP_FLAG
import com.limelight.nvstream.input.ControllerPacket.X_FLAG
import com.limelight.nvstream.input.ControllerPacket.Y_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `handleRemapping`: what the handler makes of a key before it looks at its key code, per vendor
 * and per quirk of the pad. The quirk flags are the fields of the pad's context, which the handler
 * sets from the device when it makes the context. The remapping runs for key up as well as for key
 * down, so the checks answer the same way for both.
 */
@RunWith(RobolectricTestRunner::class)
class RemappingCharacterizationTest {
    private val rig = EventsRig()

    private fun pad(deviceId: Int = 7, fields: Map<String, Any?> = emptyMap()) =
        rig.addPad(deviceId, fields = fields)

    /** The packet of a pad that sent [buttons] on controller 0 and nothing else. */
    private fun sent(buttons: Int) = Sent(controller = 0, activeMask = 1, buttons = buttons)

    private val noPacket: Sent? = null

    // ----- Back -----

    @Test
    fun `Back is ignored, down and up, on a pad that ignores it`() {
        val pad = pad(fields = mapOf("ignoreBack" to true))

        assertThat(pad.press(KEYCODE_BACK)).isFalse()
        assertThat(pad.release(KEYCODE_BACK, downTime = rig.now - 100)).isFalse()

        assertThat(rig.packets).isEmpty()
        assertThat(pad.getBoolean("hasSelect")).isFalse()
    }

    @Test
    fun `Back is Select on a pad that does not ignore it`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_BACK)).isEqualTo(KeyResult(true, sent(BACK_FLAG)))
    }

    @Test
    fun `a pad that ignores Back still takes every other key`() {
        val pad = pad(fields = mapOf("ignoreBack" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_A)).isEqualTo(KeyResult(true, sent(A_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_SELECT)).isEqualTo(KeyResult(true, sent(BACK_FLAG)))
    }

    @Test
    fun `ignoring Back comes before Back standing in for Start or the Tablet Remote hack`() {
        val pad = pad(fields = mapOf("ignoreBack" to true, "backIsStart" to true))

        assertThat(
                pad.press(
                    KEYCODE_BACK,
                    KeyDetail(metaState = META_ALT_ON, flags = FLAG_SOFT_KEYBOARD),
                )
            )
            .isFalse()
        assertThat(pad.press(KEYCODE_BACK)).isFalse()

        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `the on-screen controller and devices without an id ignore Back but not Select`() {
        assertThat(rig.default.press(KEYCODE_BACK)).isFalse()
        assertThat(rig.packets).isEmpty()

        assertThat(rig.default.observe(KEYCODE_BUTTON_SELECT))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
    }

    // ----- Share -----

    @Test
    fun `an unmapped record key with scan code 167 is the Share button on a pad that has one`() {
        val pad = pad(fields = mapOf("hasShare" to true))

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 167))
            .isEqualTo(KeyResult(true, sent(MISC_FLAG)))
    }

    @Test
    fun `the same key on a pad without a Share button is left to the system`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 167))
            .isEqualTo(KeyResult(false, noPacket))
    }

    @Test
    fun `scan code 167 only counts when the key code is unknown, and only 167`() {
        val pad = pad(fields = mapOf("hasShare" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 167))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 166))
            .isEqualTo(KeyResult(false, noPacket))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 168))
            .isEqualTo(KeyResult(false, noPacket))
    }

    // ----- Sony clickpad -----

    @Test
    fun `Select with scan code 317 on a Sony pad is the touchpad button`() {
        val pad = pad(fields = mapOf("vendorId" to 0x054c))

        assertThat(pad.observe(KEYCODE_BUTTON_SELECT, scanCode = 317))
            .isEqualTo(KeyResult(true, sent(TOUCHPAD_FLAG)))
        // It was not Select, so the pad is not told it has one.
        assertThat(pad.getBoolean("hasSelect")).isFalse()
    }

    @Test
    fun `Select from the standalone touchpad device of a Sony pad is the touchpad button whatever the scan code`() {
        val pad = pad(fields = mapOf("vendorId" to 0x054c, "isDualShockStandaloneTouchpad" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_SELECT, scanCode = 314))
            .isEqualTo(KeyResult(true, sent(TOUCHPAD_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_SELECT, scanCode = 0))
            .isEqualTo(KeyResult(true, sent(TOUCHPAD_FLAG)))
    }

    @Test
    fun `Select on a Sony pad with another scan code stays Select`() {
        val pad = pad(fields = mapOf("vendorId" to 0x054c))

        assertThat(pad.observe(KEYCODE_BUTTON_SELECT, scanCode = 314))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
    }

    @Test
    fun `scan code 317 on a pad of another vendor stays Select, and on a Sony pad only for Select`() {
        val other = pad(deviceId = 7, fields = mapOf("vendorId" to 0x045e))
        assertThat(other.observe(KEYCODE_BUTTON_SELECT, scanCode = 317))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        val standalone =
            pad(
                deviceId = 8,
                fields = mapOf("vendorId" to 0x045e, "isDualShockStandaloneTouchpad" to true),
            )
        assertThat(standalone.observe(KEYCODE_BUTTON_SELECT, scanCode = 317))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        val sony = pad(deviceId = 9, fields = mapOf("vendorId" to 0x054c))
        assertThat(sony.observe(KEYCODE_BUTTON_A, scanCode = 317))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    // ----- 8BitDo -----

    @Test
    fun `scan code 306 on an 8BitDo pad is the Mode button, whatever the key code`() {
        val pad = pad(fields = mapOf("vendorId" to 0x2dc8))

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 306))
            .isEqualTo(KeyResult(true, sent(SPECIAL_BUTTON_FLAG)))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 306))
            .isEqualTo(KeyResult(true, sent(SPECIAL_BUTTON_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 305))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    @Test
    fun `scan code 306 on a pad of another vendor is left alone`() {
        val pad = pad(fields = mapOf("vendorId" to 0x045e))

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 306))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    // ----- Nintendo pads -----

    private val switchLayout =
        listOf(
            0x130 to sent(A_FLAG),
            0x131 to sent(B_FLAG),
            0x132 to sent(X_FLAG),
            0x133 to sent(Y_FLAG),
            0x134 to sent(LB_FLAG),
            0x135 to sent(RB_FLAG),
            0x136 to Sent(0, 1, 0, leftTrigger = -1),
            0x137 to Sent(0, 1, 0, rightTrigger = -1),
            0x138 to sent(BACK_FLAG),
            0x139 to sent(PLAY_FLAG),
            0x13A to sent(LS_CLK_FLAG),
            0x13B to sent(RS_CLK_FLAG),
            0x13D to sent(SPECIAL_BUTTON_FLAG),
        )

    private fun assertSwitchLayout(pad: Pad) {
        for ((scanCode, expected) in switchLayout) {
            // The key code is one that would not be a button by itself.
            assertWithMessage("scan code ${scanCode.toString(16)}")
                .that(pad.observe(KEYCODE_BUTTON_C, scanCode))
                .isEqualTo(KeyResult(true, expected))
        }
    }

    @Test
    @Config(sdk = [28])
    fun `a Switch Pro pad on Android 9 is mapped by scan code`() {
        assertSwitchLayout(pad(fields = mapOf("vendorId" to 0x057e, "productId" to 0x2009)))
    }

    @Test
    @Config(sdk = [29])
    fun `a Switch Pro pad on Android 10 uses the built-in mapping`() {
        val pad = pad(fields = mapOf("vendorId" to 0x057e, "productId" to 0x2009))

        assertThat(pad.observe(KEYCODE_BUTTON_C, scanCode = 0x130))
            .isEqualTo(KeyResult(false, noPacket))
        assertThat(pad.observe(KEYCODE_BUTTON_B, scanCode = 0x130))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    @Test
    @Config(sdk = [35])
    fun `a Switch Pro pad on a newer Android uses the built-in mapping`() {
        val pad = pad(fields = mapOf("vendorId" to 0x057e, "productId" to 0x2009))

        assertThat(pad.observe(KEYCODE_BUTTON_B, scanCode = 0x130))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    @Test
    @Config(sdk = [28])
    fun `another Nintendo product is not mapped by scan code`() {
        val pad = pad(fields = mapOf("vendorId" to 0x057e, "productId" to 0x2006))

        assertThat(pad.observe(KEYCODE_BUTTON_B, scanCode = 0x130))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    @Test
    @Config(sdk = [28])
    fun `the Switch product of another vendor is not mapped by scan code`() {
        val pad = pad(fields = mapOf("vendorId" to 0x045e, "productId" to 0x2009))

        assertThat(pad.observe(KEYCODE_BUTTON_B, scanCode = 0x130))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    @Test
    @Config(sdk = [28])
    fun `the HORIPAD for Switch is mapped by scan code on every Android`() {
        assertSwitchLayout(pad(fields = mapOf("vendorId" to 0x0f0d, "productId" to 0x00c1)))
    }

    @Test
    @Config(sdk = [35])
    fun `the HORIPAD for Switch is mapped by scan code on a newer Android too`() {
        assertSwitchLayout(pad(fields = mapOf("vendorId" to 0x0f0d, "productId" to 0x00c1)))
    }

    @Test
    @Config(sdk = [28])
    fun `another product of the HORI vendor, and the HORIPAD product of another vendor, are not mapped by scan code`() {
        val otherProduct =
            pad(deviceId = 7, fields = mapOf("vendorId" to 0x0f0d, "productId" to 0x00c0))
        val otherVendor =
            pad(deviceId = 8, fields = mapOf("vendorId" to 0x045e, "productId" to 0x00c1))

        assertThat(otherProduct.observe(KEYCODE_BUTTON_B, scanCode = 0x130))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
        assertThat(otherVendor.observe(KEYCODE_BUTTON_B, scanCode = 0x130))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    @Test
    fun `a scan code the Switch layout does not have keeps the key code of the event`() {
        val pad = pad(fields = mapOf("vendorId" to 0x0f0d, "productId" to 0x00c1))

        // 0x13C is the one hole of the table; 0x13E and 0 are past it.
        for (scanCode in listOf(0x13C, 0x13E, 0)) {
            assertWithMessage("scan code ${scanCode.toString(16)}")
                .that(pad.observe(KEYCODE_BUTTON_L1, scanCode))
                .isEqualTo(KeyResult(true, sent(LB_FLAG)))
        }
    }

    // ----- Linux standard face buttons -----

    @Test
    fun `a pad with the Linux standard face buttons swaps what Android calls West and North`() {
        val pad = pad(fields = mapOf("usesLinuxGamepadStandardFaceButtons" to true))

        val expected = mapOf(304 to A_FLAG, 305 to B_FLAG, 307 to Y_FLAG, 308 to X_FLAG)
        for ((scanCode, flag) in expected) {
            assertWithMessage("scan code $scanCode")
                .that(pad.observe(KEYCODE_BUTTON_C, scanCode))
                .isEqualTo(KeyResult(true, sent(flag)))
        }
    }

    @Test
    fun `the other scan codes of such a pad keep the key code`() {
        val pad = pad(fields = mapOf("usesLinuxGamepadStandardFaceButtons" to true))

        for (scanCode in listOf(303, 306, 309, 0)) {
            assertWithMessage("scan code $scanCode")
                .that(pad.observe(KEYCODE_BUTTON_L1, scanCode))
                .isEqualTo(KeyResult(true, sent(LB_FLAG)))
        }
    }

    // ----- DualShock 4 with the old driver -----

    @Test
    fun `a non-standard DualShock 4 maps every button by scan code`() {
        val pad = pad(fields = mapOf("isNonStandardDualShock4" to true))
        val expected =
            listOf(
                304 to sent(X_FLAG),
                305 to sent(A_FLAG),
                306 to sent(B_FLAG),
                307 to sent(Y_FLAG),
                308 to sent(LB_FLAG),
                309 to sent(RB_FLAG),
                312 to sent(BACK_FLAG),
                313 to sent(PLAY_FLAG),
                314 to sent(LS_CLK_FLAG),
                315 to sent(RS_CLK_FLAG),
                316 to sent(SPECIAL_BUTTON_FLAG),
            )

        for ((scanCode, packet) in expected) {
            assertWithMessage("scan code $scanCode")
                .that(pad.observe(KEYCODE_BUTTON_C, scanCode))
                .isEqualTo(KeyResult(true, packet))
        }
    }

    @Test
    fun `a non-standard DualShock 4 consumes every other button, the analog triggers included`() {
        val pad = pad(fields = mapOf("isNonStandardDualShock4" to true))

        // 310 and 311 are the digital triggers, which the analog axes replace.
        for (scanCode in listOf(310, 311, 303, 317, 0)) {
            assertWithMessage("scan code $scanCode")
                .that(pad.observe(KEYCODE_BUTTON_A, scanCode))
                .isEqualTo(KeyResult(true, noPacket))
        }
        // The key code is not even looked at: the press is taken and nothing is sent.
        assertThat(pad.press(KEYCODE_A, KeyDetail(scanCode = 310))).isTrue()
        assertThat(pad.release(KEYCODE_A, KeyDetail(scanCode = 310), downTime = rig.now - 100))
            .isTrue()
        assertThat(rig.packets).isEmpty()
    }

    @Test
    fun `a non-standard DualShock 4 comes before the other pad quirks`() {
        val pad =
            pad(
                fields =
                    mapOf(
                        "isNonStandardDualShock4" to true,
                        "isServal" to true,
                        "isNonStandardXboxBtController" to true,
                    )
            )

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 314))
            .isEqualTo(KeyResult(true, sent(LS_CLK_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 306))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    // ----- Razer Serval -----

    @Test
    fun `a Serval maps its unknown keys to Select and Start`() {
        val pad = pad(fields = mapOf("isServal" to true))

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 314))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 315))
            .isEqualTo(KeyResult(true, sent(PLAY_FLAG)))
    }

    @Test
    fun `any other unknown key of a Serval is left to the system`() {
        val pad = pad(fields = mapOf("isServal" to true))

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 313))
            .isEqualTo(KeyResult(false, noPacket))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 100))
            .isEqualTo(KeyResult(false, noPacket))
    }

    @Test
    fun `the scan codes of a Serval only count for an unknown key code`() {
        val pad = pad(fields = mapOf("isServal" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 314))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    @Test
    fun `an unknown key of a Serval ends the chain of quirks, so the Xbox Bluetooth table is not tried`() {
        val pad = pad(fields = mapOf("isServal" to true, "isNonStandardXboxBtController" to true))

        // Scan code 306 is X on an Xbox Bluetooth pad, but the Serval branch is the one that
        // matches.
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 306))
            .isEqualTo(KeyResult(false, noPacket))
        // For a known key code the Serval branch does not match, so the Xbox table is the next.
        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 306))
            .isEqualTo(KeyResult(true, sent(X_FLAG)))
    }

    // ----- Xbox One S over Bluetooth -----

    @Test
    fun `a non-standard Xbox Bluetooth pad maps its buttons by scan code`() {
        val pad = pad(fields = mapOf("isNonStandardXboxBtController" to true))
        val expected =
            listOf(
                306 to sent(X_FLAG),
                307 to sent(Y_FLAG),
                308 to sent(LB_FLAG),
                309 to sent(RB_FLAG),
                310 to sent(BACK_FLAG),
                311 to sent(PLAY_FLAG),
                312 to sent(LS_CLK_FLAG),
                313 to sent(RS_CLK_FLAG),
                139 to sent(SPECIAL_BUTTON_FLAG),
            )

        for ((scanCode, packet) in expected) {
            assertWithMessage("scan code $scanCode")
                .that(pad.observe(KEYCODE_BUTTON_C, scanCode))
                .isEqualTo(KeyResult(true, packet))
        }
    }

    @Test
    fun `the other buttons of that pad are mapped correctly by Android and keep their key code`() {
        val pad = pad(fields = mapOf("isNonStandardXboxBtController" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 304))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_B, scanCode = 305))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 0))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    @Test
    fun `the Xbox button of that pad is sent as Menu and becomes Mode`() {
        val pad = pad(fields = mapOf("isNonStandardXboxBtController" to true))

        assertThat(pad.observe(KEYCODE_MENU, scanCode = 172))
            .isEqualTo(KeyResult(true, sent(SPECIAL_BUTTON_FLAG)))
        // A scan code of the table wins over the Menu key code, since the table is tried first.
        assertThat(pad.observe(KEYCODE_MENU, scanCode = 306))
            .isEqualTo(KeyResult(true, sent(X_FLAG)))
    }

    @Test
    fun `Menu is Start on a pad that is not a non-standard Xbox Bluetooth pad`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_MENU, scanCode = 172))
            .isEqualTo(KeyResult(true, sent(PLAY_FLAG)))
    }

    // ----- ASUS ROG Kunai -----

    @Test
    fun `the macro buttons of a Kunai are Start and Select`() {
        for (productId in listOf(0x7900, 0x7902)) {
            val pad =
                pad(
                    deviceId = productId,
                    fields = mapOf("vendorId" to 0x0b05, "productId" to productId),
                )

            for (scanCode in listOf(264, 266)) {
                assertWithMessage("$productId scan code $scanCode")
                    .that(pad.observe(KEYCODE_BUTTON_C, scanCode))
                    .isEqualTo(KeyResult(true, sent(PLAY_FLAG)))
            }
            for (scanCode in listOf(265, 267)) {
                assertWithMessage("$productId scan code $scanCode")
                    .that(pad.observe(KEYCODE_BUTTON_C, scanCode))
                    .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
            }
        }
    }

    @Test
    fun `the other scan codes and products of the ASUS vendor keep the key code`() {
        val kunai = pad(deviceId = 7, fields = mapOf("vendorId" to 0x0b05, "productId" to 0x7900))
        assertThat(kunai.observe(KEYCODE_BUTTON_A, scanCode = 263))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
        assertThat(kunai.observe(KEYCODE_BUTTON_A, scanCode = 268))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))

        val other = pad(deviceId = 8, fields = mapOf("vendorId" to 0x0b05, "productId" to 0x7901))
        assertThat(other.observe(KEYCODE_BUTTON_A, scanCode = 264))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))

        val notAsus = pad(deviceId = 9, fields = mapOf("vendorId" to 0x045e, "productId" to 0x7900))
        assertThat(notAsus.observe(KEYCODE_BUTTON_A, scanCode = 264))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    @Test
    fun `the Kunai table is the last of the chain, so a quirk that came first hides it`() {
        val pad =
            pad(
                fields =
                    mapOf(
                        "vendorId" to 0x0b05,
                        "productId" to 0x7900,
                        "isNonStandardXboxBtController" to true,
                    )
            )

        // 264 is Start for a Kunai but nothing for the Xbox Bluetooth table, which matched first.
        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 264))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    // ----- Raw D-pad scan codes -----

    @Test
    fun `without hat axes the raw D-pad scan codes of an unknown key are the D-pad`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 704))
            .isEqualTo(KeyResult(true, sent(LEFT_FLAG)))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 705))
            .isEqualTo(KeyResult(true, sent(RIGHT_FLAG)))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 706))
            .isEqualTo(KeyResult(true, sent(UP_FLAG)))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 707))
            .isEqualTo(KeyResult(true, sent(DOWN_FLAG)))
    }

    @Test
    fun `scan codes next to the raw D-pad ones are left to the system`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 703))
            .isEqualTo(KeyResult(false, noPacket))
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 708))
            .isEqualTo(KeyResult(false, noPacket))
    }

    @Test
    fun `the raw D-pad scan codes need an unknown key code`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 704))
            .isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    @Test
    fun `the raw D-pad scan codes need both hat axes to be missing`() {
        val hatX = pad(deviceId = 7, fields = mapOf("hatXAxis" to 15))
        val hatY = pad(deviceId = 8, fields = mapOf("hatYAxis" to 16))
        val hats = pad(deviceId = 9, fields = mapOf("hatXAxis" to 15, "hatYAxis" to 16))

        assertThat(hatX.observe(KEYCODE_UNKNOWN, scanCode = 704))
            .isEqualTo(KeyResult(false, noPacket))
        assertThat(hatY.observe(KEYCODE_UNKNOWN, scanCode = 704))
            .isEqualTo(KeyResult(false, noPacket))
        assertThat(hats.observe(KEYCODE_UNKNOWN, scanCode = 704))
            .isEqualTo(KeyResult(false, noPacket))
    }

    @Test
    fun `the on-screen controller has hat axes, so the raw D-pad scan codes do not reach its buttons`() {
        assertThat(rig.default.observe(KEYCODE_UNKNOWN, scanCode = 704))
            .isEqualTo(KeyResult(false, noPacket))
    }

    @Test
    fun `a pad with paddles meets the raw D-pad scan codes first, so the fourth trigger-happy button is Down`() {
        val pad = pad(fields = mapOf("hasPaddles" to true))

        // 0x2c3 is 707, the last raw D-pad code; the paddles start at 0x2c4.
        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 0x2c3))
            .isEqualTo(KeyResult(true, sent(DOWN_FLAG)))
    }

    // ----- Tablet Remote -----

    @Test
    fun `Back with a modifier from the soft keyboard is the B button`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_BACK, metaState = META_ALT_ON, flags = FLAG_SOFT_KEYBOARD))
            .isEqualTo(KeyResult(true, sent(B_FLAG)))
    }

    @Test
    fun `Back without the soft keyboard flag, or without a modifier, stays Select`() {
        val pad = pad()

        assertThat(pad.observe(KEYCODE_BACK, metaState = META_ALT_ON))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        assertThat(pad.observe(KEYCODE_BACK, flags = FLAG_SOFT_KEYBOARD))
            .isEqualTo(KeyResult(true, sent(BACK_FLAG)))
    }

    // ----- Back, Mode and Search standing in -----

    @Test
    fun `Back is Start on a pad without a Start button`() {
        val pad = pad(fields = mapOf("backIsStart" to true))

        assertThat(pad.observe(KEYCODE_BACK)).isEqualTo(KeyResult(true, sent(PLAY_FLAG)))
        // The substitution does not clear the flag by itself.
        assertThat(pad.getBoolean("backIsStart")).isTrue()
    }

    @Test
    fun `a real Start or Menu ends Back standing in for Start`() {
        for ((deviceId, keyCode) in listOf(7 to KEYCODE_BUTTON_START, 8 to KEYCODE_MENU)) {
            val pad = pad(deviceId = deviceId, fields = mapOf("backIsStart" to true))

            assertThat(pad.observe(keyCode)).isEqualTo(KeyResult(true, sent(PLAY_FLAG)))
            assertWithMessage("flag after $keyCode").that(pad.getBoolean("backIsStart")).isFalse()
            assertThat(pad.observe(KEYCODE_BACK)).isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        }
    }

    @Test
    fun `the remapping clears the flag on key up as well`() {
        val pad = pad(fields = mapOf("backIsStart" to true, "modeIsSelect" to true))

        pad.release(KEYCODE_BUTTON_START, downTime = rig.now - 100)
        assertThat(pad.getBoolean("backIsStart")).isFalse()
        pad.release(KEYCODE_BUTTON_SELECT, downTime = rig.now - 100)
        assertThat(pad.getBoolean("modeIsSelect")).isFalse()
    }

    @Test
    fun `Mode is Select on a pad that has none, until a real Select is seen`() {
        val pad = pad(fields = mapOf("modeIsSelect" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_MODE)).isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        assertThat(pad.getBoolean("modeIsSelect")).isTrue()

        assertThat(pad.observe(KEYCODE_BUTTON_SELECT)).isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        assertThat(pad.getBoolean("modeIsSelect")).isFalse()
        assertThat(pad.observe(KEYCODE_BUTTON_MODE))
            .isEqualTo(KeyResult(true, sent(SPECIAL_BUTTON_FLAG)))
    }

    @Test
    fun `Search is Mode on a pad that sends it for the big button, and nothing otherwise`() {
        val with = pad(deviceId = 7, fields = mapOf("searchIsMode" to true))
        assertThat(with.observe(KEYCODE_SEARCH))
            .isEqualTo(KeyResult(true, sent(SPECIAL_BUTTON_FLAG)))

        val without = pad(deviceId = 8)
        assertThat(without.observe(KEYCODE_SEARCH)).isEqualTo(KeyResult(false, noPacket))
    }

    @Test
    fun `a pad that sends Search for the big button keeps every other key as it is`() {
        val pad = pad(fields = mapOf("searchIsMode" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_A)).isEqualTo(KeyResult(true, sent(A_FLAG)))
    }

    @Test
    fun `a pad where Back is Start and Mode is Select keeps them apart`() {
        val pad =
            pad(
                fields =
                    mapOf("backIsStart" to true, "modeIsSelect" to true, "searchIsMode" to true)
            )

        assertThat(pad.observe(KEYCODE_BACK)).isEqualTo(KeyResult(true, sent(PLAY_FLAG)))
        assertThat(pad.observe(KEYCODE_BUTTON_MODE)).isEqualTo(KeyResult(true, sent(BACK_FLAG)))
        // Search becomes Mode, which this pad then does not turn into Select again.
        assertThat(pad.observe(KEYCODE_SEARCH))
            .isEqualTo(KeyResult(true, sent(SPECIAL_BUTTON_FLAG)))
    }
}
