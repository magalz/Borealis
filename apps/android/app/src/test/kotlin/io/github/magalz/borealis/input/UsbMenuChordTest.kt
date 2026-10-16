package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A pad on the USB driver opens the menu with the same chord. */
@RunWith(RobolectricTestRunner::class)
class UsbMenuChordTest {
    private val rig = ControllerHandlerRig()
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

        assertThat(rig.buttons()).containsExactly(chord, 0).inOrder()
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
}
