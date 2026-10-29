package io.github.magalz.borealis.stream.game.session

import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.ui.StreamView
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.RecordingSurface
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.recordSession
import io.github.magalz.borealis.stream.game.session
import io.github.magalz.borealis.stream.host.StreamChromeHost
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * The copy of the last frame of the stream that the error popup stands on (the owner's decision of
 * 2026-10-29). The decoder is released a fraction of a second after the connection reports the end,
 * so the activity asks the chrome host for the copy at the top of `connectionTerminated` and
 * `stageFailed`, before the network test that takes seconds and before the connection stops, and
 * only for a stream that showed its first frame and did not end by the user's own Quit or
 * gracefully. The host lets the copy go when the activity is destroyed.
 */
internal class GameFrozenFrameTest : GameTestBase() {
    /** The frozen frame of the chrome host, which is internal to its module: read by name. */
    private fun frozen(): Any =
        ReflectionHelpers.getField(rig.field<StreamChromeHost>("chromeHost"), "frozenFrame")

    private fun Any.requested(): Boolean =
        ReflectionHelpers.callInstanceMethod(this, "getRequested")

    private fun Any.frame(): Any? = ReflectionHelpers.callInstanceMethod(this, "getFrame")

    /** A stream view with a surface and a size, which is what the copy needs of it. */
    private fun withVideo() {
        val view = rig.game.findViewById<StreamView>(R.id.surfaceView)
        useSurface(view, RecordingSurface())
        view.layout(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT)
    }

    private fun streamingWithVideo() {
        rig.streaming()
        withVideo()
    }

    // When the copy is asked for

    @Test
    fun `a stream that ends in error after its first frame is copied`() {
        streamingWithVideo()

        rig.game.connectionTerminated(-5)
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        assertThat(frozen().requested()).isTrue()
        assertThat(frozen().frame()).isNotNull()
        assertThat(rig.phase).isInstanceOf(StreamPhase.Failed::class.java)
    }

    @Test
    fun `the copy is asked for before the connection is stopped`() {
        streamingWithVideo()
        var askedAtTheStop: Boolean? = null
        ShadowRecordingBridge.onInterrupt = { askedAtTheStop = frozen().requested() }

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(ShadowRecordingBridge.names).contains("interruptConnection")
        assertThat(askedAtTheStop).isTrue()
    }

    @Test
    fun `the copy is asked for before the connectivity test of the end of the stream`() {
        streamingWithVideo()
        var askedAtTheTest: Boolean? = null
        ShadowRecordingBridge.onConnectivityTest = { askedAtTheTest = frozen().requested() }

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(ShadowRecordingBridge.named("testClientConnectivity")).hasSize(1)
        assertThat(askedAtTheTest).isTrue()
    }

    @Test
    fun `the copy is asked for before the connectivity test of a stage that fails`() {
        streamingWithVideo()
        var askedAtTheTest: Boolean? = null
        ShadowRecordingBridge.onConnectivityTest = { askedAtTheTest = frozen().requested() }

        rig.game.stageFailed("Control connection", 0, -5)
        ShadowLooper.idleMainLooper()

        assertThat(ShadowRecordingBridge.named("testClientConnectivity")).hasSize(1)
        assertThat(askedAtTheTest).isTrue()
    }

    @Test
    fun `the copy is asked for on the thread that reports the end, before the main thread acts`() {
        streamingWithVideo()

        onConnectionThread { rig.game.connectionTerminated(-5) }

        // The main thread has not run what the callback posted: nothing is stopped, nothing shows.
        assertThat(ShadowRecordingBridge.names).doesNotContain("interruptConnection")
        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat(frozen().requested()).isTrue()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()
    }

    @Test
    fun `a stage that fails after the first frame is copied`() {
        streamingWithVideo()

        rig.game.stageFailed("Control connection", 0, -5)
        ShadowLooper.idleMainLooper()

        assertThat(frozen().requested()).isTrue()
    }

    @Test
    fun `a stage that fails reports from the connection thread and is copied there`() {
        streamingWithVideo()

        onConnectionThread { rig.game.stageFailed("Control connection", 0, -5) }

        assertThat(frozen().requested()).isTrue()
        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        ShadowLooper.idleMainLooper()
    }

    // When it is not

    @Test
    fun `a stream that ends before its first frame is not copied`() {
        rig.create()
        withVideo()
        rig.game.connectionStarted()

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(frozen().requested()).isFalse()
    }

    @Test
    fun `the watchdog of the first frame does not copy`() {
        rig.create()
        withVideo()
        rig.game.connectionStarted()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_FRAME)
        rig.awaitStop()

        assertThat(frozen().requested()).isFalse()
    }

    @Test
    fun `a stage that fails before the first frame is not copied`() {
        rig.create()
        withVideo()

        rig.game.stageFailed("Control connection", 0, -5)

        assertThat(frozen().requested()).isFalse()
    }

    @Test
    fun `the end that the user's own Quit caused is not copied`() {
        streamingWithVideo()
        rig.recordSession()
        rig.session.quitApp()

        rig.game.connectionTerminated(ENET_DISCONNECT)
        rig.awaitStop()

        assertThat(frozen().requested()).isFalse()
    }

    @Test
    fun `another end while a quit is under way is a network drop and is copied`() {
        streamingWithVideo()
        rig.recordSession()
        rig.session.quitApp()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        rig.awaitStop()

        assertThat(frozen().requested()).isTrue()
    }

    @Test
    fun `a graceful end is not copied`() {
        streamingWithVideo()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()

        assertThat(frozen().requested()).isFalse()
    }

    // The copy goes with the activity

    @Test
    fun `the copy is let go of when the activity is destroyed`() {
        streamingWithVideo()
        rig.game.connectionTerminated(-5)
        ShadowLooper.idleMainLooper()
        rig.awaitStop()
        val copy = frozen()
        assertThat(copy.frame()).isNotNull()

        rig.destroyActivity()

        assertThat(copy.frame()).isNull()
    }

    @Test
    fun `the copy stays while the activity only stops`() {
        streamingWithVideo()
        rig.game.connectionTerminated(-5)
        ShadowLooper.idleMainLooper()
        rig.awaitStop()
        val copy = frozen()

        rig.stop()

        assertThat(copy.frame()).isNotNull()
    }

    private companion object {
        const val VIDEO_WIDTH = 1280
        const val VIDEO_HEIGHT = 720
        const val ENET_DISCONNECT = -1
    }
}
