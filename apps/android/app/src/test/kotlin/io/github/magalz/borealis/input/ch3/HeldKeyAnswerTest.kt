package io.github.magalz.borealis.input.ch3

import android.view.KeyEvent.ACTION_DOWN
import android.view.KeyEvent.KEYCODE_BUTTON_A
import android.view.KeyEvent.KEYCODE_BUTTON_C
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.input.events.EventsRig
import io.github.magalz.borealis.input.events.KeyDetail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What `handleButtonDown` answers for a key that went down before the chrome gave the input back:
 * the handler takes it, whatever the key is, and sends nothing. `HeldThroughCaptureTest` has what
 * such a key does to the pad; the answer is what keeps the system from also acting on it.
 */
@RunWith(RobolectricTestRunner::class)
class HeldKeyAnswerTest {
    @get:Rule val rig = EventsRig()
    private val pad = rig.addPad(7)

    private fun repeatOfAKeyHeldThroughTheCapture(keyCode: Int): Boolean {
        val downTime = rig.now
        rig.handler.setCaptured(true)
        rig.advanceBy(1_100)
        rig.handler.setCaptured(false)
        return rig.handler.handleButtonDown(
            pad.keyEvent(downTime, rig.now, ACTION_DOWN, keyCode, KeyDetail(repeat = 1))
        )
    }

    @Test
    fun `a button held through the capture is taken and sends nothing`() {
        assertThat(repeatOfAKeyHeldThroughTheCapture(KEYCODE_BUTTON_A)).isTrue()

        // The capture sent its neutral packets; the held key sent none of its own
        assertThat(rig.packets.none { it.buttons != 0 }).isTrue()
    }

    @Test
    fun `a key the handler does not know is taken too when it was held through the capture`() {
        assertThat(repeatOfAKeyHeldThroughTheCapture(KEYCODE_BUTTON_C)).isTrue()

        // A fresh press of the same key is left to the system
        assertThat(pad.press(KEYCODE_BUTTON_C)).isFalse()
    }
}
