package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Which vibrator a pad rumbles with, picked when the handler first sees the pad on Android 12 and
 * later: a manager with four vibrators that all have amplitude control, then one with two, then the
 * pad's own vibrator, and for a pad that is not external the same three on the phone. The pick
 * shows in what the pad reports when it arrives and in which object gets the rumble.
 */
@RunWith(RobolectricTestRunner::class)
class VibratorChoiceTest : FeedbackTestBase() {
    private fun vibrators(count: Int, amplitudeControl: Boolean = true) =
        List(count) { Hardware.vibrator(amplitudeControl = amplitudeControl) }

    private fun capabilities(rig: FeedbackRig) = rig.connection.arrivals.single().capabilities

    private fun has(capabilities: Int, flag: Short) = capabilities and flag.toInt() == flag.toInt()

    private fun rumbleOnce(rig: FeedbackRig) = rig.rumble(0x4000, 0xC000)

    private fun vibrationsOf(manager: Any) = Hardware.manager(manager).vibrations

    @Test
    fun `four vibrators win over two and over the vibrator of the pad`() {
        val quad = Hardware.vibratorManager(vibrators(4))
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = quad, vibrator = own)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(quad)).hasSize(1)
        assertThat(Hardware.calls(own)).isEmpty()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_TRIGGER_RUMBLE)).isTrue()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isTrue()
    }

    @Test
    fun `two vibrators win over the vibrator of the pad and give no trigger rumble`() {
        val dual = Hardware.vibratorManager(vibrators(2))
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = dual, vibrator = own)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(dual)).hasSize(1)
        assertThat(Hardware.calls(own)).isEmpty()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isTrue()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_TRIGGER_RUMBLE)).isFalse()
    }

    @Test
    fun `the vibrator of the pad is used when its manager has none`() {
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = own)))

        rumbleOnce(rig)

        assertThat(Hardware.calls(own)).hasSize(1)
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isTrue()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_TRIGGER_RUMBLE)).isFalse()
    }

    @Test
    fun `a manager with three vibrators is not used`() {
        val three = Hardware.vibratorManager(vibrators(3))
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = three, vibrator = own)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(three)).isEmpty()
        assertThat(Hardware.calls(own)).hasSize(1)
    }

    @Test
    fun `a manager with one vibrator is not used`() {
        val one = Hardware.vibratorManager(vibrators(1))
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = one, vibrator = own)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(one)).isEmpty()
        assertThat(Hardware.calls(own)).hasSize(1)
    }

    @Test
    fun `two vibrators of which one has no amplitude control are not used`() {
        val mixed =
            Hardware.vibratorManager(
                listOf(Hardware.vibrator(), Hardware.vibrator(amplitudeControl = false))
            )
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = mixed, vibrator = own)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(mixed)).isEmpty()
        assertThat(Hardware.calls(own)).hasSize(1)
    }

    @Test
    fun `two vibrators without amplitude control are not used either`() {
        val flat = Hardware.vibratorManager(vibrators(2, amplitudeControl = false))
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = flat)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(flat)).isEmpty()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isFalse()
    }

    @Test
    fun `four vibrators of which one has no amplitude control are neither four nor two`() {
        val mixed =
            Hardware.vibratorManager(
                listOf(
                    Hardware.vibrator(),
                    Hardware.vibrator(),
                    Hardware.vibrator(),
                    Hardware.vibrator(amplitudeControl = false),
                )
            )
        val own = Hardware.vibrator()
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibratorManager = mixed, vibrator = own)))

        rumbleOnce(rig)

        assertThat(vibrationsOf(mixed)).isEmpty()
        assertThat(Hardware.calls(own)).hasSize(1)
    }

    @Test
    fun `an external pad without a vibrator does not rumble and says so`() {
        val phoneQuad = Hardware.vibratorManager(vibrators(4))
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone, phoneVibratorManager = phoneQuad)
        rig.wake(rig.connectPad())

        rumbleOnce(rig)

        assertThat(vibrationsOf(phoneQuad)).isEmpty()
        assertThat(Hardware.calls(phone)).isEmpty()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isFalse()
    }

    @Test
    fun `a vibrator that reports none is not the vibrator of the pad`() {
        val absent = Hardware.vibrator(present = false)
        val rig = rig()
        rig.wake(rig.connectPad(features = DeviceFeatures(vibrator = absent)))

        rumbleOnce(rig)

        assertThat(Hardware.calls(absent)).isEmpty()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isFalse()
    }

    @Test
    fun `a pad that is not external rumbles the four vibrators of the phone`() {
        val phoneQuad = Hardware.vibratorManager(vibrators(4))
        val rig = rig(phoneVibratorManager = phoneQuad)
        rig.wake(rig.connectPad(external = false))

        rumbleOnce(rig)

        assertThat(vibrationsOf(phoneQuad)).hasSize(1)
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_TRIGGER_RUMBLE)).isTrue()
    }

    @Test
    fun `a pad that is not external rumbles the two vibrators of the phone`() {
        val phoneDual = Hardware.vibratorManager(vibrators(2))
        val rig = rig(phoneVibratorManager = phoneDual)
        rig.wake(rig.connectPad(external = false))

        rumbleOnce(rig)

        assertThat(vibrationsOf(phoneDual)).hasSize(1)
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_TRIGGER_RUMBLE)).isFalse()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isTrue()
    }

    @Test
    fun `a pad that is not external rumbles the vibrator of the phone`() {
        val phone = Hardware.vibrator()
        val rig = rig(phoneVibrator = phone)
        rig.wake(rig.connectPad(external = false))

        rumbleOnce(rig)

        assertThat(Hardware.calls(phone)).hasSize(1)
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isTrue()
    }

    @Test
    fun `a pad that is not external and finds no vibrator on the phone does not rumble`() {
        val phone = Hardware.vibrator(present = false)
        val rig = rig(phoneVibrator = phone)
        rig.wake(rig.connectPad(external = false))

        rumbleOnce(rig)

        assertThat(Hardware.calls(phone)).isEmpty()
        assertThat(has(capabilities(rig), MoonBridge.LI_CCAP_RUMBLE)).isFalse()
    }

    @Test
    fun `the vibrator of the pad wins over the phone for a pad that is not external`() {
        val phoneQuad = Hardware.vibratorManager(vibrators(4))
        val own = Hardware.vibrator()
        val rig = rig(phoneVibratorManager = phoneQuad)
        rig.wake(rig.connectPad(external = false, features = DeviceFeatures(vibrator = own)))

        rumbleOnce(rig)

        assertThat(Hardware.calls(own)).hasSize(1)
        assertThat(vibrationsOf(phoneQuad)).isEmpty()
    }

    @Test
    fun `the two vibrators of the phone win over its single vibrator`() {
        val phone = Hardware.vibrator()
        val phoneDual = Hardware.vibratorManager(vibrators(2), defaultVibrator = phone)
        val rig = rig(phoneVibrator = phone, phoneVibratorManager = phoneDual)
        rig.wake(rig.connectPad(external = false))

        rumbleOnce(rig)

        assertThat(vibrationsOf(phoneDual)).hasSize(1)
        assertThat(Hardware.calls(phone)).isEmpty()
    }

    @Test
    fun `the pad reports a battery only when it is external`() {
        val internal = rig()
        internal.wake(internal.connectPad(external = false))
        val external = rig()
        external.wake(external.connectPad())

        assertThat(has(capabilities(internal), MoonBridge.LI_CCAP_BATTERY_STATE)).isFalse()
        assertThat(has(capabilities(external), MoonBridge.LI_CCAP_BATTERY_STATE)).isTrue()
    }
}
