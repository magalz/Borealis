package io.github.magalz.borealis.input

import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_MODE
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/** Holding Select and Start opens the menu, and nothing of that hold reaches the PC. */
@RunWith(RobolectricTestRunner::class)
class MenuChordTest {
    private val rig = ControllerHandlerRig()

    private fun holdStartAndSelect() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(50)
        rig.press(KEYCODE_BUTTON_SELECT)
    }

    private fun giveThePadAModeButton() {
        rig.press(KEYCODE_BUTTON_MODE)
        rig.advanceBy(50)
        rig.release(KEYCODE_BUTTON_MODE)
        rig.advanceBy(50)
    }

    @Test
    fun `holding Start and Select for one second opens the menu while the buttons are still`() {
        holdStartAndSelect()

        rig.advanceBy(999)
        assertThat(rig.chords).isEqualTo(0)
        rig.advanceBy(1)
        assertThat(rig.chords).isEqualTo(1)
    }

    @Test
    fun `the menu opens once however long the buttons are held`() {
        holdStartAndSelect()

        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(1)
    }

    @Test
    fun `releasing a button before one second does not open the menu`() {
        holdStartAndSelect()
        rig.advanceBy(900)
        rig.release(KEYCODE_BUTTON_SELECT)

        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `the quit combo still quits and does not open the menu`() {
        holdStartAndSelect()
        rig.advanceBy(100)
        rig.press(KEYCODE_BUTTON_L1)
        rig.press(KEYCODE_BUTTON_R1)
        rig.advanceBy(3_000)

        rig.release(KEYCODE_BUTTON_R1)
        rig.release(KEYCODE_BUTTON_L1)
        rig.release(KEYCODE_BUTTON_SELECT)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.chords).isEqualTo(0)
        assertThat(rig.activity.isFinishing).isTrue()
    }

    @Test
    fun `after the menu opens the PC is told that Select and Start are up`() {
        giveThePadAModeButton()
        holdStartAndSelect()
        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG or BACK_FLAG)

        rig.advanceBy(1_000)

        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `letting go of the two buttons after the menu opened sends nothing`() {
        giveThePadAModeButton()
        holdStartAndSelect()
        rig.advanceBy(1_000)
        val sent = rig.connection.packets.size

        rig.release(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.connection.packets).hasSize(sent)
    }

    @Test
    fun `the Start hold that toggles mouse emulation does not after the menu opened`() {
        giveThePadAModeButton()
        holdStartAndSelect()
        rig.advanceBy(1_000)
        rig.release(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(500)

        rig.release(KEYCODE_BUTTON_START)

        assertThat(ShadowToast.shownToastCount()).isEqualTo(0)
    }

    @Test
    fun `a plain Start hold still toggles mouse emulation`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(800)

        rig.release(KEYCODE_BUTTON_START)

        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: ON")
    }

    @Test
    fun `a quick Start and Select without a Mode button is a Guide tap sent on release`() {
        holdStartAndSelect()
        rig.advanceBy(300)

        rig.release(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.buttons()).containsExactly(PLAY_FLAG, 0, SPECIAL_BUTTON_FLAG, 0).inOrder()
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `nothing of Start and Select reaches the PC while the chord is pending`() {
        holdStartAndSelect()

        rig.advanceBy(900)

        assertThat(rig.buttons().drop(1).all { it and (PLAY_FLAG or BACK_FLAG) == 0 }).isTrue()
        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `holding Start and Select for one second opens the menu and sends no Guide button`() {
        holdStartAndSelect()
        rig.advanceBy(1_000)
        rig.release(KEYCODE_BUTTON_SELECT)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.chords).isEqualTo(1)
        assertThat(rig.buttons().none { it and SPECIAL_BUTTON_FLAG != 0 }).isTrue()
        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `a pad with a Mode button sends a quick Start and Select as a short press on release`() {
        giveThePadAModeButton()
        val before = rig.connection.packets.size
        holdStartAndSelect()
        rig.advanceBy(300)

        rig.release(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.buttons().drop(before))
            .containsExactly(PLAY_FLAG, 0, PLAY_FLAG or BACK_FLAG, 0)
            .inOrder()
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a pad with a Mode button never sends Start and Select together while the chord is held`() {
        giveThePadAModeButton()
        val before = rig.connection.packets.size
        holdStartAndSelect()

        rig.advanceBy(1_000)

        assertThat(rig.chords).isEqualTo(1)
        assertThat(rig.buttons().drop(before).none { it == PLAY_FLAG or BACK_FLAG }).isTrue()
        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `a pad with a Mode button sends Start alone and Select alone at once`() {
        giveThePadAModeButton()

        rig.press(KEYCODE_BUTTON_START)
        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)
        rig.release(KEYCODE_BUTTON_START)

        rig.press(KEYCODE_BUTTON_SELECT)
        assertThat(rig.lastButtons()).isEqualTo(BACK_FLAG)
    }

    @Test
    fun `a pad with a Mode button sends the third button with Start and Select`() {
        giveThePadAModeButton()
        holdStartAndSelect()
        rig.advanceBy(200)

        rig.press(KEYCODE_BUTTON_L1)

        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG or BACK_FLAG or LB_FLAG)
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a pad with a Mode button still quits with the quit combo`() {
        giveThePadAModeButton()
        holdStartAndSelect()
        rig.advanceBy(100)
        rig.press(KEYCODE_BUTTON_L1)
        rig.press(KEYCODE_BUTTON_R1)
        rig.advanceBy(3_000)

        rig.release(KEYCODE_BUTTON_R1)
        rig.release(KEYCODE_BUTTON_L1)
        rig.release(KEYCODE_BUTTON_SELECT)
        rig.release(KEYCODE_BUTTON_START)

        assertThat(rig.chords).isEqualTo(0)
        assertThat(rig.activity.isFinishing).isTrue()
    }
}
