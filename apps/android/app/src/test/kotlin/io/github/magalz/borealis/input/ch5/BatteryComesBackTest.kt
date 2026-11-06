package io.github.magalz.borealis.input.ch5

import android.hardware.BatteryState
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.input.feedback.BatteryEvent
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.input.feedback.batteryEvents
import org.junit.Test

/**
 * A pad whose battery goes away is told to the host once as not present, and what the host holds is
 * then the number's state: a battery that comes back is a change, whatever status and capacity it
 * had before it went, and a battery that goes away again is told again.
 */
class BatteryComesBackTest : FeedbackCharacterization() {
    private val battery =
        Hardware.battery(present = true, status = BatteryState.STATUS_DISCHARGING, capacity = 0.5f)

    private fun poll(rig: FeedbackRig) {
        rig.advanceBackgroundBy(POLL_MS)
        rig.awaitBackground()
    }

    private fun batteryRig(): FeedbackRig {
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(batteryState = battery)))
        rig.awaitBackground()
        return rig
    }

    private fun present(present: Boolean) {
        Hardware.batteryState(battery).present = present
    }

    @Test
    fun `a battery that comes back with the status and capacity it had is reported again`() {
        val rig = batteryRig()

        present(false)
        poll(rig)
        present(true)
        poll(rig)

        assertThat(batteryEvents).containsExactly(HALF, NOT_PRESENT, HALF).inOrder()
    }

    @Test
    fun `a battery that comes back and goes away again is told as not present again`() {
        val rig = batteryRig()

        present(false)
        poll(rig)
        present(true)
        poll(rig)
        present(false)
        poll(rig)

        assertThat(batteryEvents).containsExactly(HALF, NOT_PRESENT, HALF, NOT_PRESENT).inOrder()
    }

    private companion object {
        const val POLL_MS = 120_000L
        val NOT_PRESENT = BatteryEvent(0, MoonBridge.LI_BATTERY_STATE_NOT_PRESENT.toInt(), -1)
        val HALF = BatteryEvent(0, MoonBridge.LI_BATTERY_STATE_DISCHARGING.toInt(), 50)
    }
}
