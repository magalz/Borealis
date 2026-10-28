package io.github.magalz.borealis.input

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Pins how the handler turns Start and Select into the Guide button, before the menu chord. */
@RunWith(RobolectricTestRunner::class)
class GuideChordCharacterizationTest {
    @get:Rule val rig = ControllerHandlerRig()

    @Test
    fun `Start then Select on a pad without a Mode button wait for the menu chord, not the Guide button`() {
        // Reason for the change: they used to become the Guide button at once. They now count
        // down to the menu chord, and a release before it is a Guide tap.
        rig.press(KeyEvent.KEYCODE_BUTTON_START, at = 1_000)
        rig.press(KeyEvent.KEYCODE_BUTTON_SELECT, at = 1_050)

        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `releasing Select before the chord sends the Guide button as a tap that ends`() {
        rig.press(KeyEvent.KEYCODE_BUTTON_START, at = 1_000)
        rig.press(KeyEvent.KEYCODE_BUTTON_SELECT, at = 1_050)

        rig.release(KeyEvent.KEYCODE_BUTTON_SELECT, at = 1_400)
        assertThat(rig.lastButtons()).isEqualTo(ControllerPacket.SPECIAL_BUTTON_FLAG)
        rig.advanceBy(100)

        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `a pad with a Mode button holds Start and Select back from the PC while the chord is pending`() {
        rig.press(KeyEvent.KEYCODE_BUTTON_MODE, at = 500)
        rig.release(KeyEvent.KEYCODE_BUTTON_MODE, at = 600)

        rig.press(KeyEvent.KEYCODE_BUTTON_START, at = 1_000)
        rig.press(KeyEvent.KEYCODE_BUTTON_SELECT, at = 1_050)

        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `Start with the right bumper is the Guide button on a pad without Select`() {
        rig.press(KeyEvent.KEYCODE_BUTTON_START, at = 1_000)
        rig.press(KeyEvent.KEYCODE_BUTTON_R1, at = 1_050)

        assertThat(rig.lastButtons()).isEqualTo(ControllerPacket.SPECIAL_BUTTON_FLAG)
    }

    @Test
    fun `releasing the right bumper ends that Guide button`() {
        rig.press(KeyEvent.KEYCODE_BUTTON_START, at = 1_000)
        rig.press(KeyEvent.KEYCODE_BUTTON_R1, at = 1_050)

        rig.release(KeyEvent.KEYCODE_BUTTON_R1, at = 1_400)

        assertThat(rig.lastButtons()).isEqualTo(0)
    }
}
