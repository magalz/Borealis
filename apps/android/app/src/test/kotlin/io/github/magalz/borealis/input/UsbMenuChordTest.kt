package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.RB_FLAG
import io.github.magalz.borealis.stream.input.StartHold
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A pad on the USB driver opens the menu with the same chord. */
@RunWith(RobolectricTestRunner::class)
class UsbMenuChordTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val pad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }
    private val chord = PLAY_FLAG or BACK_FLAG

    @Test
    fun `holding Select and Start for one second opens the menu`() {
        pad.report(chord)

        rig.advanceBy(999)
        assertThat(rig.chords).isEqualTo(0)
        rig.advanceBy(1)
        assertThat(rig.chords).isEqualTo(1)
    }

    @Test
    fun `Select and Start never reach the PC while the chord is held, or after it opened the menu`() {
        pad.report(chord)
        rig.advanceBy(1_000)
        val packets = rig.connection.packets.size

        pad.report(PLAY_FLAG)
        pad.report(0)

        assertThat(rig.buttons().filter { it != 0 }).isEmpty()
        assertThat(rig.connection.packets).hasSize(packets)
    }

    @Test
    fun `releasing a button before one second does not open the menu`() {
        pad.report(chord)
        rig.advanceBy(900)
        pad.report(PLAY_FLAG)

        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a quick Start and Select reaches the PC once, as a short press on release`() {
        pad.report(chord)
        rig.advanceBy(300)
        assertThat(rig.buttons().none { it and chord != 0 }).isTrue()

        pad.report(0)
        rig.advanceBy(100)

        // The first packet only tells the PC that the pad is neutral while the chord is held back.
        assertThat(rig.buttons()).containsExactly(0, chord, 0).inOrder()
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `a third button joining before the chord completes sends what the user pressed`() {
        pad.report(chord)
        rig.advanceBy(500)

        pad.report(chord or A_FLAG)

        assertThat(rig.lastButtons()).isEqualTo(chord or A_FLAG)
        rig.advanceBy(5_000)
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `Start alone reaches the PC at once, and so does Select alone`() {
        pad.report(PLAY_FLAG)
        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG)
        pad.report(0)

        pad.report(BACK_FLAG)
        assertThat(rig.lastButtons()).isEqualTo(BACK_FLAG)
        pad.report(0)
        assertThat(rig.lastButtons()).isEqualTo(0)
    }

    @Test
    fun `Start alone is in the packet of the report that carried it, with no wait for Select`() {
        pad.report(PLAY_FLAG)

        assertThat(rig.buttons()).containsExactly(PLAY_FLAG)
    }

    @Test
    fun `Start with another button reaches the PC at once`() {
        pad.report(PLAY_FLAG or A_FLAG)

        assertThat(rig.lastButtons()).isEqualTo(PLAY_FLAG or A_FLAG)
    }

    @Test
    fun `a third button cancels the chord`() {
        pad.report(chord)
        rig.advanceBy(500)
        pad.report(chord or LB_FLAG)

        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `Start, Select and both bumpers, then letting go, end the stream`() {
        pad.report(PLAY_FLAG or BACK_FLAG or LB_FLAG or RB_FLAG)
        rig.advanceBy(200)
        assertThat(rig.activity.isFinishing).isFalse()

        pad.report(0)
        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isTrue()
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `the quit combo ends the stream when the pad lets go in steps`() {
        pad.report(PLAY_FLAG or BACK_FLAG or LB_FLAG or RB_FLAG)
        pad.report(PLAY_FLAG or BACK_FLAG)
        pad.report(PLAY_FLAG)
        rig.advanceBy(0)
        assertThat(rig.activity.isFinishing).isFalse()

        pad.report(0)
        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isTrue()
    }

    @Test
    fun `the menu chord alone does not end the stream`() {
        pad.report(chord)
        rig.advanceBy(1_500)
        pad.report(0)
        rig.advanceBy(0)

        assertThat(rig.chords).isEqualTo(1)
        assertThat(rig.activity.isFinishing).isFalse()
    }

    @Test
    fun `three of the four buttons do not end the stream`() {
        pad.report(PLAY_FLAG or BACK_FLAG or LB_FLAG)
        pad.report(0)
        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isFalse()
    }

    @Test
    fun `the quit combo ends the stream from the driver thread, on the main thread`() {
        var failure: Throwable? = null
        Thread {
            try {
                pad.report(PLAY_FLAG or BACK_FLAG or LB_FLAG or RB_FLAG)
                pad.report(0)
            } catch (e: Throwable) {
                failure = e
            }
        }
            .apply {
                start()
                join()
            }
        assertThat(failure).isNull()
        assertThat(rig.activity.isFinishing).isFalse()

        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isTrue()
    }

    private val quitCombo = PLAY_FLAG or BACK_FLAG or LB_FLAG or RB_FLAG

    @Test
    fun `a combo begun before the chrome took the input does not end the stream when let go`() {
        pad.report(quitCombo)

        rig.handler.setCaptured(true)
        pad.report(0)
        rig.handler.setCaptured(false)
        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isFalse()
    }

    @Test
    fun `a combo pressed while the chrome has the input does not end the stream`() {
        rig.handler.setCaptured(true)
        pad.report(quitCombo)
        rig.handler.setCaptured(false)
        pad.report(quitCombo)

        pad.report(0)
        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isFalse()
    }

    @Test
    fun `letting go of Start first, after a long hold, still ends the stream and leaves the mouse off`() {
        pad.report(quitCombo)
        rig.advanceBy(StartHold.HOLD_MS + 500)
        pad.report(BACK_FLAG or LB_FLAG or RB_FLAG)
        pad.report(0)
        rig.advanceBy(0)

        assertThat(rig.activity.isFinishing).isTrue()
        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `letting the quit combo go down to Select and Start, then one of them, sends no tap`() {
        pad.report(PLAY_FLAG)
        pad.report(chord)
        pad.report(chord or LB_FLAG)
        pad.report(quitCombo)
        pad.report(chord or RB_FLAG)
        pad.report(chord)

        // Select and Start reach the PC as they are held, not masked as a pending chord
        assertThat(rig.lastButtons()).isEqualTo(chord)
        rig.advanceBy(200)
        pad.report(BACK_FLAG)
        rig.advanceBy(100)

        assertThat(rig.buttons().dropWhile { it != chord }.drop(1)).containsExactly(BACK_FLAG)
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `Select and Start left held after the quit combo do not open the menu`() {
        pad.report(quitCombo)
        pad.report(chord or LB_FLAG)
        pad.report(chord)

        rig.advanceBy(StartHold.HOLD_MS + 500)

        assertThat(rig.chords).isEqualTo(0)
        assertThat(rig.lastButtons()).isEqualTo(chord)
    }

    @Test
    fun `Select and Start arm the chord again after the quit combo was let go entirely`() {
        pad.report(quitCombo)
        pad.report(chord)
        pad.report(0)
        rig.advanceBy(0)

        pad.report(chord)
        rig.advanceBy(1_000)

        assertThat(rig.chords).isEqualTo(1)
    }

    @Test
    fun `a button held before the chrome took the input does not keep Select and Start from arming`() {
        pad.report(LB_FLAG)
        rig.handler.setCaptured(true)
        pad.report(0)
        rig.handler.setCaptured(false)

        pad.report(chord)
        rig.advanceBy(1_000)

        assertThat(rig.chords).isEqualTo(1)
    }
}
