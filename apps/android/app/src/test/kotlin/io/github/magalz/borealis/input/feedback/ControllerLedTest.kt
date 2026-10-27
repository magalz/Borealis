package io.github.magalz.borealis.input.feedback

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.testing.LimeLogCapture
import java.util.logging.Level
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The colour a host sets on the LED of a pad, on Android 14 and later where `Light.hasRgbControl()`
 * can be trusted: the handler packs the three signed bytes into an opaque ARGB int and has the
 * handler's own thread hand it to every RGB light of the pad, through one lights session that it
 * keeps open until the pad goes.
 */
@RunWith(RobolectricTestRunner::class)
class ControllerLedTest : FeedbackTestBase() {
    private val rgb = Hardware.rgbLight(1)
    private val lights = Hardware.lightsManager(listOf(rgb))

    private fun argb(red: Int, green: Int, blue: Int) =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private fun ledPad(
        rig: FeedbackRig,
        manager: Any = lights,
        id: Int = FeedbackRig.PAD_ID,
    ): Pad {
        val pad = rig.connectPad(id = id, features = DeviceFeatures(lightsManager = manager))
        rig.wake(pad)
        return pad
    }

    private fun sessions(manager: Any = lights) =
        Hardware.lights(manager).sessions.map { Hardware.session(it) }

    private fun setLed(rig: FeedbackRig, red: Int, green: Int, blue: Int, controller: Int = 0) {
        rig.led(red, green, blue, controller)
        rig.awaitBackground()
    }

    @Test
    fun `the colour reaches the light as an opaque ARGB int`() {
        val rig = rig()
        ledPad(rig)

        setLed(rig, 0x80, 0x10, 0xFF)

        assertThat(sessions().single().requests)
            .containsExactly(listOf(LightColor(1, 0xFF8010FF.toInt())))
    }

    @Test
    fun `each component lands in its own byte, whatever its sign`() {
        val rig = rig()
        ledPad(rig)

        setLed(rig, 0xFF, 0, 0)
        setLed(rig, 0, 0xFF, 0)
        setLed(rig, 0, 0, 0xFF)
        setLed(rig, 0, 0, 0)
        setLed(rig, 0x7F, 0x80, 0x01)

        assertThat(sessions().single().requests.map { it.single().argb })
            .containsExactly(
                argb(0xFF, 0, 0),
                argb(0, 0xFF, 0),
                argb(0, 0, 0xFF),
                argb(0, 0, 0),
                argb(0x7F, 0x80, 0x01),
            )
            .inOrder()
        assertThat(sessions().single().requests[0].single().argb).isEqualTo(0xFFFF0000.toInt())
        assertThat(sessions().single().requests[1].single().argb).isEqualTo(0xFF00FF00.toInt())
        assertThat(sessions().single().requests[2].single().argb).isEqualTo(0xFF0000FF.toInt())
        assertThat(sessions().single().requests[3].single().argb).isEqualTo(0xFF000000.toInt())
    }

    @Test
    fun `one session serves every request`() {
        val rig = rig()
        ledPad(rig)

        setLed(rig, 1, 2, 3)
        setLed(rig, 4, 5, 6)

        assertThat(sessions()).hasSize(1)
        assertThat(sessions().single().requests).hasSize(2)
        assertThat(sessions().single().closed).isFalse()
    }

    @Test
    fun `no session is opened before the host sets a colour`() {
        val rig = rig()
        ledPad(rig)
        rig.awaitBackground()

        assertThat(sessions()).isEmpty()
    }

    @Test
    fun `a light without colour control is left out of the request`() {
        val plain = Hardware.plainLight(2)
        val mixed = Hardware.lightsManager(listOf(plain, rgb))
        val rig = rig()
        ledPad(rig, mixed)

        setLed(rig, 1, 2, 3)

        assertThat(sessions(mixed).single().requests.single())
            .containsExactly(LightColor(1, argb(1, 2, 3)))
    }

    @Test
    fun `every light with colour control gets the colour`() {
        val second = Hardware.rgbLight(3)
        val two = Hardware.lightsManager(listOf(rgb, second))
        val rig = rig()
        ledPad(rig, two)

        setLed(rig, 1, 2, 3)

        assertThat(sessions(two).single().requests.single())
            .containsExactly(LightColor(1, argb(1, 2, 3)), LightColor(3, argb(1, 2, 3)))
    }

    @Test
    fun `a pad whose lights have no colour control ignores the colour`() {
        val plain = Hardware.lightsManager(listOf(Hardware.plainLight(2)))
        val rig = rig()
        ledPad(rig, plain)

        setLed(rig, 1, 2, 3)

        assertThat(sessions(plain)).isEmpty()
    }

    @Test
    fun `a pad with a coloured light reports it and a pad without one does not`() {
        val withLed = rig()
        ledPad(withLed)
        val without = rig()
        ledPad(without, Hardware.lightsManager(listOf(Hardware.plainLight(2))))

        val flag = MoonBridge.LI_CCAP_RGB_LED.toInt()
        assertThat(withLed.connection.arrivals.single().capabilities and flag).isEqualTo(flag)
        assertThat(without.connection.arrivals.single().capabilities and flag).isEqualTo(0)
    }

    @Test
    fun `a colour for another controller number reaches no light`() {
        val rig = rig()
        ledPad(rig)

        setLed(rig, 1, 2, 3, controller = 1)

        assertThat(sessions()).isEmpty()
    }

    @Test
    fun `the colour reaches the pad of its controller number only`() {
        val other = Hardware.lightsManager(listOf(Hardware.rgbLight(7)))
        val rig = rig { multiController = true }
        ledPad(rig, lights, id = 5)
        ledPad(rig, other, id = 6)

        setLed(rig, 1, 2, 3, controller = 1)

        assertThat(sessions()).isEmpty()
        assertThat(sessions(other).single().requests.single())
            .containsExactly(LightColor(7, argb(1, 2, 3)))
    }

    @Test
    fun `colours that arrive while the thread is busy collapse into the last one`() {
        val rig = rig()
        ledPad(rig)
        val release = rig.holdBackground()

        rig.led(1, 2, 3)
        rig.led(4, 5, 6)
        rig.led(7, 8, 9)
        release()
        rig.awaitBackground()

        assertThat(sessions().single().requests)
            .containsExactly(listOf(LightColor(1, argb(7, 8, 9))))
    }

    @Test
    fun `a request that finds no coloured light left opens no session`() {
        val rig = rig()
        ledPad(rig)
        Hardware.lights(lights).deviceLights = emptyList()

        setLed(rig, 1, 2, 3)

        assertThat(sessions()).isEmpty()
    }

    @Test
    fun `a stopped handler ignores the colour`() {
        val rig = rig()
        ledPad(rig)
        rig.handler.stop()

        setLed(rig, 1, 2, 3)

        assertThat(sessions()).isEmpty()
    }

    @Test
    fun `stopping the handler drops a colour that was still waiting`() {
        val rig = rig()
        ledPad(rig)
        val release = rig.holdBackground()
        rig.led(1, 2, 3)

        rig.handler.stop()
        release()
        rig.awaitBackground()

        assertThat(sessions()).isEmpty()
    }

    @Test
    fun `stopping the handler closes the session`() {
        val rig = rig()
        ledPad(rig)
        setLed(rig, 1, 2, 3)

        rig.handler.stop()

        assertThat(sessions().single().closed).isTrue()
    }

    @Test
    fun `a pad that goes away closes its session`() {
        val rig = rig()
        val pad = ledPad(rig)
        setLed(rig, 1, 2, 3)

        rig.handler.onInputDeviceRemoved(pad.id)

        assertThat(sessions().single().closed).isTrue()
    }

    @Test
    fun `a session that fails to close is logged and the rest of the teardown goes on`() {
        val battery =
            Hardware.battery(
                status = android.hardware.BatteryState.STATUS_DISCHARGING,
                capacity = 0.5f,
            )
        val rig = rig()
        val pad =
            rig.connectPad(
                features = DeviceFeatures(lightsManager = lights, batteryState = battery)
            )
        rig.wake(pad)
        rig.awaitBackground()
        setLed(rig, 1, 2, 3)
        Hardware.session(Hardware.lights(lights).sessions.single()).failOnClose =
            IllegalStateException("closed twice")
        val log = LimeLogCapture().start()
        log.use { rig.handler.stop() }
        Hardware.batteryState(battery).batteryCapacity = 0.25f
        rig.advanceBackgroundBy(120_000)
        rig.awaitBackground()

        assertThat(log.entries)
            .contains(Level.WARNING to "Error closing LightsSession: closed twice")
        assertThat(ShadowFeedbackBridge.batteryEvents).hasSize(1)
    }

    @Test
    fun `the pad without a session has nothing to close`() {
        val rig = rig()
        ledPad(rig)

        rig.handler.stop()

        assertThat(sessions()).isEmpty()
    }
}
