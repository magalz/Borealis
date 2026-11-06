package io.github.magalz.borealis.input.ch1

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.input.feedback.DeviceFeatures
import io.github.magalz.borealis.input.feedback.FeedbackRig
import io.github.magalz.borealis.input.feedback.FeedbackTestBase
import io.github.magalz.borealis.input.feedback.Hardware
import io.github.magalz.borealis.input.feedback.ShadowFeedbackInputDevice
import io.github.magalz.borealis.input.feedback.ShadowRecordingBatteryState
import io.github.magalz.borealis.input.feedback.ShadowRecordingInputDeviceBatteryState
import io.github.magalz.borealis.input.feedback.ShadowRecordingLightsManager
import io.github.magalz.borealis.input.feedback.ShadowRecordingLightsSession
import io.github.magalz.borealis.input.feedback.ShadowRecordingLocalBatteryState
import io.github.magalz.borealis.input.feedback.ShadowRecordingSensorManager
import io.github.magalz.borealis.input.feedback.ShadowRecordingSystemLightsManager
import io.github.magalz.borealis.input.feedback.ShadowRecordingSystemLightsSession
import io.github.magalz.borealis.input.feedback.ShadowRecordingSystemSensorManager
import io.github.magalz.borealis.input.feedback.ShadowRecordingSystemVibrator
import io.github.magalz.borealis.input.feedback.ShadowRecordingSystemVibratorManager
import io.github.magalz.borealis.input.feedback.ShadowRecordingVibrator
import io.github.magalz.borealis.input.feedback.ShadowRecordingVibratorManager
import io.github.magalz.borealis.input.feedback.ShadowSceManager
import io.github.magalz.borealis.input.feedback.VibrationExpectations
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A pad with four vibrators (the high motor, the low motor, the left trigger and the right trigger,
 * in that order) cancels its vibration only when all four values are zero. Each motor alone keeps
 * its own vibrator going, with the triggers at zero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowFeedbackInputDevice::class,
            ShadowRecordingVibrator::class,
            ShadowRecordingSystemVibrator::class,
            ShadowRecordingVibratorManager::class,
            ShadowRecordingSystemVibratorManager::class,
            ShadowRecordingSensorManager::class,
            ShadowRecordingSystemSensorManager::class,
            ShadowRecordingLightsManager::class,
            ShadowRecordingSystemLightsManager::class,
            ShadowRecordingLightsSession::class,
            ShadowRecordingSystemLightsSession::class,
            ShadowRecordingBatteryState::class,
            ShadowRecordingInputDeviceBatteryState::class,
            ShadowRecordingLocalBatteryState::class,
            ShadowSceManager::class,
        ]
)
class QuadRumbleOneMotorTest : FeedbackTestBase() {
    private val manager = Hardware.vibratorManager(List(4) { Hardware.vibrator() })
    private val recorded
        get() = Hardware.manager(manager)

    private fun quadPad(): FeedbackRig {
        val rig = rig()
        val pad = rig.connectPad(features = DeviceFeatures(vibratorManager = manager))
        rig.wake(pad)
        return rig
    }

    private fun combos() = recorded.vibrations.map { it.combined }

    @Test
    fun `the high motor alone vibrates its vibrator and does not cancel`() {
        val rig = quadPad()

        rig.handler.handleRumble(0, 0, 0x4000)

        assertThat(recorded.cancels).isEqualTo(0)
        assertThat(combos()).containsExactly(VibrationExpectations.combo(10 to 64))
    }

    @Test
    fun `the low motor alone vibrates its vibrator and does not cancel`() {
        val rig = quadPad()

        rig.handler.handleRumble(0, 0x4000, 0)

        assertThat(recorded.cancels).isEqualTo(0)
        assertThat(combos()).containsExactly(VibrationExpectations.combo(11 to 64))
    }
}
