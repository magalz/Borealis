package io.github.magalz.borealis.input.ch1

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import io.github.magalz.borealis.input.ControllerHandlerRig
import io.github.magalz.borealis.input.FakeUsbController
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A USB pad's Start hold that the chrome's capture cut short. The hold is timed from the report
 * that showed Start down, before the capture; the capture cuts the buttons the handler sees to
 * none, so the release inside it looks like the end of a long hold.
 */
@RunWith(RobolectricTestRunner::class)
class UsbStartHoldCaptureTest {
    @get:Rule val rig = ControllerHandlerRig()
    private val pad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }

    @Test
    fun `a Start held before the capture and let go during it does not turn the mouse on`() {
        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        rig.handler.setCaptured(true)

        pad.report(0)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }

    @Test
    fun `that release ends the hold, so the next report after the capture does not turn it on`() {
        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        rig.handler.setCaptured(true)
        pad.report(0)
        rig.handler.setCaptured(false)

        pad.report(0)

        assertThat(rig.handler.isMouseEmulationOnAnyPad).isFalse()
    }
}
