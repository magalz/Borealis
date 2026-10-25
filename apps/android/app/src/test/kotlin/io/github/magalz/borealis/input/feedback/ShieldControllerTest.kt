package io.github.magalz.borealis.input.feedback

import android.hardware.BatteryState
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.cgutman.shieldcontrollerextensions.SceChargingState
import org.cgutman.shieldcontrollerextensions.SceConnectionType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The SHIELD controllers, which have their own service for the battery and the rumble, and which
 * the handler asks before it asks Android for a vibrator. The service answers a connection type, a
 * charging state and a percentage, and the handler turns them into the battery state of Android.
 */
// Abstract so that it does not run alone: the subclasses carry the Robolectric runner and the SDK level.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class ShieldContract : FeedbackTestBase() {
    private data class Reading(
        val percentage: Int,
        val connection: SceConnectionType,
        val charging: SceChargingState,
    )

    private fun reported(reading: Reading): BatteryEvent {
        val rig = rig()
        ShadowSceManager.recognized = true
        ShadowSceManager.batteryPercentage = reading.percentage
        ShadowSceManager.connectionType = reading.connection
        ShadowSceManager.chargingState = reading.charging
        rig.wake(rig.connectPad())
        return ShadowFeedbackBridge.batteryEvents.single()
    }

    private fun event(state: Int, percentage: Int) = BatteryEvent(0, state, percentage)

    @Test
    fun `a wired pad is full at 100 percent, not charging or charging otherwise`() {
        val results =
            listOf(
                    Reading(100, SceConnectionType.WIRED, SceChargingState.NOT_CHARGING),
                    Reading(100, SceConnectionType.BOTH, SceChargingState.CHARGING),
                    Reading(50, SceConnectionType.WIRED, SceChargingState.NOT_CHARGING),
                    Reading(50, SceConnectionType.BOTH, SceChargingState.NOT_CHARGING),
                    Reading(50, SceConnectionType.WIRED, SceChargingState.CHARGING),
                    Reading(50, SceConnectionType.WIRED, SceChargingState.UNKNOWN),
                    Reading(50, SceConnectionType.BOTH, SceChargingState.UNKNOWN),
                )
                .map(::reported)

        assertThat(results)
            .containsExactly(
                event(5, 100),
                event(5, 100),
                event(4, 50),
                event(4, 50),
                event(3, 50),
                event(3, 50),
                event(3, 50),
            )
            .inOrder()
    }

    @Test
    fun `a wireless pad is charging or discharging, and never full`() {
        val results =
            listOf(
                    Reading(50, SceConnectionType.WIRELESS, SceChargingState.CHARGING),
                    Reading(50, SceConnectionType.WIRELESS, SceChargingState.NOT_CHARGING),
                    Reading(50, SceConnectionType.WIRELESS, SceChargingState.UNKNOWN),
                    Reading(100, SceConnectionType.WIRELESS, SceChargingState.NOT_CHARGING),
                    Reading(100, SceConnectionType.WIRELESS, SceChargingState.CHARGING),
                )
                .map(::reported)

        assertThat(results)
            .containsExactly(event(3, 50), event(2, 50), event(2, 50), event(2, 100), event(3, 100))
            .inOrder()
    }

    @Test
    fun `a pad of an unknown connection goes by the charging state`() {
        val results =
            listOf(
                    Reading(100, SceConnectionType.UNKNOWN, SceChargingState.NOT_CHARGING),
                    Reading(100, SceConnectionType.UNKNOWN, SceChargingState.UNKNOWN),
                    Reading(50, SceConnectionType.UNKNOWN, SceChargingState.NOT_CHARGING),
                    Reading(50, SceConnectionType.UNKNOWN, SceChargingState.CHARGING),
                    Reading(50, SceConnectionType.UNKNOWN, SceChargingState.UNKNOWN),
                )
                .map(::reported)

        assertThat(results)
            .containsExactly(event(5, 100), event(5, 100), event(2, 50), event(3, 50), event(0, 50))
            .inOrder()
    }

    @Test
    fun `a negative percentage is an unknown capacity`() {
        val results =
            listOf(
                    Reading(-1, SceConnectionType.WIRELESS, SceChargingState.CHARGING),
                    Reading(-40, SceConnectionType.WIRED, SceChargingState.NOT_CHARGING),
                )
                .map(::reported)

        assertThat(results).containsExactly(event(3, -1), event(4, -1)).inOrder()
    }

    @Test
    fun `a percentage goes through a float, which loses one at 53 and at 59`() {
        val results =
            listOf(1, 29, 53, 57, 59, 99).map {
                reported(Reading(it, SceConnectionType.WIRELESS, SceChargingState.CHARGING))
                    .percentage
            }

        assertThat(results).containsExactly(1, 29, 52, 57, 58, 99).inOrder()
    }

    @Test
    fun `the battery is polled again with the service`() {
        val rig = rig()
        ShadowSceManager.recognized = true
        ShadowSceManager.batteryPercentage = 80
        ShadowSceManager.connectionType = SceConnectionType.WIRELESS
        ShadowSceManager.chargingState = SceChargingState.CHARGING
        rig.wake(rig.connectPad())

        ShadowSceManager.chargingState = SceChargingState.NOT_CHARGING
        rig.advanceBackgroundBy(120_000)
        rig.awaitBackground()

        assertThat(ShadowFeedbackBridge.batteryEvents)
            .containsExactly(event(3, 80), event(2, 80))
            .inOrder()
    }

    @Test
    fun `a pad the service does not know reports no battery`() {
        val rig = rig()
        ShadowSceManager.batteryPercentage = 80
        rig.wake(rig.connectPad())

        assertThat(ShadowFeedbackBridge.batteryEvents).isEmpty()
    }

    @Test
    fun `a pad the service knows reports a rumble and a battery on arrival, whatever its vibrators`() {
        val rig = rig()
        ShadowSceManager.recognized = true
        rig.wake(rig.connectPad(external = false))

        val capabilities = rig.connection.arrivals.single().capabilities
        val rumble = MoonBridge.LI_CCAP_RUMBLE.toInt()
        val battery = MoonBridge.LI_CCAP_BATTERY_STATE.toInt()
        assertThat(capabilities and (rumble or battery)).isEqualTo(rumble or battery)
    }

    @Test
    fun `a rumble goes to the service as signed ints and nothing else vibrates`() {
        val own = Hardware.vibrator()
        val phone = Hardware.vibrator()
        val rig =
            rig(phoneVibrator = phone) {
                vibrateFallbackToDevice = true
                vibrateFallbackToDeviceStrength = 100
            }
        ShadowSceManager.recognized = true
        ShadowSceManager.rumbleAccepted = true
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = own)))

        rig.rumble(0x4000, 0xC000)

        assertThat(ShadowSceManager.rumbles).containsExactly(0x4000 to -0x4000)
        assertThat(Hardware.calls(own)).isEmpty()
        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `a rumble the service does not take goes on to the vibrator of the pad`() {
        val own = Hardware.vibrator()
        val rig = rig()
        ShadowSceManager.recognized = true
        ShadowSceManager.rumbleAccepted = false
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = own)))

        rig.rumble(0x6400, 0x6400)

        assertThat(ShadowSceManager.rumbles).containsExactly(0x6400 to 0x6400)
        assertThat(Hardware.calls(own)).hasSize(1)
    }

    @Test
    fun `a rumble the service does not take and no vibrator leaves the pad silent`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)
        rig.wake(rig.connectPad())

        rig.rumble(0x6400, 0x6400)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `a rumble for another controller number is not asked of the service`() {
        val rig = rig()
        ShadowSceManager.rumbleAccepted = true
        rig.wake(rig.connectPad())

        rig.rumble(0x6400, 0x6400, controller = 1)

        assertThat(ShadowSceManager.rumbles).isEmpty()
    }

    @Test
    fun `the service is started with the handler and stopped with its destruction`() {
        val rig = rig()
        assertThat(ShadowSceManager.started).isEqualTo(1)
        assertThat(ShadowSceManager.stopped).isEqualTo(0)

        rig.handler.stop()
        assertThat(ShadowSceManager.stopped).isEqualTo(0)
        rig.destroy()

        assertThat(ShadowSceManager.stopped).isEqualTo(1)
    }
}

/** Android 13 and later. */
@RunWith(RobolectricTestRunner::class)
class ShieldControllerTest : ShieldContract() {
    @Test
    fun `the battery of Android wins when the pad has one`() {
        val battery = Hardware.battery(status = BatteryState.STATUS_FULL, capacity = 1f)
        val rig = rig()
        ShadowSceManager.recognized = true
        ShadowSceManager.batteryPercentage = 10
        ShadowSceManager.connectionType = SceConnectionType.WIRELESS
        ShadowSceManager.chargingState = SceChargingState.CHARGING
        rig.wake(rig.connectPad(features = DeviceFeatures(batteryState = battery)))

        assertThat(ShadowFeedbackBridge.batteryEvents).containsExactly(BatteryEvent(0, 5, 100))
    }

    @Test
    fun `the vibrators of Android win over the service for a pad that has a manager`() {
        val manager = Hardware.vibratorManager(List(2) { Hardware.vibrator() })
        val rig = rig()
        ShadowSceManager.recognized = true
        ShadowSceManager.rumbleAccepted = true
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.rumble(0x4000, 0xC000)

        assertThat(ShadowSceManager.rumbles).isEmpty()
        assertThat(Hardware.manager(manager).vibrations).hasSize(1)
    }
}

/** Android 11, which has no battery state on input devices: the service is the only source. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ShieldControllerAndroid11Test : ShieldContract()
