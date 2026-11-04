package io.github.magalz.borealis.stream.game.session

import android.content.SharedPreferences
import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.binding.video.CrashListener
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.RecordingSurfaceHolder
import io.github.magalz.borealis.stream.game.phase
import io.github.magalz.borealis.stream.game.recordSession
import io.github.magalz.borealis.stream.game.session
import kotlinx.coroutines.CompletableDeferred
import org.junit.Rule
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

private const val LOST_TEXT = "The video surface was lost"
private const val LOST_WARNING = "video surface lost while the stream is ending"

/**
 * The codec refused the surface of the video renderer, which stopped decoding: the picture is gone
 * and nothing else tells the stream (it was a frozen video before). The renderer tells its listener
 * from the thread that recovers the codec, while it holds the lock of the recovery, so the activity
 * only posts the report and acts on the main thread. A stream that runs ends with the connection
 * error screen and stops its connection, in picture in picture too: the stream keeps running there,
 * and the failure shows when it ends. A stream that is ending already, because the user left (the
 * surface went away, the activity stopped, a Disconnect, a finish) or because it failed, has
 * nothing more to say: a warning is all, in either order with the report. The audit says a stream
 * that ends this way as an error, unless the user's Quit is under way: that end is the quit's.
 */
internal class GameSurfaceLostTest : GameTestBase() {
    @get:Rule val log = RecordingLog()

    private val holder = RecordingSurfaceHolder()

    /** The renderer tells its listener from a thread of its own, as the recovery does. */
    private fun surfaceLost() {
        val listener = ReflectionHelpers.getField<CrashListener>(rig.renderer, "crashListener")
        onConnectionThread { listener.surfaceLost() }
    }

    private fun keepsScreenOn() =
        rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0

    private fun warnings() = log.records.filter { it.message == LOST_WARNING }

    private fun assertNoFailure() {
        assertThat(rig.phase).isNotInstanceOf(StreamPhase.Failed::class.java)
    }

    private fun assertWarned() {
        assertThat(warnings().map { it.level }).containsExactly(LogLevel.WARN)
    }

    @Test
    fun `the report only posts, so the renderer that holds its lock is never kept waiting`() {
        rig.streaming()

        surfaceLost()

        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat(ShadowRecordingBridge.named("stopConnection")).isEmpty()
    }

    @Test
    fun `a stream that runs ends with the error screen of a stream that stopped`() {
        rig.streaming()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Failed(StreamFailure(FailureKind.STOPPED, LOST_TEXT, null)))
        assertThat(warnings()).isEmpty()
    }

    @Test
    fun `a stream that runs stops its connection`() {
        rig.streaming()

        surfaceLost()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(1)
        assertThat(rig.field<Boolean>("stopRan")).isTrue()
    }

    @Test
    fun `a stream that runs is audited as an error end, not as a stop by the user`() {
        rig.streaming()
        rig.audit.clear()

        surfaceLost()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        // The stop of the connection that follows does not end the session a second time.
        val end = rig.audit.single()
        assertThat(end.type).isEqualTo(AuditType.SESSION_END)
        assertThat(end.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(end.detail).startsWith("reason=error")
    }

    @Test
    fun `a stream that the user left first keeps its audit as a stop by the user`() {
        rig.streaming()
        rig.audit.clear()
        rig.stop()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        val end = rig.audit.single()
        assertThat(end.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(end.detail).startsWith("reason=user")
    }

    @Test
    fun `a Quit under way that succeeds after the lost surface is audited as the quit`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, QuitResult.Quit)
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        surfaceLost()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()
        answer.complete(Unit)

        val end = rig.audit.single { it.type == AuditType.SESSION_END }
        assertThat(end.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(end.detail).startsWith("reason=quit duration_s=")
    }

    @Test
    fun `a Quit under way that fails after the lost surface still ends the session once`() {
        rig.streaming()
        val quit = SharedQuitRig(rig, QuitResult.Failed("Boom"))
        val answer = CompletableDeferred<Unit>()
        quit.apps.gate = answer
        quit.session.quitApp()

        surfaceLost()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()
        answer.complete(Unit)

        assertThat(rig.audit.filter { it.type == AuditType.SESSION_END }).hasSize(1)
    }

    @Test
    fun `a stream that runs lets the display sleep before it shows the failure`() {
        rig.streaming()
        assertThat(keepsScreenOn()).isTrue()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertThat(rig.phase).isInstanceOf(StreamPhase.Failed::class.java)
        assertThat(keepsScreenOn()).isFalse()
    }

    @Test
    fun `a stream that never showed a frame fails to start`() {
        rig.create()
        rig.game.connectionStarted()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Failed(StreamFailure(FailureKind.GENERIC, LOST_TEXT, null)))
    }

    @Test
    fun `the surface that goes away first is a leave, so the report shows nothing`() {
        rig.streaming()
        rig.game.surfaceCreated(holder)
        rig.markConnectionAttempted()
        rig.game.surfaceDestroyed(holder)
        rig.awaitStop()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertNoFailure()
        assertWarned()
    }

    @Test
    fun `the report that comes first and then the surface that goes away shows nothing`() {
        rig.streaming()
        rig.game.surfaceCreated(holder)
        rig.markConnectionAttempted()
        surfaceLost()

        rig.game.surfaceDestroyed(holder)
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        assertNoFailure()
        assertWarned()
    }

    @Test
    fun `an activity that stopped first is a leave, so the report shows nothing`() {
        rig.streaming()
        rig.stop()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertNoFailure()
        assertWarned()
    }

    @Test
    fun `the report that comes first and then an activity that stops shows nothing`() {
        rig.streaming()
        surfaceLost()

        rig.stop()
        ShadowLooper.idleMainLooper()

        assertNoFailure()
        assertWarned()
    }

    @Test
    fun `a Disconnect that came first is a leave, so the report shows nothing`() {
        rig.streaming()
        rig.recordSession()
        rig.session.disconnect()
        // What the action of the app does: the Disconnect stops the connection.
        rig.game.stopConnectionThen {}
        rig.awaitStop()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertThat(rig.phase).isEqualTo(StreamPhase.Disconnecting)
        assertWarned()
    }

    @Test
    fun `the report that comes first and then a Disconnect shows nothing`() {
        rig.streaming()
        rig.recordSession()
        surfaceLost()

        rig.session.disconnect()
        rig.game.stopConnectionThen {}
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(StreamPhase.Disconnecting)
        assertWarned()
    }

    @Test
    fun `an activity that is finishing is a leave, so the report shows nothing`() {
        rig.streaming()
        rig.game.finish()

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertNoFailure()
        assertWarned()
    }

    @Test
    fun `picture in picture keeps the stream, so the report ends it with the failure`() {
        rig.streaming()
        rig.game.enterPictureInPictureMode()

        surfaceLost()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Failed(StreamFailure(FailureKind.STOPPED, LOST_TEXT, null)))
        assertThat(warnings()).isEmpty()
        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(1)
    }

    @Test
    fun `the report that comes first and then picture in picture still ends the stream`() {
        rig.streaming()
        surfaceLost()

        rig.game.enterPictureInPictureMode()
        ShadowLooper.idleMainLooper()
        rig.awaitStop()

        assertThat(rig.phase)
            .isEqualTo(StreamPhase.Failed(StreamFailure(FailureKind.STOPPED, LOST_TEXT, null)))
        assertThat(warnings()).isEmpty()
        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(1)
    }

    @Test
    fun `a stream that failed already keeps its failure, and the report only warns`() {
        rig.streaming()
        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        rig.awaitStop()
        val failed = rig.phase
        assertThat(failed).isInstanceOf(StreamPhase.Failed::class.java)

        surfaceLost()
        ShadowLooper.idleMainLooper()

        assertThat(rig.phase).isSameInstanceAs(failed)
        assertWarned()
    }

    @Test
    fun `the report is not a crash of the codec, which keeps its own report`() {
        rig.streaming()
        val listener = ReflectionHelpers.getField<CrashListener>(rig.renderer, "crashListener")

        onConnectionThread { listener.notifyCrash(IllegalStateException("codec")) }

        assertThat(rig.field<Boolean>("reportedCrash")).isTrue()
        assertThat(rig.field<SharedPreferences>("tombstonePrefs").getInt("CrashCount", 0))
            .isEqualTo(1)
        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
    }
}
