package io.github.magalz.borealis.stream.game

import android.view.Surface
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import org.junit.Assert.assertThrows
import org.junit.Test
import org.robolectric.util.ReflectionHelpers

/** The surface callbacks of `Game` that do not start the connection. */
internal class GameSurfaceTest : GameTestBase() {
    private val holder = RecordingSurfaceHolder()

    private fun stopping(): Boolean = ReflectionHelpers.getField(rig.renderer, "stopping")

    @Test
    fun `a surface that changes before it was created is a failure`() {
        rig.create()

        val failure =
            assertThrows(IllegalStateException::class.java) {
                rig.game.surfaceChanged(holder, 0, 1280, 720)
            }

        assertThat(failure).hasMessageThat().isEqualTo("Surface changed before creation!")
    }

    @Test
    fun `a surface that is destroyed before it was created is a failure`() {
        rig.create()

        val failure =
            assertThrows(IllegalStateException::class.java) { rig.game.surfaceDestroyed(holder) }

        assertThat(failure).hasMessageThat().isEqualTo("Surface destroyed before creation!")
    }

    @Test
    fun `a created surface is asked for the frame rate of the stream, fixed source, always`() {
        rig.create()

        rig.game.surfaceCreated(holder)

        assertThat(holder.recordingSurface.frameRates)
            .containsExactly(
                FrameRateRequest(
                    60f,
                    Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
                    Surface.CHANGE_FRAME_RATE_ALWAYS,
                )
            )
        assertThat(rig.field<Boolean>("surfaceCreated")).isTrue()
    }

    @Test
    fun `a created surface is asked for the refresh rate that the display picked`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "30")
        rig.displayModes(DisplayMode(0, 1920, 1080, 60f), DisplayMode(2, 1920, 1080, 90f))
        rig.create()

        rig.game.surfaceCreated(holder)

        // 30 frames divide 90 hertz evenly, and latency pacing takes the highest rate that does.
        assertThat(holder.recordingSurface.frameRates.map { it.frameRate }).containsExactly(90f)
    }

    @Test
    fun `a created surface is asked for the frame rate when the display cannot keep up`() {
        rig.preference("list_fps", "90")
        rig.create()

        rig.game.surfaceCreated(holder)

        assertThat(holder.recordingSurface.frameRates.map { it.frameRate }).containsExactly(90f)
    }

    @Test
    fun `a created surface is asked for the frame rate when the refresh rate may be reduced`() {
        rig.preference("list_resolution", "1920x1080")
        rig.preference("list_fps", "30")
        rig.preference("frame_pacing", "balanced")
        rig.preference("checkbox_reduce_refresh_rate", true)
        rig.displayModes(DisplayMode(0, 1920, 1080, 60f), DisplayMode(2, 1920, 1080, 90f))
        rig.create()

        rig.game.surfaceCreated(holder)

        assertThat(holder.recordingSurface.frameRates.map { it.frameRate }).containsExactly(30f)
        assertThat(rig.field<Float>("desiredRefreshRate")).isEqualTo(60f)
    }

    @Test
    fun `a surface that goes away before any connection attempt stops nothing`() {
        rig.streaming()
        rig.game.surfaceCreated(holder)

        rig.game.surfaceDestroyed(holder)

        assertThat(stopping()).isFalse()
        assertThat(rig.field<Boolean>("connected")).isTrue()
        assertThat(ShadowRecordingBridge.names).isEmpty()
    }

    @Test
    fun `a surface that changes after the connection attempt does not start another`() {
        rig.create()
        rig.game.surfaceCreated(holder)
        rig.markConnectionAttempted()

        rig.game.surfaceChanged(holder, 0, 1280, 720)

        assertThat(ShadowRecordingBridge.names).isEmpty()
        assertThat(stopping()).isFalse()
    }

    @Test
    fun `a surface that goes away after the attempt makes the decoder ready to stop`() {
        rig.create()
        rig.game.surfaceCreated(holder)
        rig.markConnectionAttempted()

        rig.game.surfaceDestroyed(holder)

        assertThat(stopping()).isTrue()
        // The connection had not started, so there is nothing to stop.
        assertThat(ShadowRecordingBridge.names).isEmpty()
    }

    @Test
    fun `a surface that goes away under a running stream stops the connection`() {
        rig.streaming()
        rig.game.surfaceCreated(holder)
        rig.markConnectionAttempted()

        rig.game.surfaceDestroyed(holder)
        rig.awaitStop()

        assertThat(stopping()).isTrue()
        assertThat(rig.field<Boolean>("connected")).isFalse()
        assertThat(ShadowRecordingBridge.names)
            .containsExactly("interruptConnection", "stopConnection", "cleanupBridge")
            .inOrder()
    }
}
