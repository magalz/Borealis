package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A pad whose vibrator manager has four vibrators: the high motor, the low motor, the left trigger
 * and the right trigger, in that order. The trigger values are kept by the handler, so a rumble
 * and a trigger rumble each send all four.
 */
@RunWith(RobolectricTestRunner::class)
class RumbleQuadTest : FeedbackTestBase() {
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
    fun `a rumble drives the first two vibrators and the triggers stay at their last value`() {
        val rig = quadPad()

        rig.handler.handleRumble(0, 0x4000, 0xC000.toShort())

        assertThat(combos()).containsExactly(VibrationExpectations.combo(10 to 192, 11 to 64))
    }

    @Test
    fun `a trigger rumble drives the last two vibrators with the motors at their last value`() {
        val rig = quadPad()

        rig.handler.handleRumbleTriggers(0, 0x2000, 0x6000)

        assertThat(combos()).containsExactly(VibrationExpectations.combo(12 to 32, 13 to 96))
    }

    @Test
    fun `a rumble after a trigger rumble sends all four values`() {
        val rig = quadPad()
        rig.handler.handleRumbleTriggers(0, 0x2000, 0x6000)

        rig.handler.handleRumble(0, 0x4000, 0xC000.toShort())

        assertThat(combos().last())
            .isEqualTo(VibrationExpectations.combo(10 to 192, 11 to 64, 12 to 32, 13 to 96))
    }

    @Test
    fun `a trigger rumble after a rumble sends all four values`() {
        val rig = quadPad()
        rig.handler.handleRumble(0, 0x4000, 0xC000.toShort())

        rig.handler.handleRumbleTriggers(0, 0x2000, 0x6000)

        assertThat(combos().last())
            .isEqualTo(VibrationExpectations.combo(10 to 192, 11 to 64, 12 to 32, 13 to 96))
    }

    @Test
    fun `trigger values keep only their high byte and are unsigned`() {
        val rig = quadPad()

        rig.handler.handleRumbleTriggers(0, 0xFFFF.toShort(), 0x80FF.toShort())

        assertThat(combos()).containsExactly(VibrationExpectations.combo(12 to 255, 13 to 128))
    }

    @Test
    fun `a trigger left at zero is out of the combination`() {
        val rig = quadPad()

        rig.handler.handleRumbleTriggers(0, 0, 0x4000)
        rig.handler.handleRumbleTriggers(0, 0x4000, 0)

        assertThat(combos())
            .containsExactly(VibrationExpectations.combo(13 to 64), VibrationExpectations.combo(12 to 64))
            .inOrder()
    }

    @Test
    fun `all four at zero cancel the manager`() {
        val rig = quadPad()
        rig.handler.handleRumbleTriggers(0, 0x4000, 0x4000)

        rig.handler.handleRumbleTriggers(0, 0, 0)
        rig.handler.handleRumble(0, 0, 0)

        assertThat(recorded.cancels).isEqualTo(2)
        assertThat(combos()).hasSize(1)
    }

    @Test
    fun `a rumble at zero keeps the triggers going`() {
        val rig = quadPad()
        rig.handler.handleRumbleTriggers(0, 0x4000, 0x4000)

        rig.handler.handleRumble(0, 0, 0)

        assertThat(combos().last()).isEqualTo(VibrationExpectations.combo(12 to 64, 13 to 64))
        assertThat(recorded.cancels).isEqualTo(0)
    }

    @Test
    fun `the vibrators are matched by position and not by id`() {
        val reversed =
            Hardware.vibratorManager(List(4) { Hardware.vibrator() }, ids = intArrayOf(9, 3, 7, 1))
        val rig = rig()
        val pad = rig.connectPad(features = DeviceFeatures(vibratorManager = reversed))
        rig.wake(pad)

        rig.handler.handleRumble(0, 0x4000, 0xC000.toShort())
        rig.handler.handleRumbleTriggers(0, 0x2000, 0x6000)

        assertThat(Hardware.manager(reversed).vibrations.last().combined)
            .isEqualTo(VibrationExpectations.combo(9 to 192, 3 to 64, 7 to 32, 1 to 96))
    }

    @Test
    fun `a trigger rumble for another controller number reaches nobody`() {
        val rig = quadPad()

        rig.handler.handleRumbleTriggers(1, 0x4000, 0x4000)

        assertThat(combos()).isEmpty()
    }

    @Test
    fun `a stopped handler stores and sends no trigger rumble`() {
        val rig = quadPad()
        rig.handler.stop()

        rig.handler.handleRumbleTriggers(0, 0x4000, 0x4000)

        assertThat(combos()).isEmpty()
    }

    @Test
    fun `the pad reports rumble and trigger rumble when it arrives`() {
        val rig = quadPad()

        val capabilities = rig.connection.arrivals.single().capabilities
        assertThat(capabilities and MoonBridge.LI_CCAP_RUMBLE.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_RUMBLE.toInt())
        assertThat(capabilities and MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt())
            .isEqualTo(MoonBridge.LI_CCAP_TRIGGER_RUMBLE.toInt())
    }
}
