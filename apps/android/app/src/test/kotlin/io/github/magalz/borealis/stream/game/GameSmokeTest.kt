package io.github.magalz.borealis.stream.game

import android.view.KeyEvent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.StreamPhase
import org.junit.Test

/**
 * One check for each area that a later characterization covers (keys, motion, the stream session):
 * the rig can drive the activity in the streaming state, and what it does reaches the native
 * library or the session.
 */
internal class GameSmokeTest : GameTestBase() {
    @Test
    fun `a key of a hardware keyboard reaches the host as a key press`() {
        rig.streaming()

        val handled =
            rig.game.dispatchKeyEvent(keyboardEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A))

        assertThat(handled).isTrue()
        val call = ShadowRecordingBridge.named("sendKeyboardInput").single()
        // The host takes Windows virtual keys; the translator marks them with 0x8000: VK_A.
        assertThat(call.args[0]).isEqualTo(0x8041.toShort())
        assertThat(call.args[1]).isEqualTo(KeyboardPacket.KEY_DOWN)
    }

    @Test
    fun `a mouse press on the stream view reaches the host as a click at the pointer`() {
        rig.streaming()
        val view = rig.game.findViewById<android.view.View>(R.id.surfaceView)

        val handled =
            rig.game.onGenericMotion(view, mousePress(MotionEvent.BUTTON_PRIMARY, 10f, 20f))

        assertThat(handled).isTrue()
        val position = ShadowRecordingBridge.named("sendMousePosition").single()
        assertThat(position.args.take(2)).containsExactly(10.toShort(), 20.toShort()).inOrder()
        val button = ShadowRecordingBridge.named("sendMouseButton").single()
        assertThat(button.args)
            .containsExactly(MouseButtonPacket.PRESS_EVENT, MouseButtonPacket.BUTTON_LEFT)
            .inOrder()
    }

    @Test
    fun `a connection that ends in error reaches the session as a failure`() {
        rig.streaming()
        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)

        assertThat(rig.phase).isInstanceOf(StreamPhase.Failed::class.java)
    }

    @Test
    fun `a stage that fails reaches the session as a failure`() {
        rig.create()
        assertThat(rig.phase).isInstanceOf(StreamPhase.Starting::class.java)

        rig.game.stageFailed("RTSP handshake", 0, -1)

        assertThat(rig.phase).isInstanceOf(StreamPhase.Failed::class.java)
    }
}
