package io.github.magalz.borealis.input

import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.ControllerPacket.BACK_FLAG
import com.limelight.nvstream.input.ControllerPacket.PLAY_FLAG
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowToast

/** Holding Start turns the gamepad mouse on or off, on a pad on the USB driver as on any other. */
@RunWith(RobolectricTestRunner::class)
class UsbStartHoldTest {
    private val rig = ControllerHandlerRig()
    private val pad = FakeUsbController(1, rig.handler).also { rig.handler.deviceAdded(it) }

    @Test
    fun `holding Start for a while and letting go turns the mouse on`() {
        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()

        pad.report(0)

        assertThat(rig.handler.isMouseEmulationEnabled).isTrue()
    }

    @Test
    fun `holding Start again turns the mouse off`() {
        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        pad.report(0)
        assertThat(rig.handler.isMouseEmulationEnabled).isTrue()

        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        pad.report(0)

        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()
    }

    @Test
    fun `a short press of Start leaves the mouse alone`() {
        pad.report(PLAY_FLAG)
        rig.advanceBy(300)
        pad.report(0)

        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()
    }

    @Test
    fun `the Start of the menu chord does not turn the mouse on`() {
        pad.report(PLAY_FLAG or BACK_FLAG)
        rig.advanceBy(1_500)
        pad.report(BACK_FLAG)
        pad.report(0)

        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()
    }

    @Test
    fun `Start held while the chrome has the input does not turn the mouse on`() {
        rig.handler.setCaptured(true)

        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        pad.report(0)

        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()
    }

    @Test
    fun `it does nothing when mouse emulation is off in the settings`() {
        rig.prefs.mouseEmulation = false

        pad.report(PLAY_FLAG)
        rig.advanceBy(800)
        pad.report(0)

        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()
    }

    /** The USB driver reports from its own thread, which has no looper. */
    private fun onDriverThread(block: () -> Unit): Throwable? {
        var failure: Throwable? = null
        Thread {
                try {
                    block()
                } catch (e: Throwable) {
                    failure = e
                }
            }
            .apply {
                start()
                join()
            }
        return failure
    }

    @Test
    fun `the toggle from the driver thread runs on the main thread, where the notice is shown`() {
        assertThat(onDriverThread { pad.report(PLAY_FLAG) }).isNull()
        rig.advanceBy(800)

        assertThat(onDriverThread { pad.report(0) }).isNull()

        // Nothing changes until the main thread has had its turn.
        assertThat(rig.handler.isMouseEmulationEnabled).isFalse()
        rig.advanceBy(0)
        assertThat(rig.handler.isMouseEmulationEnabled).isTrue()
        assertThat(ShadowToast.shownToastCount()).isEqualTo(1)
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Mouse emulation is: ON")
    }
}
