package io.github.magalz.borealis.stream.game.motion

import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamNotice
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.TouchMode
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.session
import org.junit.Rule
import org.junit.Test

private const val REFUSED_LINE = "the host takes no touch events, the touch mode is direct"

/**
 * A host that does not take touch events (the answer is not supported, as when `native_pen_touch`
 * is off): the first touch that it refuses puts the session in the Direct mode for the rest of the
 * stream, tells the viewer once and goes on through the Direct touch contexts, so the finger that
 * was refused is not lost. Native picked again from the menu is Direct again, with the notice and
 * without asking the host.
 */
internal class NativeTouchRefusedTest : MotionTestBase() {
    @get:Rule val log = RecordingLog()

    private val left = MouseButtonPacket.BUTTON_LEFT

    private fun native() = rig.session.setTouchMode(TouchMode.NATIVE)

    private fun hostWithoutTouch() {
        ShadowRecordingBridge.intResult = MoonBridge.LI_ERR_UNSUPPORTED
    }

    private fun down(x: Float = 100.9f, y: Float = 50.7f, at: Long = TIME) =
        finger(MotionEvent.ACTION_DOWN, x, y, at)

    private fun up(x: Float = 100.9f, y: Float = 50.7f, at: Long = TIME + 50) =
        finger(MotionEvent.ACTION_UP, x, y, at)

    private fun touchMode() = (rig.phase as StreamPhase.Streaming).touchMode

    // How often the host was asked for a touch.
    private fun asked() = ShadowRecordingBridge.named("sendTouchEvent").size

    // What reached the host as a mouse.
    private fun mouseCalls() = sent().filterNot { it.name == "sendTouchEvent" }

    private fun refusalLines() = log.records.filter { it.message == REFUSED_LINE }

    /** The notices that the session has now, each one shown and gone, in order. */
    private fun notices(): List<StreamNotice> {
        val given = mutableListOf<StreamNotice>()
        while (true) {
            val next = rig.session.notice.value ?: return given
            given += next
            rig.session.noticeShown(next)
        }
    }

    // ----- The refusal -----

    @Test
    fun `a host that takes no touch puts the session in Direct, tells the viewer once and logs it once`() {
        hostWithoutTouch()
        streaming()
        native()

        assertThat(touch(streamView, down())).isTrue()

        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
        assertThat(notices()).containsExactly(StreamNotice.NativeTouchRefused)
        assertThat(refusalLines()).hasSize(1)
        assertThat(refusalLines().single().level).isEqualTo(LogLevel.INFO)
    }

    @Test
    fun `the host is asked once, and what follows is Direct touch with nothing more to tell or log`() {
        hostWithoutTouch()
        streaming()
        native()
        touch(streamView, down())
        notices()

        touch(streamView, up())
        touch(streamView, finger(MotionEvent.ACTION_DOWN, 20f, 20f, TIME + 1000))
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 30f, 20f, TIME + 1010))
        touch(streamView, finger(MotionEvent.ACTION_CANCEL, 30f, 20f, TIME + 1020))

        assertThat(asked()).isEqualTo(1)
        assertThat(notices()).isEmpty()
        assertThat(refusalLines()).hasSize(1)
        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
    }

    @Test
    fun `the finger that the host refused goes on through the Direct contexts, a tap is a click`() {
        hostWithoutTouch()
        streaming()
        native()

        assertThat(touch(streamView, down())).isTrue()
        // The host was asked, and the touch is not a mouse until the finger lifts.
        assertThat(asked()).isEqualTo(1)
        assertThat(mouseCalls()).isEmpty()
        assertThat(touch(streamView, up())).isTrue()

        assertThat(mouseCalls()).containsExactly(position(100, 50), press(left)).inOrder()
        forgetSent()
        idle(100)
        assertThat(sent()).containsExactly(release(left))
    }

    @Test
    fun `a host that refuses a move after taking the touch down puts the session in Direct`() {
        streaming()
        native()
        touch(streamView, down())
        assertThat(touchMode()).isEqualTo(TouchMode.NATIVE)

        hostWithoutTouch()
        assertThat(touch(streamView, finger(MotionEvent.ACTION_MOVE, 110f, 50f, TIME + 10)))
            .isTrue()

        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
        assertThat(notices()).containsExactly(StreamNotice.NativeTouchRefused)
        assertThat(refusalLines()).hasSize(1)
    }

    @Test
    fun `a host that refuses the cancel of the gesture puts the session in Direct`() {
        streaming()
        native()
        touch(streamView, down())

        hostWithoutTouch()
        assertThat(touch(streamView, finger(MotionEvent.ACTION_CANCEL, 100f, 50f, TIME + 10)))
            .isTrue()

        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
        assertThat(notices()).containsExactly(StreamNotice.NativeTouchRefused)
        assertThat(refusalLines()).hasSize(1)
    }

    @Test
    fun `after the refusal a tap of three fingers opens the keyboard, as in Direct`() {
        hostWithoutTouch()
        streaming()
        native()
        touch(streamView, down())
        val three = listOf(10f to 10f, 20f to 20f, 30f to 30f)

        assertThat(
                touch(
                    streamView,
                    fingerEvent(MotionEvent.ACTION_POINTER_DOWN, three, index = 2) {
                        eventTime = TIME + 100
                    },
                )
            )
            .isTrue()
        assertThat(
                touch(
                    streamView,
                    fingerEvent(MotionEvent.ACTION_UP, listOf(30f to 30f)) {
                        eventTime = TIME + 200
                    },
                )
            )
            .isTrue()

        assertThat((rig.phase as StreamPhase.Streaming).panel).isEqualTo(Panel.KEYBOARD)
        assertThat(asked()).isEqualTo(1)
    }

    // ----- Nothing to cancel on a host that refused -----

    @Test
    fun `after the refusal the chrome taking the input sends the host no touch event`() {
        hostWithoutTouch()
        streaming()
        native()
        touch(streamView, down())

        rig.session.openMenu()

        assertThat(asked()).isEqualTo(1)
    }

    @Test
    fun `a host that took the touch down and refused the move is sent no cancel when the chrome takes the input`() {
        streaming()
        native()
        touch(streamView, down())
        hostWithoutTouch()
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 110f, 50f, TIME + 10))
        assertThat(asked()).isEqualTo(2)

        rig.session.openMenu()

        assertThat(asked()).isEqualTo(2)
    }

    @Test
    fun `a host that refuses the cancel of a mode change leaves the mode that was picked`() {
        streaming()
        native()
        touch(streamView, down())
        hostWithoutTouch()

        rig.session.setTouchMode(TouchMode.TRACKPAD)

        assertThat(touchMode()).isEqualTo(TouchMode.TRACKPAD)
        assertThat(notices()).isEmpty()
        assertThat(refusalLines()).isEmpty()
    }

    // ----- Only a refusal is a refusal -----

    @Test
    fun `a host that takes touch keeps the session in Native, with nothing to tell or log`() {
        streaming()
        native()

        touch(streamView, down())
        touch(streamView, finger(MotionEvent.ACTION_MOVE, 110f, 50f, TIME + 10))
        touch(streamView, up(at = TIME + 20))

        assertThat(asked()).isEqualTo(3)
        assertThat(touchMode()).isEqualTo(TouchMode.NATIVE)
        assertThat(notices()).isEmpty()
        assertThat(refusalLines()).isEmpty()
    }

    @Test
    fun `an action that is no touch of a finger is not a refusal of the host`() {
        streaming()
        native()

        assertThat(touch(streamView, finger(MotionEvent.ACTION_SCROLL, 100f, 50f))).isFalse()
        assertThat(touch(streamView, finger(MotionEvent.ACTION_OUTSIDE, 100f, 50f))).isFalse()

        assertThat(asked()).isEqualTo(0)
        assertThat(touchMode()).isEqualTo(TouchMode.NATIVE)
        assertThat(notices()).isEmpty()
        assertThat(refusalLines()).isEmpty()
    }

    // ----- For the rest of the stream -----

    @Test
    fun `Native picked again after the refusal leaves the session in Direct and tells the viewer again`() {
        hostWithoutTouch()
        streaming()
        native()
        touch(streamView, down())
        touch(streamView, up())
        notices()
        idle(100)

        // The menu shows Direct, whatever the viewer picks; the host's answer holds for the stream.
        native()

        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
        assertThat(notices()).containsExactly(StreamNotice.NativeTouchRefused)
        assertThat(asked()).isEqualTo(1)
        assertThat(refusalLines()).hasSize(1)
    }

    @Test
    fun `after Native picked again the touch is Direct, and the host is asked nothing more`() {
        hostWithoutTouch()
        streaming()
        native()
        touch(streamView, down())
        touch(streamView, up())
        notices()
        idle(100)
        native()
        notices()
        val before = mouseCalls().size

        touch(streamView, down(at = TIME + 1000))
        touch(streamView, up(at = TIME + 1050))

        assertThat(asked()).isEqualTo(1)
        assertThat(notices()).isEmpty()
        assertThat(mouseCalls().drop(before))
            .containsExactly(position(100, 50), press(left))
            .inOrder()
    }

    @Test
    fun `Native picked again after another mode is Direct with the notice each time, and Trackpad stays Trackpad`() {
        hostWithoutTouch()
        streaming()
        native()
        touch(streamView, down())
        touch(streamView, up())
        notices()

        rig.session.setTouchMode(TouchMode.TRACKPAD)
        assertThat(touchMode()).isEqualTo(TouchMode.TRACKPAD)
        assertThat(notices()).isEmpty()

        native()
        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
        assertThat(notices()).containsExactly(StreamNotice.NativeTouchRefused)

        native()
        assertThat(touchMode()).isEqualTo(TouchMode.DIRECT)
        assertThat(notices()).containsExactly(StreamNotice.NativeTouchRefused)
        assertThat(asked()).isEqualTo(1)
        assertThat(refusalLines()).hasSize(1)
    }
}
