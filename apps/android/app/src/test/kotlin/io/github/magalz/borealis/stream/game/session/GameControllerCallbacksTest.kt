package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.stream.game.GameTestBase
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/**
 * What the host asks of the gamepads and of the decoder while it streams: the rumble, the motion
 * sensors, the LED of a pad and the HDR mode. The native library calls the activity, which only
 * hands each request on, to the controller handler or to the renderer, so these tests pin the
 * routing and the values that cross it; the handler and the renderer have their own tests.
 */
internal class GameControllerCallbacksTest : GameTestBase() {
    private val handler: ControllerHandler
        get() = rig.field("controllerHandler")

    private fun usbPad(id: Int = 1): RecordingPad =
        RecordingPad(id, handler).also { handler.deviceAdded(it) }

    private fun hdrMetadata(): ByteArray? =
        ReflectionHelpers.getField(rig.renderer, "currentHdrMetadata")

    private val metadata = ByteArray(24) { (it + 1).toByte() }

    // The rumble

    @Test
    fun `the rumble of the host reaches the pad with its two motors`() {
        rig.create()
        val pad = usbPad()

        rig.game.rumble(0, 0x1234, 0x5678)

        assertThat(pad.rumbles).containsExactly(0x1234.toShort() to 0x5678.toShort())
    }

    @Test
    fun `the strength of a motor keeps its unsigned value across the signed short`() {
        rig.create()
        val pad = usbPad()

        rig.game.rumble(0, 0xFFFF.toShort(), 0x8000.toShort())

        assertThat(pad.rumbles).containsExactly((-1).toShort() to Short.MIN_VALUE)
    }

    @Test
    fun `the rumble of another controller number reaches no pad`() {
        rig.create()
        val pad = usbPad()

        rig.game.rumble(3, 0x1234, 0x5678)

        assertThat(pad.rumbles).isEmpty()
    }

    @Test
    fun `the rumble of the triggers reaches the pad with its two triggers`() {
        rig.create()
        val pad = usbPad()

        rig.game.rumbleTriggers(0, 0x1111, 0x2222)

        assertThat(pad.triggerRumbles).containsExactly(0x1111.toShort() to 0x2222.toShort())
        assertThat(pad.rumbles).isEmpty()
    }

    @Test
    fun `the rumble of the triggers of another controller number reaches no pad`() {
        rig.create()
        val pad = usbPad()

        rig.game.rumbleTriggers(3, 0x1111, 0x2222)

        assertThat(pad.triggerRumbles).isEmpty()
    }

    // The motion sensors

    @Test
    fun `the host asks for the accelerometer of a pad at a rate`() {
        rig.create()
        val pad = KnownPad(handler, 1)

        rig.game.setMotionEventState(1, MoonBridge.LI_MOTION_TYPE_ACCEL, 100)

        assertThat(pad.accelReportRateHz).isEqualTo(100.toShort())
        assertThat(pad.gyroReportRateHz).isEqualTo(0.toShort())
    }

    @Test
    fun `the host asks for the gyroscope of a pad at a rate`() {
        rig.create()
        val pad = KnownPad(handler, 1)

        rig.game.setMotionEventState(1, MoonBridge.LI_MOTION_TYPE_GYRO, 60)

        assertThat(pad.gyroReportRateHz).isEqualTo(60.toShort())
        assertThat(pad.accelReportRateHz).isEqualTo(0.toShort())
    }

    @Test
    fun `the motion state of another controller number reaches no pad`() {
        rig.create()
        val pad = KnownPad(handler, 1)

        rig.game.setMotionEventState(2, MoonBridge.LI_MOTION_TYPE_ACCEL, 100)

        assertThat(pad.accelReportRateHz).isEqualTo(0.toShort())
    }

    // The LED

    @Test
    fun `the colour of the LED of the host reaches the pad as one opaque value`() {
        rig.create()
        val pad = KnownPad(handler, 1)

        rig.game.setControllerLED(1, 0x11, 0x22, 0x33)

        assertThat(pad.awaitLed()).isTrue()
        assertThat(pad.ledArgbValue).isEqualTo(0xFF112233.toInt())
    }

    @Test
    fun `the colour of the LED keeps the unsigned value of each component`() {
        rig.create()
        val pad = KnownPad(handler, 1)

        rig.game.setControllerLED(1, 0xFF.toByte(), 0x80.toByte(), 0x01)

        assertThat(pad.awaitLed()).isTrue()
        assertThat(pad.ledArgbValue).isEqualTo(0xFFFF8001.toInt())
    }

    @Test
    fun `the colour of the LED of another controller number reaches no pad`() {
        rig.create()
        val pad = KnownPad(handler, 1)

        rig.game.setControllerLED(2, 0x11, 0x22, 0x33)

        assertThat(pad.ledArgbValue).isEqualTo(0)
    }

    // The HDR mode

    @Test
    fun `the HDR mode of the host reaches the renderer with its metadata`() {
        rig.create()

        rig.game.setHdrMode(true, metadata)

        assertThat(hdrMetadata()).isEqualTo(metadata)
    }

    @Test
    fun `HDR that the host turns off takes the metadata away`() {
        rig.create()
        rig.game.setHdrMode(true, metadata)

        rig.game.setHdrMode(false, null)

        assertThat(hdrMetadata()).isNull()
    }

    @Test
    fun `HDR on with no metadata takes the metadata away too`() {
        rig.create()
        rig.game.setHdrMode(true, metadata)

        rig.game.setHdrMode(true, null)

        assertThat(hdrMetadata()).isNull()
    }

    @Test
    fun `HDR on with no metadata, when there was none, is no failure`() {
        rig.create()

        rig.game.setHdrMode(true, null)

        assertThat(hdrMetadata()).isNull()
    }

    @Test
    fun `HDR that is off keeps no metadata even when the host sends some`() {
        rig.create()

        rig.game.setHdrMode(false, metadata)

        assertThat(hdrMetadata()).isNull()
    }
}
