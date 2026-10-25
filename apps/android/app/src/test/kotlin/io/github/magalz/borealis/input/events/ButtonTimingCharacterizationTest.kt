package io.github.magalz.borealis.input.events

import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_L1
import android.view.KeyEvent.KEYCODE_BUTTON_R1
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import android.view.KeyEvent.KEYCODE_MENU
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.nvstream.input.ControllerPacket.A_FLAG
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.LB_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import com.limelight.nvstream.input.ControllerPacket.RB_FLAG
import com.limelight.nvstream.input.ControllerPacket.SPECIAL_BUTTON_FLAG
import com.limelight.nvstream.input.ControllerPacket.TOUCHPAD_FLAG
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The times `handleButtonUp` and `handleButtonDown` read from key events: the 25 ms a key is held
 * before the release goes out, the Start hold that turns the gamepad mouse on, the 100 ms in which a
 * released bumper still counts for Start, the 50 ms Guide tap, and the quit combo.
 */
@RunWith(RobolectricTestRunner::class)
class ButtonTimingCharacterizationTest {
    private val rig = EventsRig()
    private val pad = rig.addPad(7)

    // ----- The wait before a short press goes up -----

    /** Runs [block] with the error stream captured; answers what was written to it. */
    private fun errorOutput(block: () -> Unit): String {
        val original = System.err
        val captured = ByteArrayOutputStream()
        System.setErr(PrintStream(captured, true))
        try {
            block()
        } finally {
            System.setErr(original)
        }
        return captured.toString()
    }

    /**
     * Lets go of A as if it had been held for [heldMs] and answers whether the handler tried to
     * wait, which an interrupted thread shows: the wait throws and the handler prints the error.
     */
    private fun waited(heldMs: Long): Boolean {
        Thread.currentThread().interrupt()
        try {
            val output = errorOutput { pad.release(KEYCODE_BUTTON_A, at = rig.now, downTime = rig.now - heldMs) }
            return "InterruptedException" in output
        } finally {
            // The handler puts the interrupt flag back, so this test must clear it.
            assertThat(Thread.interrupted()).isTrue()
        }
    }

    /** How long the handler took to let go of A held for [heldMs], in real milliseconds. */
    private fun releaseTime(heldMs: Long): Double {
        val start = System.nanoTime()
        pad.release(KEYCODE_BUTTON_A, at = rig.now, downTime = rig.now - heldMs)
        return (System.nanoTime() - start) / 1_000_000.0
    }

    private fun median(heldMs: Long): Double = List(5) { releaseTime(heldMs) }.sorted()[2]

    @Test
    fun `a key let go at once makes the handler wait 25 ms before the release goes out`() {
        releaseTime(100) // warm up the code the measure runs

        val instant = median(0)
        val nearly = median(24)
        val long = median(100)

        assertThat(instant).isAtLeast(22.0)
        // Held for 24 ms, only the missing millisecond is waited, and a long hold waits for nothing.
        assertThat(instant - nearly).isAtLeast(12.0)
        assertThat(instant - long).isAtLeast(12.0)
    }

    @Test
    fun `a key held for less than 25 ms is waited for, and one held for 25 ms is not`() {
        assertThat(waited(0)).isTrue()
        assertThat(waited(10)).isTrue()
        assertThat(waited(24)).isTrue()
        assertThat(waited(25)).isFalse()
        assertThat(waited(26)).isFalse()
        assertThat(waited(10_000)).isFalse()
    }

    @Test
    fun `a key that comes up before it went down is waited for`() {
        assertThat(waited(-5)).isTrue()
    }

    @Test
    fun `an interrupted wait still sends the release and leaves the thread interrupted`() {
        pad.press(KEYCODE_BUTTON_A)

        Thread.currentThread().interrupt()
        val handled: Boolean
        try {
            handled = pad.release(KEYCODE_BUTTON_A, at = rig.now, downTime = rig.now - 5)
            assertThat(Thread.currentThread().isInterrupted).isTrue()
        } finally {
            Thread.interrupted()
        }

        assertThat(handled).isTrue()
        assertThat(rig.buttons()).containsExactly(A_FLAG, 0).inOrder()
    }

    @Test
    fun `the held time is cut to an int, so one that is a multiple of 2 to the 32 plus 10 ms looks like 10 ms`() {
        assertThat(waited((1L shl 32) + 10)).isTrue()
        assertThat(waited((1L shl 32) + 30)).isFalse()
        assertThat(waited((1L shl 31) - 1)).isFalse()
    }

    @Test
    fun `a held time past what an int holds wraps to a negative one and the wait length overflows to a negative sleep`() {
        for (heldMs in listOf(1L shl 31, (1L shl 31) + 5)) {
            val error =
                assertThrows(IllegalArgumentException::class.java) {
                    pad.release(KEYCODE_BUTTON_A, at = rig.now, downTime = rig.now - heldMs)
                }
            assertWithMessage("held $heldMs").that(error).hasMessageThat().contains("negative")
        }
    }

    // ----- Start held for the gamepad mouse -----

    private fun holdStartFor(heldMs: Long, keyCode: Int = KEYCODE_BUTTON_START): Boolean {
        pad.press(keyCode)
        rig.advanceBy(heldMs)
        pad.release(keyCode)
        return pad.getBoolean("mouseEmulationActive")
    }

    @Test
    fun `Start held for more than 750 ms turns the gamepad mouse on when it goes up`() {
        assertThat(holdStartFor(751)).isTrue()
    }

    @Test
    fun `Start held for exactly 750 ms does not`() {
        assertThat(holdStartFor(750)).isFalse()
    }

    @Test
    fun `Menu held that long is Start and turns the mouse on too`() {
        assertThat(holdStartFor(1_000, KEYCODE_MENU)).isTrue()
    }

    @Test
    fun `the mouse comes on when Start goes up, not while it is held`() {
        pad.press(KEYCODE_BUTTON_START)
        rig.advanceBy(2_000)

        assertThat(pad.getBoolean("mouseEmulationActive")).isFalse()
    }

    @Test
    fun `Start held again turns the mouse off`() {
        assertThat(holdStartFor(800)).isTrue()
        rig.advanceBy(200)

        assertThat(holdStartFor(800)).isFalse()
    }

    @Test
    fun `the Start hold counts from the first key down, not from its repeats`() {
        pad.press(KEYCODE_BUTTON_START)
        rig.advanceBy(600)
        pad.press(KEYCODE_BUTTON_START, repeat = 1)
        rig.advanceBy(200)
        pad.release(KEYCODE_BUTTON_START)

        assertThat(pad.getBoolean("mouseEmulationActive")).isTrue()
    }

    @Test
    fun `a new key down starts the Start hold again`() {
        pad.press(KEYCODE_BUTTON_START)
        rig.advanceBy(60)
        pad.release(KEYCODE_BUTTON_START)
        rig.advanceBy(1_000)

        assertThat(holdStartFor(500)).isFalse()
    }

    @Test
    fun `a Start key up while Start is not held, as a pad that disconnects sends, does nothing`() {
        pad.set("startDownTime", 0L)

        pad.release(KEYCODE_BUTTON_START, downTime = rig.now - 5_000)

        assertThat(pad.getBoolean("mouseEmulationActive")).isFalse()
    }

    @Test
    fun `the Start hold does nothing when the mouse emulation setting is off`() {
        val off = EventsRig { mouseEmulation = false }
        val offPad = off.addPad(7)

        offPad.press(KEYCODE_BUTTON_START)
        off.advanceBy(1_000)
        offPad.release(KEYCODE_BUTTON_START)

        assertThat(offPad.getBoolean("mouseEmulationActive")).isFalse()
    }

    // ----- Start and a bumper -----

    @Test
    fun `Start with the left bumper is Select on a pad without a Select button, and it is one button`() {
        pad.press(KEYCODE_BUTTON_L1)
        pad.press(KEYCODE_BUTTON_START)

        assertThat(rig.buttons()).containsExactly(LB_FLAG, BACK_FLAG).inOrder()
        assertThat(pad.inputMap).isEqualTo(BACK_FLAG)
    }

    @Test
    fun `that Select ends with Start, and Start and the bumper do not come back`() {
        pad.press(KEYCODE_BUTTON_L1)
        pad.press(KEYCODE_BUTTON_START)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_START)

        assertThat(rig.buttons()).containsExactly(LB_FLAG, BACK_FLAG, 0).inOrder()
        assertThat(pad.getInt("emulatingButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `that Select also ends when the bumper is let go`() {
        pad.press(KEYCODE_BUTTON_START)
        pad.press(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)

        // The bumper went down second, so Start is what is left of inputMap; the emulation needs
        // PLAY and LB both in inputMap at the moment of the second press, which is the case here.
        assertThat(rig.buttons()).containsExactly(PLAY_FLAG, BACK_FLAG).inOrder()
        pad.release(KEYCODE_BUTTON_L1)
        assertThat(rig.buttons().last()).isEqualTo(0)
    }

    @Test
    fun `any key up while Select is emulated ends it, though Start and the bumper are still held`() {
        pad.press(KEYCODE_BUTTON_L1)
        pad.press(KEYCODE_BUTTON_START)
        pad.press(KEYCODE_BUTTON_A)
        rig.advanceBy(100)
        assertThat(rig.buttons().last()).isEqualTo(BACK_FLAG or A_FLAG)

        // Start and the left bumper were taken out of the buttons when they became Select, so
        // neither is there for the check that a release makes, and Select goes up with A.
        pad.release(KEYCODE_BUTTON_A)

        assertThat(rig.buttons().last()).isEqualTo(0)
        assertThat(pad.getInt("emulatingButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `Start within 100 ms of the left bumper going up is Select`() {
        pad.press(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)

        pad.press(KEYCODE_BUTTON_START)

        assertThat(rig.buttons().last()).isEqualTo(BACK_FLAG)
        assertThat(pad.getInt("emulatingButtonFlags")).isEqualTo(2)
    }

    @Test
    fun `Start 101 ms after the left bumper went up is Start`() {
        pad.press(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_L1)
        rig.advanceBy(101)

        pad.press(KEYCODE_BUTTON_START)

        assertThat(rig.buttons().last()).isEqualTo(PLAY_FLAG)
        assertThat(pad.getInt("emulatingButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `Start right after the right bumper is the Guide button and not Select`() {
        pad.press(KEYCODE_BUTTON_R1)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_R1)

        pad.press(KEYCODE_BUTTON_START)

        assertThat(rig.buttons().last()).isEqualTo(SPECIAL_BUTTON_FLAG)
    }

    @Test
    fun `Start within 100 ms of the right bumper going up is the Guide button, and 101 ms is Start`() {
        pad.press(KEYCODE_BUTTON_R1)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_R1)
        rig.advanceBy(100)
        pad.press(KEYCODE_BUTTON_START)
        assertThat(rig.buttons().last()).isEqualTo(SPECIAL_BUTTON_FLAG)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_START)
        rig.advanceBy(100)

        pad.press(KEYCODE_BUTTON_R1)
        rig.advanceBy(100)
        pad.release(KEYCODE_BUTTON_R1)
        rig.advanceBy(101)
        pad.press(KEYCODE_BUTTON_START)

        assertThat(rig.buttons().last()).isEqualTo(PLAY_FLAG)
    }

    @Test
    fun `a pad that has a Select button sends Start and the left bumper as they are`() {
        val withSelect = rig.addPad(8, fields = mapOf("hasSelect" to true))

        withSelect.press(KEYCODE_BUTTON_L1)
        withSelect.press(KEYCODE_BUTTON_START)
        rig.advanceBy(100)

        assertThat(rig.buttons()).containsExactly(LB_FLAG, LB_FLAG or PLAY_FLAG).inOrder()
    }

    // ----- Select and the bumper as the clickpad -----

    @Test
    fun `Select with the left bumper is the touchpad button on a pad that stands in for a PS4 pad`() {
        val clickpad = rig.addPad(8, fields = mapOf("hasSelect" to true, "needsClickpadEmulation" to true))

        clickpad.press(KEYCODE_BUTTON_L1)
        clickpad.press(KEYCODE_BUTTON_SELECT)

        assertThat(rig.buttons()).containsExactly(LB_FLAG, TOUCHPAD_FLAG).inOrder()
        assertThat(clickpad.getInt("emulatingButtonFlags")).isEqualTo(4)
    }

    @Test
    fun `that touchpad button ends when the bumper is let go`() {
        val clickpad = rig.addPad(8, fields = mapOf("hasSelect" to true, "needsClickpadEmulation" to true))
        clickpad.press(KEYCODE_BUTTON_L1)
        clickpad.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)

        clickpad.release(KEYCODE_BUTTON_L1)

        assertThat(rig.buttons().last()).isEqualTo(0)
        assertThat(clickpad.getInt("emulatingButtonFlags")).isEqualTo(0)
    }

    @Test
    fun `Select within 100 ms of the left bumper going up is the touchpad button, and 101 ms is Select`() {
        val clickpad = rig.addPad(8, fields = mapOf("hasSelect" to true, "needsClickpadEmulation" to true))
        clickpad.press(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)
        clickpad.release(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)
        clickpad.press(KEYCODE_BUTTON_SELECT)
        assertThat(rig.buttons().last()).isEqualTo(TOUCHPAD_FLAG)
        rig.advanceBy(100)
        clickpad.release(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)

        clickpad.press(KEYCODE_BUTTON_L1)
        rig.advanceBy(100)
        clickpad.release(KEYCODE_BUTTON_L1)
        rig.advanceBy(101)
        clickpad.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)

        assertThat(rig.buttons().last()).isEqualTo(BACK_FLAG)
    }

    @Test
    fun `a pad with a Select button that is not standing in for a PS4 pad sends Select and the bumper as they are`() {
        val plain = rig.addPad(8, fields = mapOf("hasSelect" to true))
        plain.press(KEYCODE_BUTTON_L1)
        plain.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)

        assertThat(rig.buttons()).containsExactly(LB_FLAG, LB_FLAG or BACK_FLAG).inOrder()
    }

    // ----- The Guide tap of the menu chord -----

    private fun startSelectChord(target: Pad) {
        target.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(30)
        target.press(KEYCODE_BUTTON_START)
        rig.advanceBy(100)
    }

    @Test
    fun `Start and Select let go before the chord are a Guide tap that lasts 50 ms`() {
        val chordPad = rig.addPad(8, fields = mapOf("hasSelect" to true))
        startSelectChord(chordPad)
        val before = rig.packets.size

        chordPad.release(KEYCODE_BUTTON_SELECT)
        val tapped = rig.packets.drop(before).map { it.buttons }
        rig.advanceBy(49)
        val stillTapping = rig.packets.drop(before).map { it.buttons }
        rig.advanceBy(1)
        val ended = rig.packets.drop(before).map { it.buttons }

        assertThat(tapped).containsExactly(SPECIAL_BUTTON_FLAG)
        assertThat(stillTapping).containsExactly(SPECIAL_BUTTON_FLAG)
        assertThat(ended).containsExactly(SPECIAL_BUTTON_FLAG, 0).inOrder()
        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `on a pad with a Mode button the early chord is a short press of Start and Select`() {
        val chordPad = rig.addPad(8, fields = mapOf("hasSelect" to true, "hasMode" to true))
        startSelectChord(chordPad)
        val before = rig.packets.size

        chordPad.release(KEYCODE_BUTTON_START)
        rig.advanceBy(49)
        val tapping = rig.packets.drop(before).map { it.buttons }
        rig.advanceBy(1)
        val ended = rig.packets.drop(before).map { it.buttons }

        assertThat(tapping).containsExactly(PLAY_FLAG or BACK_FLAG)
        assertThat(ended).containsExactly(PLAY_FLAG or BACK_FLAG, 0).inOrder()
    }

    // ----- The quit combo -----

    private fun pressAllFour(target: Pad) {
        target.press(KEYCODE_BUTTON_L1)
        target.press(KEYCODE_BUTTON_R1)
        target.press(KEYCODE_BUTTON_START)
        target.press(KEYCODE_BUTTON_SELECT)
        rig.advanceBy(100)
    }

    @Test
    fun `Start, Select and both bumpers held finish the game when the last of them is let go`() {
        pressAllFour(pad)

        assertThat(pad.getBoolean("pendingExit")).isTrue()
        assertThat(rig.activity.isFinishing).isFalse()
        for (keyCode in listOf(KEYCODE_BUTTON_START, KEYCODE_BUTTON_L1, KEYCODE_BUTTON_SELECT)) {
            pad.release(keyCode)
            assertWithMessage("after $keyCode").that(rig.activity.isFinishing).isFalse()
        }

        pad.release(KEYCODE_BUTTON_R1)

        assertThat(rig.activity.isFinishing).isTrue()
    }

    @Test
    fun `the combo does not arm with any other button held, and nothing finishes the game`() {
        pad.press(KEYCODE_BUTTON_A)
        pressAllFour(pad)

        assertThat(pad.getBoolean("pendingExit")).isFalse()
        for (keyCode in listOf(KEYCODE_BUTTON_START, KEYCODE_BUTTON_L1, KEYCODE_BUTTON_SELECT, KEYCODE_BUTTON_R1, KEYCODE_BUTTON_A)) {
            pad.release(keyCode)
        }

        assertThat(rig.activity.isFinishing).isFalse()
    }

    @Test
    fun `the combo needs a real Select, which a pad without one has no way to press`() {
        // With Start, the left bumper and the right bumper, Start+LB becomes Select, so the four
        // buttons of the combo are never all in the packet.
        pad.press(KEYCODE_BUTTON_L1)
        pad.press(KEYCODE_BUTTON_R1)
        pad.press(KEYCODE_BUTTON_START)
        rig.advanceBy(100)

        assertThat(pad.getBoolean("pendingExit")).isFalse()
    }

    @Test
    fun `pressing a button after the combo is armed does not disarm it`() {
        pressAllFour(pad)
        pad.press(KEYCODE_BUTTON_A)
        rig.advanceBy(100)
        for (keyCode in listOf(KEYCODE_BUTTON_START, KEYCODE_BUTTON_L1, KEYCODE_BUTTON_SELECT, KEYCODE_BUTTON_R1)) {
            pad.release(keyCode)
        }
        assertThat(rig.activity.isFinishing).isFalse()

        pad.release(KEYCODE_BUTTON_A)

        assertThat(rig.activity.isFinishing).isTrue()
    }
}
