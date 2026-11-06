package io.github.magalz.borealis.input.ch2

import android.view.KeyEvent.FLAG_SOFT_KEYBOARD
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_C
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_UNKNOWN
import android.view.KeyEvent.META_ALT_ON
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.DOWN_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.LEFT_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.RIGHT_FLAG
import com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG
import com.limelight.nvstream.input.ControllerPacket.TOUCHPAD_FLAG
import com.limelight.nvstream.input.ControllerPacket.UP_FLAG
import com.limelight.nvstream.input.ControllerPacket.X_FLAG
import io.github.magalz.borealis.input.events.EventsRig
import io.github.magalz.borealis.input.events.KeyResult
import io.github.magalz.borealis.input.events.Sent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The order of the steps of `handleRemapping`, and what a step that answers does to the ones after
 * it. The first step that has an answer ends the remapping: the steps after it do not run, so the
 * flags of the pad that they would change stay as they are. A step that has no answer lets the next
 * one look. `RemappingCharacterizationTest` pins what each step answers; these pin the order of two
 * steps that fit the same key and the hand-off between them.
 */
@RunWith(RobolectricTestRunner::class)
class RemapStagesTest {
    @get:Rule val rig = EventsRig()

    private fun pad(deviceId: Int = 7, fields: Map<String, Any?> = emptyMap()) =
        rig.addPad(deviceId, fields = fields)

    private fun sent(buttons: Int) = Sent(controller = 0, activeMask = 1, buttons = buttons)

    private fun taken(buttons: Int) = KeyResult(true, sent(buttons))

    // ----- Which of two steps that fit a key comes first -----

    @Test
    fun `the Sony clickpad comes before the DualShock 4 table`() {
        val pad = pad(fields = mapOf("vendorId" to 0x054c, "isNonStandardDualShock4" to true))

        // The table has no 317, so it would take the key and send nothing
        assertThat(pad.observe(KEYCODE_BUTTON_SELECT, scanCode = 317))
            .isEqualTo(taken(TOUCHPAD_FLAG))
    }

    @Test
    fun `the 8BitDo Mode button comes before the tables`() {
        val pad = pad(fields = mapOf("vendorId" to 0x2dc8, "isNonStandardXboxBtController" to true))

        // The Xbox Bluetooth table has 306 for X
        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 306))
            .isEqualTo(taken(SPECIAL_BUTTON_FLAG))
    }

    @Test
    fun `the Switch table comes before the Linux standard face buttons`() {
        val pad =
            pad(
                fields =
                    mapOf(
                        "vendorId" to 0x0f0d,
                        "productId" to 0x00c1,
                        "usesLinuxGamepadStandardFaceButtons" to true,
                    )
            )

        // Scan code 308 is L1 in the Switch table and X in the Linux one
        assertThat(pad.observe(KEYCODE_BUTTON_C, scanCode = 308)).isEqualTo(taken(LB_FLAG))
    }

    @Test
    fun `the Linux standard face buttons come before the DualShock 4 table`() {
        val pad =
            pad(
                fields =
                    mapOf(
                        "usesLinuxGamepadStandardFaceButtons" to true,
                        "isNonStandardDualShock4" to true,
                    )
            )

        // Scan code 308 is X in the Linux table and L1 in the DualShock 4 one
        assertThat(pad.observe(KEYCODE_BUTTON_C, scanCode = 308)).isEqualTo(taken(X_FLAG))
    }

    @Test
    fun `a table comes before the Tablet Remote hack`() {
        val pad = pad(fields = mapOf("usesLinuxGamepadStandardFaceButtons" to true))

        // The hack alone would make Back the B button
        assertThat(
                pad.observe(
                    KEYCODE_BACK,
                    scanCode = 304,
                    metaState = META_ALT_ON,
                    flags = FLAG_SOFT_KEYBOARD,
                )
            )
            .isEqualTo(taken(A_FLAG))
    }

    // ----- A step with no answer lets the next one look -----

    @Test
    fun `a pad whose table has no answer still gets the raw D-pad codes`() {
        val cases =
            listOf(
                Triple("Serval", mapOf("isServal" to true), 704 to LEFT_FLAG),
                Triple(
                    "Xbox Bluetooth",
                    mapOf("isNonStandardXboxBtController" to true),
                    706 to UP_FLAG,
                ),
                Triple(
                    "Kunai",
                    mapOf("vendorId" to 0x0b05, "productId" to 0x7900),
                    707 to DOWN_FLAG,
                ),
                Triple(
                    "HORIPAD",
                    mapOf("vendorId" to 0x0f0d, "productId" to 0x00c1),
                    705 to RIGHT_FLAG,
                ),
                Triple(
                    "Linux standard",
                    mapOf("usesLinuxGamepadStandardFaceButtons" to true),
                    704 to LEFT_FLAG,
                ),
            )

        cases.forEachIndexed { index, (name, fields, key) ->
            val (scanCode, flag) = key
            assertWithMessage(name)
                .that(
                    pad(deviceId = 20 + index, fields = fields).observe(KEYCODE_UNKNOWN, scanCode)
                )
                .isEqualTo(taken(flag))
        }
    }

    // ----- A key that a step remaps does not reach the stand-in rules -----

    @Test
    fun `the 8BitDo Mode button is not turned into Select on a pad that has none`() {
        val pad = pad(fields = mapOf("vendorId" to 0x2dc8, "modeIsSelect" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_A, scanCode = 306))
            .isEqualTo(taken(SPECIAL_BUTTON_FLAG))
        assertThat(pad.getBoolean("modeIsSelect")).isTrue()
    }

    @Test
    fun `a Start from the Serval table leaves Back standing in for Start`() {
        val pad = pad(fields = mapOf("isServal" to true, "backIsStart" to true))

        assertThat(pad.observe(KEYCODE_UNKNOWN, scanCode = 315)).isEqualTo(taken(PLAY_FLAG))

        assertThat(pad.getBoolean("backIsStart")).isTrue()
        assertThat(pad.observe(KEYCODE_BACK)).isEqualTo(taken(PLAY_FLAG))
    }

    @Test
    fun `a Select from the Kunai table leaves Mode standing in for Select`() {
        val pad =
            pad(fields = mapOf("vendorId" to 0x0b05, "productId" to 0x7900, "modeIsSelect" to true))

        assertThat(pad.observe(KEYCODE_BUTTON_C, scanCode = 265)).isEqualTo(taken(BACK_FLAG))

        assertThat(pad.getBoolean("modeIsSelect")).isTrue()
    }
}
