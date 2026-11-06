package io.github.magalz.borealis.input.feedback

import android.os.Handler
import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.testing.contextField
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A battery poll that the handler's thread has already taken off its queue cannot be cancelled when
 * its pad leaves or the handler stops: it runs anyway, and queues its next turn. It must find that
 * its pad is gone and do nothing: send nothing to the host, change nothing for the controller
 * number (which another pad may hold by then, or none), and not come back.
 *
 * The thread takes the poll between the check of the queue and the end of the leave, a window of
 * microseconds in the 120 seconds of a turn, so these tests run the poll by hand on the handler's
 * thread at the point where that thread would.
 */
@RunWith(RobolectricTestRunner::class)
class StrayBatteryPollTest : FeedbackTestBase() {
    private fun battery(present: Boolean = true) = Hardware.battery(present, DISCHARGING, 0.5f)

    /**
     * Connects a pad and lets it send its first input. The handler's thread waits until the press
     * is done: its first poll reads the handler's list of pads, which the press is still reading.
     */
    private fun padWith(rig: FeedbackRig, battery: Any, id: Int): Pad {
        val pad = rig.connectPad(id = id, features = DeviceFeatures(batteryState = battery))
        val letThreadGo = rig.holdBackground()
        rig.press(pad, KeyEvent.KEYCODE_BUTTON_A)
        letThreadGo()
        rig.awaitBackground()
        rig.release(pad, KeyEvent.KEYCODE_BUTTON_A)
        return pad
    }

    /** The poll of [pad], the runnable that the handler's thread runs every 120 seconds. */
    private fun pollOf(rig: FeedbackRig, pad: Pad): Runnable =
        rig.handler.contextField(pad.id, "batteryStateUpdateRunnable") as Runnable

    /** Runs [poll] on the handler's thread, behind what the handler posted before. */
    private fun runOnBackground(rig: FeedbackRig, poll: Runnable) {
        Handler(rig.backgroundLooper).post(poll)
        rig.awaitBackground()
    }

    private fun nextTurn(rig: FeedbackRig) {
        rig.advanceBackgroundBy(POLL_MS)
        rig.awaitBackground()
    }

    private fun events() = batteryEvents.toList()

    @Test
    fun `a poll that the thread had taken when its pad left does not come back`() {
        val rig = rig { multiController = true }
        val first = padWith(rig, battery(present = false), id = 5)
        val poll = pollOf(rig, first)
        val taken = CountDownLatch(1)
        val go = CountDownLatch(1)
        Handler(rig.backgroundLooper).post {
            taken.countDown()
            check(go.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "The poll was never let go" }
            poll.run()
        }
        check(taken.await(WAIT_SECONDS, TimeUnit.SECONDS)) { "The thread did not take the poll" }

        // The leave cannot cancel what the thread holds, and frees the number
        rig.handler.onInputDeviceRemoved(first.id)
        go.countDown()
        rig.awaitBackground()
        nextTurn(rig)

        assertThat(events()).containsExactly(NO_BATTERY)
        padWith(rig, battery(present = false), id = 6)
        assertThat(events()).containsExactly(NO_BATTERY, NO_BATTERY)
    }

    @Test
    fun `a poll that runs after its pad left sends nothing and leaves its number to the next pad`() {
        val rig = rig { multiController = true }
        val first = padWith(rig, battery(present = false), id = 5)
        val poll = pollOf(rig, first)

        // The leave frees the number and queues the start over of its state for the thread
        rig.handler.onInputDeviceRemoved(first.id)
        runOnBackground(rig, poll)

        assertThat(events()).containsExactly(NO_BATTERY)
        padWith(rig, battery(present = false), id = 6)
        nextTurn(rig)
        assertThat(events()).containsExactly(NO_BATTERY, NO_BATTERY)
    }

    @Test
    fun `a poll that runs after stop sends nothing and does not come back`() {
        val rig = rig()
        val battery = battery()
        val pad = padWith(rig, battery, id = FeedbackRig.PAD_ID)
        val poll = pollOf(rig, pad)

        rig.handler.stop()
        // A poll that ran now would report the new capacity
        Hardware.batteryState(battery).batteryCapacity = 0.25f
        runOnBackground(rig, poll)
        nextTurn(rig)

        assertThat(events()).containsExactly(HALF_FULL)
    }

    @Test
    fun `a poll that runs after destroy sends nothing`() {
        val rig = rig()
        val battery = battery()
        val pad = padWith(rig, battery, id = FeedbackRig.PAD_ID)
        val poll = pollOf(rig, pad)

        rig.destroy()
        Hardware.batteryState(battery).batteryCapacity = 0.25f
        // The handler's thread is gone: this one runs the poll, as the thread would have
        poll.run()

        assertThat(events()).containsExactly(HALF_FULL)
    }

    private companion object {
        const val POLL_MS = 120_000L
        const val WAIT_SECONDS = 10L
        const val DISCHARGING = android.hardware.BatteryState.STATUS_DISCHARGING
        val NO_BATTERY = BatteryEvent(0, MoonBridge.LI_BATTERY_STATE_NOT_PRESENT.toInt(), -1)
        val HALF_FULL = BatteryEvent(0, MoonBridge.LI_BATTERY_STATE_DISCHARGING.toInt(), 50)
    }
}
