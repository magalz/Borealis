package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Before Android 14 `Light.hasRgbControl()` says yes for every light, so the handler trusts it only
 * for a PlayStation pad, whose vendor id it knows.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class ControllerLedAndroid12Test : FeedbackTestBase() {
    private val lights = Hardware.lightsManager(listOf(Hardware.rgbLight(1)))

    private fun padOf(rig: FeedbackRig, vendorId: Int): Pad {
        val pad =
            rig.connectPad(vendorId = vendorId, features = DeviceFeatures(lightsManager = lights))
        rig.wake(pad)
        return pad
    }

    private val sessions
        get() = Hardware.lights(lights).sessions

    @Test
    fun `a PlayStation pad takes the colour`() {
        val rig = rig()
        padOf(rig, FeedbackRig.SONY_VENDOR)

        rig.led(0x80, 0x10, 0xFF)
        rig.awaitBackground()

        assertThat(Hardware.session(sessions.single()).requests)
            .containsExactly(listOf(LightColor(1, 0xFF8010FF.toInt())))
    }

    @Test
    fun `a PlayStation pad reports its LED`() {
        val rig = rig()
        padOf(rig, FeedbackRig.SONY_VENDOR)

        val flag = MoonBridge.LI_CCAP_RGB_LED.toInt()
        assertThat(rig.connection.arrivals.single().capabilities and flag).isEqualTo(flag)
    }

    @Test
    fun `an Xbox pad that has a light that claims colour control is not trusted`() {
        val rig = rig()
        padOf(rig, FeedbackRig.XBOX_VENDOR)

        rig.led(1, 2, 3)
        rig.awaitBackground()

        assertThat(sessions).isEmpty()
        val flag = MoonBridge.LI_CCAP_RGB_LED.toInt()
        assertThat(rig.connection.arrivals.single().capabilities and flag).isEqualTo(0)
    }

    @Test
    fun `a Nintendo pad is not trusted either`() {
        val rig = rig()
        padOf(rig, FeedbackRig.NINTENDO_VENDOR)

        rig.led(1, 2, 3)
        rig.awaitBackground()

        assertThat(sessions).isEmpty()
    }

    @Test
    fun `a pad of an unknown vendor is not trusted`() {
        val rig = rig()
        padOf(rig, 0x1234)

        rig.led(1, 2, 3)
        rig.awaitBackground()

        assertThat(sessions).isEmpty()
    }
}

/** Android 11 has no lights API for input devices, so a colour goes nowhere and nothing breaks. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
class ControllerLedAndroid11Test : FeedbackTestBase() {
    @Test
    fun `a colour is ignored and the pad reports no LED`() {
        val rig = rig()
        rig.wake(rig.connectPad(vendorId = FeedbackRig.SONY_VENDOR))

        rig.led(1, 2, 3)
        rig.awaitBackground()

        val flag = MoonBridge.LI_CCAP_RGB_LED.toInt()
        assertThat(rig.connection.arrivals.single().capabilities and flag).isEqualTo(0)
    }
}
