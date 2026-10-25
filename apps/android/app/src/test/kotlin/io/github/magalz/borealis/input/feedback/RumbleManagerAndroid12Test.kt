package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The manager rumble on Android 12: the same combinations as on Android 13, but the attributes
 * carry no usage, because `VibrationAttributes.setUsage` is only called from Android 13.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class RumbleManagerAndroid12Test : FeedbackTestBase() {
    @Test
    fun `two vibrators are driven with attributes that have no usage`() {
        val manager = Hardware.vibratorManager(List(2) { Hardware.vibrator() })
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.rumble(0x4000, 0xC000)

        assertThat(Hardware.manager(manager).vibrations.map { it.combined to it.attributes })
            .containsExactly(
                VibrationExpectations.combo(10 to 192, 11 to 64) to VibrationExpectations.unspecified()
            )
    }

    @Test
    fun `four vibrators are driven with attributes that have no usage`() {
        val manager = Hardware.vibratorManager(List(4) { Hardware.vibrator() })
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.rumble(0x4000, 0xC000)
        rig.rumbleTriggers(0x2000, 0x6000)

        assertThat(Hardware.manager(manager).vibrations.map { it.combined to it.attributes })
            .containsExactly(
                VibrationExpectations.combo(10 to 192, 11 to 64) to VibrationExpectations.unspecified(),
                VibrationExpectations.combo(10 to 192, 11 to 64, 12 to 32, 13 to 96) to
                    VibrationExpectations.unspecified(),
            )
            .inOrder()
    }

    @Test
    fun `a trigger rumble reaches a pad with four vibrators`() {
        val manager = Hardware.vibratorManager(List(4) { Hardware.vibrator() })
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = manager)))

        rig.rumbleTriggers(0x2000, 0x6000)

        assertThat(Hardware.manager(manager).vibrations.map { it.combined })
            .containsExactly(VibrationExpectations.combo(12 to 32, 13 to 96))
    }
}
