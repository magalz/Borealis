package io.github.magalz.borealis.input.ch5

import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.input.feedback.FeedbackTestBase
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The base of the Sprint 18a CH-5 tests (battery, rumble and motion) and of the CH-1 quad rumble
 * test: the feedback rig with the shadows of its hardware. The shadows are listed here because
 * `robolectric.properties` gives them to the `feedback` package alone, and one for `ch1` would give
 * them to its other tests too; the list is that file's.
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
// Abstract so that it does not run alone: the subclasses are the tests.
@Suppress("AbstractClassCanBeConcreteClass")
abstract class FeedbackCharacterization : FeedbackTestBase()
