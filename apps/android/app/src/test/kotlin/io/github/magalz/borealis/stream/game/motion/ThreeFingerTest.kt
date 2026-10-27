package io.github.magalz.borealis.stream.game.motion

import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.session
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.util.ReflectionHelpers

/**
 * The gesture of three fingers: it cancels the touches of the first two fingers, and a tap of three
 * fingers that is over within 300 milliseconds opens the keyboard of the stream.
 */
internal class ThreeFingerTest : MotionTestBase() {
    private val three = listOf(10f to 10f, 20f to 20f, 30f to 30f)

    private fun thirdFingerDown(at: Long) =
        fingerEvent(MotionEvent.ACTION_POINTER_DOWN, three, index = 2) { eventTime = at }

    private fun lastFingerUp(at: Long, flags: Int = 0) =
        fingerEvent(MotionEvent.ACTION_UP, listOf(30f to 30f)) {
            eventTime = at
            this.flags = flags
        }

    private fun panel(): Panel = (rig.phase as StreamPhase.Streaming).panel

    private fun touch(event: MotionEvent) = rig.game.onTouch(backgroundView, event)

    // ----- The tap opens the keyboard -----

    @Test
    fun `a tap of three fingers opens the keyboard of the stream`() {
        streaming()

        assertThat(touch(thirdFingerDown(TIME))).isTrue()
        assertThat(panel()).isEqualTo(Panel.NONE)
        assertThat(touch(lastFingerUp(TIME + 100))).isTrue()

        assertThat(panel()).isEqualTo(Panel.KEYBOARD)
        assertThat(sent()).isEmpty()
    }

    @Test
    fun `the keyboard that opens is the one of Borealis, not the one of Android`() {
        streaming()

        touch(thirdFingerDown(TIME))
        touch(lastFingerUp(TIME + 100))

        val input = rig.application.getSystemService(InputMethodManager::class.java)
        assertThat(shadowOf(input).isSoftInputVisible).isFalse()
        assertThat(panel()).isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `a tap of three fingers hides the keyboard of Android when it shows, and opens nothing`() {
        streaming()
        val decor = rig.game.window.decorView
        val input = rig.application.getSystemService(InputMethodManager::class.java)
        input.showSoftInput(decor, 0)
        reportImeVisible(decor)
        assertThat(shadowOf(input).isSoftInputVisible).isTrue()

        touch(thirdFingerDown(TIME))
        touch(lastFingerUp(TIME + 100))

        assertThat(shadowOf(input).isSoftInputVisible).isFalse()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    /** What the window insets say once the keyboard of Android is on screen. */
    private fun reportImeVisible(decor: View) {
        val insets = WindowInsets.Builder().setVisible(WindowInsets.Type.ime(), true).build()
        val attachInfo = ReflectionHelpers.getField<Any>(decor, "mAttachInfo")
        val root = ReflectionHelpers.getField<Any>(attachInfo, "mViewRootImpl")
        ReflectionHelpers.setField(root, "mLastWindowInsets", insets)
    }

    @Test
    fun `a tap is over within 300 milliseconds, and one that lasts 300 is a lift like any other`() {
        streaming()

        touch(thirdFingerDown(TIME))
        touch(lastFingerUp(TIME + 300))
        assertThat(panel()).isEqualTo(Panel.NONE)

        touch(thirdFingerDown(TIME + 1000))
        touch(lastFingerUp(TIME + 1299))
        assertThat(panel()).isEqualTo(Panel.KEYBOARD)
    }

    @Test
    fun `a lift of a single finger that has not followed three fingers opens nothing`() {
        streaming()

        touch(fingerEvent(MotionEvent.ACTION_DOWN, listOf(30f to 30f)) { eventTime = TIME })
        touch(lastFingerUp(TIME + 100))

        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `the lift of a finger while others stay is not the end of the tap`() {
        streaming()
        touch(thirdFingerDown(TIME))

        val liftOfTwoFingers =
            fingerEvent(MotionEvent.ACTION_POINTER_UP, listOf(10f to 10f, 20f to 20f), index = 0) {
                eventTime = TIME + 50
            }
        assertThat(touch(liftOfTwoFingers)).isTrue()

        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a lift that Android cancelled is not a tap`() {
        streaming()
        touch(thirdFingerDown(TIME))

        assertThat(touch(lastFingerUp(TIME + 100, MotionEvent.FLAG_CANCELED))).isTrue()

        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    @Test
    fun `a touch screen that is off does not take the gesture`() {
        streaming()
        rig.session.setTouchMode(TouchMode.OFF)

        touch(thirdFingerDown(TIME))
        touch(lastFingerUp(TIME + 100))

        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    // ----- The first two touches are cancelled -----

    @Test
    fun `the third finger cancels the touches of the first two, and is not a touch itself`() {
        streaming()
        rig.session.setTouchMode(TouchMode.DIRECT)
        touch(fingerEvent(MotionEvent.ACTION_DOWN, listOf(10f to 200f)) { eventTime = TIME })
        idle(100)
        assertThat(sent())
            .containsExactly(position(10, 55), press(MouseButtonPacket.BUTTON_LEFT))
            .inOrder()
        forgetSent()

        assertThat(touch(thirdFingerDown(TIME + 200))).isTrue()

        // The click of the first finger ends; nothing starts for the third.
        assertThat(sent()).containsExactly(release(MouseButtonPacket.BUTTON_LEFT))
    }

    @Test
    fun `a gesture of four fingers is not the gesture of three`() {
        streaming()
        val four = three + (40f to 40f)

        val handled =
            touch(
                fingerEvent(MotionEvent.ACTION_POINTER_DOWN, four, index = 3) { eventTime = TIME }
            )
        touch(lastFingerUp(TIME + 100))

        assertThat(handled).isFalse()
        assertThat(panel()).isEqualTo(Panel.NONE)
    }

    private companion object {
        const val TIME = 10_000L
    }
}
