package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The rumble of a pad whose vibrator manager has two or four vibrators, as the handler sends it on
 * Android 13 and later: each motor is the high byte of its 16-bit value, a vibrator at zero is left
 * out of the combination, and all zero cancels the manager. The effect is a 60-second one-shot at
 * that amplitude, with the media usage.
 */
@RunWith(RobolectricTestRunner::class)
class RumbleManagerTest : FeedbackTestBase() {
    private val first = Hardware.vibrator()
    private val second = Hardware.vibrator()
    private val manager = Hardware.vibratorManager(listOf(first, second))
    private val recorded
        get() = Hardware.manager(manager)

    private fun dualPad(rig: FeedbackRig = rig()): Pair<FeedbackRig, Pad> {
        val pad = rig.connectPad(features = DeviceFeatures(vibratorManager = manager))
        rig.wake(pad)
        return rig to pad
    }

    private fun vibrations() = recorded.vibrations.map { it.combined to it.attributes }

    @Test
    fun `the high motor drives the first vibrator and the low motor the second`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0x4000, 0xC000.toShort())

        assertThat(vibrations())
            .containsExactly(
                VibrationExpectations.combo(10 to 192, 11 to 64) to VibrationExpectations.media()
            )
    }

    @Test
    fun `the vibrators are matched by position and not by id`() {
        val a = Hardware.vibrator()
        val b = Hardware.vibrator()
        val reversed = Hardware.vibratorManager(listOf(a, b), ids = intArrayOf(42, 7))
        val rig = rig()
        val pad = rig.connectPad(features = DeviceFeatures(vibratorManager = reversed))
        rig.wake(pad)

        rig.handler.handleRumble(0, 0x4000, 0xC000.toShort())

        assertThat(Hardware.manager(reversed).vibrations.map { it.combined })
            .containsExactly(VibrationExpectations.combo(42 to 192, 7 to 64))
    }

    @Test
    fun `a motor value keeps only its high byte`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0x40FF, 0x01FF)

        assertThat(recorded.vibrations.map { it.combined })
            .containsExactly(VibrationExpectations.combo(10 to 1, 11 to 64))
    }

    @Test
    fun `the high byte of a value past the sign bit is not negative`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0x8000.toShort(), 0xFFFF.toShort())
        rig.handler.handleRumble(0, 0x7FFF, 0xFF00.toShort())

        assertThat(recorded.vibrations.map { it.combined })
            .containsExactly(
                VibrationExpectations.combo(10 to 255, 11 to 128),
                VibrationExpectations.combo(10 to 255, 11 to 127),
            )
            .inOrder()
    }

    @Test
    fun `a motor at zero leaves its vibrator out of the combination`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0, 0x4000)
        rig.handler.handleRumble(0, 0x4000, 0)

        assertThat(recorded.vibrations.map { it.combined })
            .containsExactly(
                VibrationExpectations.combo(10 to 64),
                VibrationExpectations.combo(11 to 64),
            )
            .inOrder()
    }

    @Test
    fun `a motor whose high byte is zero counts as zero`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0x00FF, 0x4000)

        assertThat(recorded.vibrations.map { it.combined })
            .containsExactly(VibrationExpectations.combo(10 to 64))
    }

    @Test
    fun `both motors at zero cancel the manager and start nothing`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0, 0)
        rig.handler.handleRumble(0, 0x00FF, 0x00FF)

        assertThat(recorded.cancels).isEqualTo(2)
        assertThat(recorded.vibrations).isEmpty()
    }

    @Test
    fun `a dual pad has no trigger motors, so a trigger rumble does nothing`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumbleTriggers(0, 0x4000, 0x4000)

        assertThat(recorded.vibrations).isEmpty()
        assertThat(recorded.cancels).isEqualTo(0)
    }

    @Test
    fun `the vibrators of the pad itself are not asked directly`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0x4000, 0x4000)

        assertThat(Hardware.calls(first)).isEmpty()
        assertThat(Hardware.calls(second)).isEmpty()
    }

    @Test
    fun `the phone does not vibrate when the pad did`() {
        val phone = Hardware.vibrator()
        val rig =
            rig(phoneVibrator = phone) {
                vibrateFallbackToDevice = true
                vibrateFallbackToDeviceStrength = 100
            }
        val pad = rig.connectPad(features = DeviceFeatures(vibratorManager = manager))
        rig.wake(pad)

        rig.handler.handleRumble(0, 0x4000, 0x4000)

        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `a rumble for another controller number reaches no vibrator`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(3, 0x4000, 0x4000)

        assertThat(recorded.vibrations).isEmpty()
    }

    @Test
    fun `the second pad gets the rumble of its own controller number`() {
        val other = Hardware.vibratorManager(listOf(Hardware.vibrator(), Hardware.vibrator()))
        val rig = rig { multiController = true }
        val pad0 = rig.connectPad(id = 5, features = DeviceFeatures(vibratorManager = manager))
        val pad1 = rig.connectPad(id = 6, features = DeviceFeatures(vibratorManager = other))
        rig.wake(pad0)
        rig.wake(pad1)

        rig.handler.handleRumble(1, 0x4000, 0xC000.toShort())

        assertThat(recorded.vibrations).isEmpty()
        assertThat(Hardware.manager(other).vibrations.map { it.combined })
            .containsExactly(VibrationExpectations.combo(10 to 192, 11 to 64))
    }

    @Test
    fun `two pads that share a controller number both rumble`() {
        val other = Hardware.vibratorManager(listOf(Hardware.vibrator(), Hardware.vibrator()))
        val rig = rig()
        val pad0 = rig.connectPad(id = 5, features = DeviceFeatures(vibratorManager = manager))
        val pad1 = rig.connectPad(id = 6, features = DeviceFeatures(vibratorManager = other))
        rig.wake(pad0)
        rig.wake(pad1)

        rig.handler.handleRumble(0, 0x4000, 0x4000)

        assertThat(recorded.vibrations).hasSize(1)
        assertThat(Hardware.manager(other).vibrations).hasSize(1)
    }

    @Test
    fun `a stopped handler does not rumble`() {
        val (rig, _) = dualPad()
        rig.handler.stop()

        rig.handler.handleRumble(0, 0x4000, 0x4000)

        assertThat(recorded.vibrations).isEmpty()
    }

    @Test
    fun `the vibration is attributed to media`() {
        val (rig, _) = dualPad()

        rig.handler.handleRumble(0, 0x4000, 0x4000)

        assertThat(recorded.vibrations.single().attributes).isEqualTo(VibrationExpectations.media())
    }
}
