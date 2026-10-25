package io.github.magalz.borealis.input.feedback

import android.hardware.BatteryState
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The battery of a pad, as the handler's own thread reports it to the host from Android 12: once
 * when the pad arrives, then every 120 seconds, and only when the status or the capacity changed
 * since the last report. The status becomes the host's battery state, and the capacity a signed byte
 * of its percentage, truncated; 255 means unknown.
 */
@RunWith(RobolectricTestRunner::class)
class BatteryReportTest : FeedbackTestBase() {
    private fun battery(
        status: Int = BatteryState.STATUS_DISCHARGING,
        capacity: Float = 0.5f,
        present: Boolean = true,
    ) = Hardware.battery(present, status, capacity)

    private fun padWith(rig: FeedbackRig, battery: Any, id: Int = FeedbackRig.PAD_ID): Pad {
        val pad = rig.connectPad(id = id, features = DeviceFeatures(batteryState = battery))
        rig.wake(pad)
        rig.awaitBackground()
        return pad
    }

    private fun events() = ShadowFeedbackBridge.batteryEvents.toList()

    private fun poll(rig: FeedbackRig, millis: Long = POLL_MS) {
        rig.advanceBackgroundBy(millis)
        rig.awaitBackground()
    }

    @Test
    fun `the first report comes when the pad arrives`() {
        val rig = rig()

        padWith(rig, battery())

        assertThat(events())
            .containsExactly(BatteryEvent(0, MoonBridge.LI_BATTERY_STATE_DISCHARGING.toInt(), 50))
    }

    @Test
    fun `each status becomes its own battery state`() {
        val expected =
            listOf(
                BatteryState.STATUS_UNKNOWN to MoonBridge.LI_BATTERY_STATE_UNKNOWN,
                BatteryState.STATUS_CHARGING to MoonBridge.LI_BATTERY_STATE_CHARGING,
                BatteryState.STATUS_DISCHARGING to MoonBridge.LI_BATTERY_STATE_DISCHARGING,
                BatteryState.STATUS_NOT_CHARGING to MoonBridge.LI_BATTERY_STATE_NOT_CHARGING,
                BatteryState.STATUS_FULL to MoonBridge.LI_BATTERY_STATE_FULL,
            )

        val states =
            expected.map { (status, _) ->
                val rig = rig()
                padWith(rig, battery(status = status))
                events().single().state
            }

        assertThat(states).containsExactlyElementsIn(expected.map { it.second.toInt() }).inOrder()
        assertThat(states).containsExactly(0, 3, 2, 4, 5).inOrder()
    }

    @Test
    fun `the percentage is the capacity times a hundred, truncated`() {
        val capacities = listOf(0f, 1f, 0.555f, 0.995f, 0.5f, 0.07f, 0.9999f, 0.53f, 0.59f)

        val percentages =
            capacities.map { capacity ->
                val rig = rig()
                padWith(rig, battery(capacity = capacity))
                events().single().percentage
            }

        // 0.53f and 0.59f times 100 come out a hair under 53 and 59, and the cast truncates them
        assertThat(percentages).containsExactly(0, 100, 55, 99, 50, 7, 99, 52, 58).inOrder()
    }

    @Test
    fun `an unknown capacity is reported as 255`() {
        val rig = rig()

        padWith(rig, battery(capacity = Float.NaN))

        assertThat(events().single().percentage).isEqualTo(MoonBridge.LI_BATTERY_PERCENTAGE_UNKNOWN.toInt())
        assertThat(events().single().percentage).isEqualTo(-1)
    }

    @Test
    fun `a capacity out of range wraps in the byte`() {
        val high = rig()
        padWith(high, battery(capacity = 1.5f))
        val high150 = events().single().percentage
        val low = rig()
        padWith(low, battery(capacity = -0.5f))
        val lowMinus50 = events().single().percentage

        assertThat(high150).isEqualTo(-106)
        assertThat(lowMinus50).isEqualTo(-50)
    }

    @Test
    fun `an unknown battery is reported once with an unknown percentage`() {
        val rig = rig()

        padWith(rig, battery(status = BatteryState.STATUS_UNKNOWN, capacity = Float.NaN))

        assertThat(events()).containsExactly(BatteryEvent(0, 0, -1))
    }

    @Test
    fun `a status the host has no state for sends nothing`() {
        val rig = rig()

        padWith(rig, battery(status = 0))
        poll(rig)
        padWith(rig, battery(status = 6), id = 6)

        assertThat(events()).isEmpty()
    }

    @Test
    fun `a status the host has no state for is tried again at the next poll`() {
        val rig = rig()
        val battery = battery(status = 0)
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryStatus = BatteryState.STATUS_FULL
        poll(rig)

        assertThat(events()).containsExactly(BatteryEvent(0, 5, 50))
    }

    @Test
    fun `a pad without a battery reports nothing`() {
        val rig = rig()

        padWith(rig, battery(present = false))
        poll(rig)

        assertThat(events()).isEmpty()
    }

    @Test
    fun `the battery is read again every 120 seconds`() {
        val rig = rig()
        val battery = battery()
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryCapacity = 0.25f
        poll(rig, POLL_MS - 1)
        assertThat(events()).hasSize(1)
        poll(rig, 1)

        assertThat(events()).hasSize(2)
        assertThat(events().last()).isEqualTo(BatteryEvent(0, 2, 25))
    }

    @Test
    fun `the poll goes on at the same pace`() {
        val rig = rig()
        val battery = battery()
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryCapacity = 0.25f
        poll(rig)
        Hardware.batteryState(battery).batteryCapacity = 0.75f
        poll(rig, POLL_MS - 1)
        assertThat(events()).hasSize(2)
        poll(rig, 1)

        assertThat(events().map { it.percentage }).containsExactly(50, 25, 75).inOrder()
    }

    @Test
    fun `an unchanged battery is not reported again`() {
        val rig = rig()
        padWith(rig, battery())

        poll(rig)
        poll(rig)

        assertThat(events()).hasSize(1)
    }

    @Test
    fun `a change of capacity alone is reported`() {
        val rig = rig()
        val battery = battery()
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryCapacity = 0.51f
        poll(rig)

        assertThat(events().last()).isEqualTo(BatteryEvent(0, 2, 51))
    }

    @Test
    fun `a change of status alone is reported`() {
        val rig = rig()
        val battery = battery()
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryStatus = BatteryState.STATUS_CHARGING
        poll(rig)

        assertThat(events().last()).isEqualTo(BatteryEvent(0, 3, 50))
    }

    @Test
    fun `an unknown capacity that stays unknown is not reported again`() {
        val rig = rig()
        padWith(rig, battery(capacity = Float.NaN))

        poll(rig)

        assertThat(events()).hasSize(1)
    }

    @Test
    fun `a capacity that becomes unknown is reported`() {
        val rig = rig()
        val battery = battery()
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryCapacity = Float.NaN
        poll(rig)

        assertThat(events().last()).isEqualTo(BatteryEvent(0, 2, -1))
    }

    @Test
    fun `a capacity that stops being unknown is reported`() {
        val rig = rig()
        val battery = battery(capacity = Float.NaN)
        padWith(rig, battery)

        Hardware.batteryState(battery).batteryCapacity = 0.5f
        poll(rig)

        assertThat(events().last()).isEqualTo(BatteryEvent(0, 2, 50))
    }

    @Test
    fun `a report goes out with the controller number of the pad`() {
        val rig = rig { multiController = true }

        padWith(rig, battery(capacity = 0.5f), id = 5)
        padWith(rig, battery(capacity = 0.25f), id = 6)

        assertThat(events()).containsExactly(BatteryEvent(0, 2, 50), BatteryEvent(1, 2, 25))
    }

    @Test
    fun `a pad that went away is no longer polled`() {
        val rig = rig()
        val battery = battery()
        val pad = padWith(rig, battery)

        rig.handler.onInputDeviceRemoved(pad.id)
        Hardware.batteryState(battery).batteryCapacity = 0.25f
        poll(rig)

        assertThat(events()).hasSize(1)
    }

    @Test
    fun `a stopped handler no longer polls`() {
        val rig = rig()
        val battery = battery()
        padWith(rig, battery)

        rig.handler.stop()
        Hardware.batteryState(battery).batteryCapacity = 0.25f
        poll(rig)

        assertThat(events()).hasSize(1)
    }

    private companion object {
        const val POLL_MS = 120_000L
    }
}

/** Before Android 12 `InputDevice` has no battery state, so nothing is reported (the SHIELD API is not here). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class BatteryReportAndroid11Test : FeedbackTestBase() {
    @Test
    fun `no battery is reported and the pad does not claim one`() {
        val rig = rig()

        rig.wake(rig.connectPad())
        rig.awaitBackground()
        rig.advanceBackgroundBy(120_000)
        rig.awaitBackground()

        assertThat(ShadowFeedbackBridge.batteryEvents).isEmpty()
        val flag = MoonBridge.LI_CCAP_BATTERY_STATE.toInt()
        assertThat(rig.connection.arrivals.single().capabilities and flag).isEqualTo(0)
    }
}
