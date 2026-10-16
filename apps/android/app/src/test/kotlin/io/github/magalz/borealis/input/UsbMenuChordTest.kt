package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
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
    fun `the PC is told that Select and Start are up and the release sends nothing more`() {
        pad.report(chord)
        rig.advanceBy(1_000)
        assertThat(rig.lastButtons()).isEqualTo(0)
        val packets = rig.connection.packets.size

        pad.report(PLAY_FLAG)
        pad.report(0)

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
    fun `a third button cancels the chord`() {
        pad.report(chord)
        rig.advanceBy(500)
        pad.report(chord or com.limelight.nvstream.input.ControllerPacket.LB_FLAG)

        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `Start and Select are sent as they are before the chord completes`() {
        pad.report(chord)

        assertThat(rig.lastButtons()).isEqualTo(chord)
    }
}
