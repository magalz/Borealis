package io.github.magalz.borealis.input

import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/** The quit combo on a system pad ends the stream and does nothing else, however it is let go. */
@RunWith(RobolectricTestRunner::class)
class SystemPadQuitComboTest {
    @get:Rule val rig = ControllerHandlerRig().also { it.useSystemPad() }

    private fun pressTheQuitCombo() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(50)
        rig.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(50)
        rig.press(KEYCODE_BUTTON_L1)
        rig.press(KEYCODE_BUTTON_R1)
    }

    @Test
    fun `letting go of the bumpers and then of Start after a long hold neither toggles the mouse nor taps Guide`() {
        pressTheQuitCombo()
        rig.advanceBy(1_000)
        rig.release(KEYCODE_BUTTON_L1)
        rig.release(KEYCODE_BUTTON_R1)

        rig.release(KEYCODE_BUTTON_START)
        rig.advanceBy(200)
        rig.release(KEYCODE_BUTTON_SELECT)

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
        assertThat(rig.buttons().none { it and SPECIAL_BUTTON_FLAG != 0 }).isTrue()
        assertThat(rig.chords).isEqualTo(0)
        assertThat(rig.activity.isFinishing).isTrue()
    }
}
