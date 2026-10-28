package io.github.magalz.borealis.input

import android.os.Looper
import android.view.KeyEvent.KEYCODE_BUTTON_SELECT
import android.view.KeyEvent.KEYCODE_BUTTON_START
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Stopping the handler ends all the work it scheduled, for every context. */
@RunWith(RobolectricTestRunner::class)
class HandlerStopTest {
    @get:Rule val rig = ControllerHandlerRig()

    @Test
    fun `stopping cancels the chord check of the default context`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(50)
        rig.press(KEYCODE_BUTTON_SELECT)

        rig.handler.stop()
        rig.advanceBy(5_000)

        assertThat(rig.chords).isEqualTo(0)
    }

    @Test
    fun `stopping cancels the mouse emulation a Start hold turned on for the default context`() {
        rig.press(KEYCODE_BUTTON_START)
        rig.advanceBy(900)
        rig.release(KEYCODE_BUTTON_START)

        rig.handler.stop()

        assertThat(shadowOf(Looper.getMainLooper()).nextScheduledTaskTime).isEqualTo(Duration.ZERO)
    }
}
