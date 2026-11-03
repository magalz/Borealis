package io.github.magalz.borealis.input

import android.os.Looper
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.driver.AbstractController
import com.limelight.binding.input.driver.UsbDriverListener
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowSystemClock

/**
 * Stopping the handler ends all the work it scheduled, for every context, and zeroes the motors of
 * the USB pads without waiting on a pad that does not answer: the stop runs on the main thread.
 */
@RunWith(RobolectricTestRunner::class)
class HandlerStopTest {
    @get:Rule val rig = ControllerHandlerRig()

    @Test
    fun `stopping cancels the chord check of the default context`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(50)
        rig.press(KEYCODE_BUTTON_SELECT)

        rig.handler.stop()
        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `stopping cancels the mouse emulation a Start hold turned on for the default context`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(900)
        rig.release(KEYCODE_BUTTON_START)

        rig.handler.stop()

        assertThat(shadowOf(Looper.getMainLooper()).nextScheduledTaskTime).isEqualTo(Duration.ZERO)
    }

    @Test
    fun `a pad that answers gets the motors and then the trigger motors zeroed`() {
        val pad = ZeroingPad(1, rig.handler)
        rig.handler.deviceAdded(pad)

        rig.handler.stop()

        assertThat(pad.writes).containsExactly("motors 0 0", "triggers 0 0").inOrder()
    }

    @Test
    fun `a pad that does not answer the motors is not sent the trigger motors`() {
        val pad = ZeroingPad(1, rig.handler, answers = false)
        rig.handler.deviceAdded(pad)

        rig.handler.stop()

        assertThat(pad.writes).containsExactly("motors 0 0")
    }

    @Test
    fun `a pad that does not answer leaves the pads after it zeroed`() {
        val stuck = ZeroingPad(1, rig.handler, answers = false)
        val answering = ZeroingPad(2, rig.handler)
        rig.handler.deviceAdded(stuck)
        rig.handler.deviceAdded(answering)

        rig.handler.stop()

        assertThat(stuck.writes).containsExactly("motors 0 0")
        assertThat(answering.writes).containsExactly("motors 0 0", "triggers 0 0").inOrder()
    }
}

/**
 * A pad the USB driver holds, with the writes it got. A pad that does not [answer] holds each write
 * for the whole bulk timeout of the drivers, on the clock of the test.
 */
private class ZeroingPad(
    id: Int,
    listener: UsbDriverListener,
    private val answers: Boolean = true,
) : AbstractController(id, listener, 0x045e, 0x028e) {
    val writes = mutableListOf<String>()

    override fun start() = true

    override fun stop() {
        // Nothing is held open.
    }

    override fun rumble(lowFreqMotor: Short, highFreqMotor: Short) {
        writes += "motors $lowFreqMotor $highFreqMotor"
        waitForTheTimeout()
    }

    override fun rumbleTriggers(leftTrigger: Short, rightTrigger: Short) {
        writes += "triggers $leftTrigger $rightTrigger"
        waitForTheTimeout()
    }

    private fun waitForTheTimeout() {
        if (!answers) {
            ShadowSystemClock.advanceBy(Duration.ofMillis(BULK_TIMEOUT_MS))
        }
    }

    companion object {
        /** The drivers' bulk write timeout. */
        const val BULK_TIMEOUT_MS = 100L
    }
}
