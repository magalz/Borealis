package io.github.magalz.borealis.input

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/**
 * On a system pad with a Select button, Start or Select alone waits up to 60 ms for the other one,
 * so that the first of the two never reaches the PC when they make the menu chord.
 */
@RunWith(RobolectricTestRunner::class)
class LonePressHoldTest {
    @get:Rule val rig = ControllerHandlerRig()

    private fun sent(button: Int) = rig.buttons().any { it and button != 0 }

    @Test
    fun `Start alone reaches the PC 60 ms after it went down, not before`() {
        rig.givePadASelectButton()

        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(59)
        assertThat(sent(PLAY_FLAG)).isFalse()

        rig.advanceBy(1)
        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)
    }

    @Test
    fun `Select alone reaches the PC 60 ms after it went down, not before`() {
        rig.givePadASelectButton()

        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(59)
        assertThat(sent(BACK_FLAG)).isFalse()

        rig.advanceBy(1)
        assertThat(rig.lastButtons()).isEqualTo(BACK_FLAG)
    }

    @Test
    fun `another button going down sends the held back Start at once`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(20)
        assertThat(sent(PLAY_FLAG)).isFalse()

        rig.press(KEYCODE_BUTTON_A)

        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG or A_FLAG)
    }

    @Test
    fun `Start and then Select within 60 ms never reach the PC, and the hold opens the menu`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(59)
        rig.press(KEYCODE_BUTTON_SELECT)

        rig.advanceBy(940)
        assertThat(rig.chords).isEqualTo(0)
        rig.advanceBy(60)

        assertThat(rig.chords).isEqualTo(1)
        assertThat(sent(PLAY_FLAG or BACK_FLAG)).isFalse()
    }

    @Test
    fun `Select and then Start within 60 ms never reach the PC either`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(30)
        rig.press(KEYCODE_BUTTON_START)

        rig.advanceBy(1_000)

        assertThat(rig.chords).isEqualTo(1)
        assertThat(sent(PLAY_FLAG or BACK_FLAG)).isFalse()
    }

    @Test
    fun `a Start tapped within 60 ms reaches the PC as a press and then a release, in that order`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(30)
        assertThat(sent(PLAY_FLAG)).isFalse()

        rig.release(KEYCODE_BUTTON_START)
        rig.advanceBy(200)

        assertThat(rig.buttons().dropWhile { it == 0 }).containsExactly(PLAY_FLAG, 0).inOrder()
    }

    @Test
    fun `a Select tapped within 60 ms reaches the PC as a press and then a release, in that order`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(30)
        assertThat(sent(BACK_FLAG)).isFalse()

        rig.release(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(200)

        assertThat(rig.buttons().dropWhile { it == 0 }).containsExactly(BACK_FLAG, 0).inOrder()
    }

    @Test
    fun `holding Start alone still switches the gamepad mouse after the Start hold time`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(800)
        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)

        rig.release(KEYCODE_BUTTON_START)

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: ON")
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a pad without a Select button sends Start at once`() {
        rig.press(KEYCODE_BUTTON_START)

        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)
    }

    @Test
    fun `the held back Start is not sent after the stream ended`() {
        rig.givePadASelectButton()
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(20)

        rig.handler.stop()
        rig.advanceBy(200)

        assertThat(sent(PLAY_FLAG)).isFalse()
    }

    @Test
    fun `a system pad with a Select button holds a lone Start back`() {
        rig.useSystemPad(hasSelect = true)

        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(59)
        assertThat(sent(PLAY_FLAG)).isFalse()

        rig.advanceBy(1)
        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)
    }

    @Test
    fun `a system pad without a Select button sends Start at once`() {
        rig.useSystemPad(hasSelect = false)

        rig.press(KEYCODE_BUTTON_START)

        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)
    }
}
