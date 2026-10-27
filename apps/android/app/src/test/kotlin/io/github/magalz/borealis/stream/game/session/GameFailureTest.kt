package io.github.magalz.borealis.stream.game.session

import android.view.WindowManager
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.binding.input.ControllerHandler
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.ui.StreamView
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.host.R as HostR
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.game.GameTestBase
import io.github.magalz.borealis.stream.game.RecordingSurface
import io.github.magalz.borealis.stream.game.assertToasts
import io.github.magalz.borealis.stream.game.phase
import org.junit.Test
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/**
 * What the activity tells the session when the start fails (`stageFailed`) or the stream ends
 * (`connectionTerminated`): the kind of the failure and the text of the error screen come from the
 * facts that the activity collects, and the classifier turns them into text, not the activity.
 */
internal class GameFailureTest : GameTestBase() {
    private fun text(id: Int): String = rig.application.getString(id)

    private fun failure(kind: FailureKind, rawCode: String, hint: String? = null) =
        StreamPhase.Failed(StreamFailure(kind, rawCode, hint))

    private fun stopped(handler: ControllerHandler): Boolean =
        ReflectionHelpers.getField(handler, "stopped")

    private fun portsHint(flags: String) =
        "Check your firewall and port forwarding rules for: $flags"

    // A stage that fails

    @Test
    fun `a stage that fails is a generic failure with the code and the stage`() {
        rig.create()

        rig.game.stageFailed("Control connection", 0, -5)

        assertThat(rig.phase)
            .isEqualTo(failure(FailureKind.GENERIC, "Error -5 · Control connection"))
    }

    @Test
    fun `the port test asks the test server about the ports of the stage`() {
        ShadowRecordingBridge.answers["stringifyPortFlags"] = "UDP 47998"
        rig.create()

        rig.game.stageFailed("Control connection", 0x5, -5)

        val test = ShadowRecordingBridge.named("testClientConnectivity").single()
        assertThat(test.args).containsExactly("android.conntest.moonlight-stream.org", 443, 0x5)
    }

    @Test
    fun `the ports of the stage are named on the error screen`() {
        ShadowRecordingBridge.answers["stringifyPortFlags"] = "UDP 47998"
        rig.create()

        rig.game.stageFailed("Control connection", 0x2, -5)

        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.GENERIC,
                    "Error -5 · Control connection",
                    portsHint("UDP 47998"),
                )
            )
        val flags = ShadowRecordingBridge.named("stringifyPortFlags").single()
        assertThat(flags.args).containsExactly(0x2, ", ")
    }

    @Test
    fun `a network that blocks the ports is said so after the ports`() {
        ShadowRecordingBridge.answers["stringifyPortFlags"] = "UDP 47998"
        ShadowRecordingBridge.answers["testClientConnectivity"] = 1
        rig.create()

        rig.game.stageFailed("Control connection", 0x2, -5)

        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.GENERIC,
                    "Error -5 · Control connection",
                    portsHint("UDP 47998") + "\n\n" + text(R.string.nettest_text_blocked),
                )
            )
    }

    @Test
    fun `a port test that was inconclusive does not say that the network blocks the ports`() {
        ShadowRecordingBridge.answers["testClientConnectivity"] =
            MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
        rig.create()

        rig.game.stageFailed("Control connection", 0, -5)

        assertThat(rig.phase)
            .isEqualTo(failure(FailureKind.GENERIC, "Error -5 · Control connection"))
    }

    @Test
    fun `a network that blocks the ports is said so even when the stage has no ports`() {
        ShadowRecordingBridge.answers["testClientConnectivity"] = 1
        rig.create()

        rig.game.stageFailed("Control connection", 0, -5)

        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.GENERIC,
                    "Error -5 · Control connection",
                    text(R.string.nettest_text_blocked),
                )
            )
    }

    @Test
    fun `the note of the start follows the ports and the blocked network`() {
        ShadowRecordingBridge.answers["stringifyPortFlags"] = "TCP 47984"
        ShadowRecordingBridge.answers["testClientConnectivity"] = 1
        rig.create()
        rig.game.displayMessage("This device is not paired with the host.")

        rig.game.stageFailed("Launch", 0x1, 7)

        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.GENERIC,
                    "Error 7 · Launch",
                    portsHint("TCP 47984") +
                        "\n\n" +
                        "This device is not paired with the host." +
                        "\n\n" +
                        text(R.string.nettest_text_blocked),
                )
            )
    }

    @Test
    fun `a reply of the host that could not be read says so instead of naming the ports`() {
        ShadowRecordingBridge.answers["stringifyPortFlags"] = "TCP 48010"
        rig.create()

        // The bridge names the RTSP handshake stage "RTSP handshake", and its error -1 is the
        // reply.
        rig.game.stageFailed("RTSP handshake", 0x4, -1)

        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.GENERIC,
                    "Error -1 · RTSP handshake",
                    text(HostR.string.failure_reply_unreadable),
                )
            )
    }

    @Test
    fun `the facts of the connection decide the kind of the failure of a stage`() {
        rig.create()
        rig.useConnection(FailedConnection(rig, cause = CauseKind.CERT_MISMATCH))

        rig.game.stageFailed("Launch", 0, 3)

        assertThat(rig.phase)
            .isEqualTo(failure(FailureKind.CERTIFICATE_CHANGED, "Error 3 · Launch"))
    }

    @Test
    fun `a host that does not answer is offline`() {
        rig.create()
        rig.useConnection(FailedConnection(rig, cause = CauseKind.UNREACHABLE))

        rig.game.stageFailed("Launch", 0, 3)

        assertThat(rig.phase).isEqualTo(failure(FailureKind.HOST_OFFLINE, "Error 3 · Launch"))
    }

    @Test
    fun `the status of the host is part of the code`() {
        rig.create()
        rig.useConnection(FailedConnection(rig, cause = CauseKind.HTTP, http = 503))

        rig.game.stageFailed("Launch", 0, 3)

        assertThat(rig.phase).isEqualTo(failure(FailureKind.GENERIC, "Error 3 · Launch · HTTP 503"))
    }

    @Test
    fun `retries that ran out leave the host busy`() {
        rig.create()
        rig.useConnection(FailedConnection(rig, exhausted = true))

        rig.game.stageFailed("Launch", 0, 3)

        assertThat(rig.phase).isEqualTo(failure(FailureKind.HOST_BUSY, "Error 3 · Launch"))
    }

    @Test
    fun `a collision with the start for another device explains itself`() {
        rig.create()
        rig.useConnection(FailedConnection(rig, collision = true))

        rig.game.stageFailed("Launch", 0, 3)

        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.HOST_BUSY,
                    "Error 3 · Launch",
                    text(HostR.string.launch_resume_collision),
                )
            )
    }

    @Test
    fun `a stage that the connection thread reports fails the session on the main thread`() {
        rig.create()

        onConnectionThread { rig.game.stageFailed("Control connection", 0, -5) }
        assertThat(rig.phase).isInstanceOf(StreamPhase.Starting::class.java)
        // The port test runs where the callback runs: it does network I/O.
        assertThat(ShadowRecordingBridge.named("testClientConnectivity")).hasSize(1)

        ShadowLooper.idleMainLooper()
        assertThat(rig.phase)
            .isEqualTo(failure(FailureKind.GENERIC, "Error -5 · Control connection"))
    }

    // The second failure

    @Test
    fun `the failure that comes first is the one that stays`() {
        rig.create()
        rig.game.stageFailed("Control connection", 0, -5)

        rig.game.stageFailed("Input connection", 0, -6)
        rig.game.connectionTerminated(-7)

        assertThat(rig.phase)
            .isEqualTo(failure(FailureKind.GENERIC, "Error -5 · Control connection"))
    }

    @Test
    fun `a stage that fails after the activity stopped shows nothing`() {
        rig.create()
        rig.stop()

        rig.game.stageFailed("Control connection", 0, -5)

        assertThat(rig.phase).isInstanceOf(StreamPhase.Starting::class.java)
    }

    // A video stage that fails

    private fun surfaceOfTheStream(valid: Boolean) {
        val surface = RecordingSurface()
        if (!valid) surface.release()
        useSurface(rig.game.findViewById<StreamView>(R.id.surfaceView), surface)
    }

    @Test
    fun `a video stage that fails with a valid surface tells the user about the decoder`() {
        rig.create()
        surfaceOfTheStream(valid = true)

        rig.game.stageFailed("video stream", 0, -5)

        assertToasts(text(R.string.video_decoder_init_failed))
        assertThat(rig.phase).isEqualTo(failure(FailureKind.GENERIC, "Error -5 · video stream"))
    }

    @Test
    fun `a video stage that fails without a valid surface shows no toast`() {
        rig.create()
        surfaceOfTheStream(valid = false)

        rig.game.stageFailed("video stream", 0, -5)

        assertToasts()
        assertThat(rig.phase).isEqualTo(failure(FailureKind.GENERIC, "Error -5 · video stream"))
    }

    @Test
    fun `a stage that is not the video one shows no toast`() {
        rig.create()
        surfaceOfTheStream(valid = true)

        rig.game.stageFailed("Control connection", 0, -5)

        assertToasts()
    }

    @Test
    fun `a video stage that fails after the first failure shows no toast`() {
        rig.create()
        surfaceOfTheStream(valid = true)
        rig.game.stageFailed("Control connection", 0, -5)

        rig.game.stageFailed("video stream", 0, -6)

        assertToasts()
    }

    // A connection that ends

    @Test
    fun `a host that ends the stream gracefully ends the activity and no failure is shown`() {
        rig.streaming()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()

        assertThat(rig.game.isFinishing).isTrue()
        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat(ShadowRecordingBridge.names)
            .containsAtLeast("interruptConnection", "stopConnection", "cleanupBridge")
    }

    @Test
    fun `a stream that ends in error after the first frame stopped, it did not fail to start`() {
        rig.streaming()

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(failure(FailureKind.STOPPED, "Error -5"))
        assertThat(rig.game.isFinishing).isFalse()
    }

    @Test
    fun `a stream that ends in error before the first frame is a generic failure`() {
        rig.create()
        rig.game.connectionStarted()

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(failure(FailureKind.GENERIC, "Error -5"))
    }

    @Test
    fun `a stream that ends in error stops the connection once`() {
        rig.streaming()

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(ShadowRecordingBridge.names)
            .containsExactly(
                "getPortFlagsFromTerminationErrorCode",
                "testClientConnectivity",
                "interruptConnection",
                "stopConnection",
                "cleanupBridge",
            )
            .inOrder()
    }

    @Test
    fun `the ports of a termination come from its error code`() {
        ShadowRecordingBridge.answers["getPortFlagsFromTerminationErrorCode"] = 0x3
        ShadowRecordingBridge.answers["stringifyPortFlags"] = "UDP 47998, UDP 48000"
        rig.streaming()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        rig.awaitStop()

        assertThat(
                ShadowRecordingBridge.named("getPortFlagsFromTerminationErrorCode").single().args
            )
            .containsExactly(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        assertThat(ShadowRecordingBridge.named("testClientConnectivity").single().args)
            .containsExactly("android.conntest.moonlight-stream.org", 443, 0x3)
        assertThat(rig.phase)
            .isEqualTo(
                failure(
                    FailureKind.STOPPED,
                    "Error -100",
                    portsHint("UDP 47998, UDP 48000") +
                        "\n\n" +
                        text(R.string.no_video_received_error) +
                        "\n\n" +
                        text(HostR.string.stream_luminalshine_hint),
                )
            )
    }

    @Test
    fun `a network that blocks the ports supersedes the other explanations of an ended stream`() {
        ShadowRecordingBridge.answers["testClientConnectivity"] = 1
        rig.streaming()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)
        rig.awaitStop()

        assertThat(rig.phase)
            .isEqualTo(
                failure(FailureKind.STOPPED, "Error -100", text(R.string.nettest_text_blocked))
            )
    }

    private fun endedWith(code: Int, hint: String) {
        rig.streaming()

        rig.game.connectionTerminated(code)
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(failure(FailureKind.STOPPED, "Error $code", hint))
    }

    @Test
    fun `an ended stream with no video frame says so and adds the hint for the host`() {
        endedWith(
            MoonBridge.ML_ERROR_NO_VIDEO_FRAME,
            text(R.string.no_frame_received_error) +
                "\n\n" +
                text(HostR.string.stream_luminalshine_hint),
        )
    }

    @Test
    fun `an ended stream that the host cut short says that something went wrong on the host`() {
        endedWith(
            MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION,
            text(R.string.early_termination_error),
        )
    }

    @Test
    fun `an ended stream of protected content says what an early termination says`() {
        endedWith(MoonBridge.ML_ERROR_PROTECTED_CONTENT, text(R.string.early_termination_error))
    }

    @Test
    fun `an ended stream that the host could not encode says so`() {
        endedWith(MoonBridge.ML_ERROR_FRAME_CONVERSION, text(R.string.frame_conversion_error))
    }

    @Test
    fun `the facts of the connection decide the kind of the failure of an ended stream`() {
        rig.streaming()
        rig.useConnection(FailedConnection(rig, cause = CauseKind.CERT_MISMATCH))

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(rig.phase).isEqualTo(failure(FailureKind.CERTIFICATE_CHANGED, "Error -5"))
    }

    @Test
    fun `an ended stream lets the display sleep, the pad rest and the pointer go`() {
        rig.streaming()
        val handler = rig.field<ControllerHandler>("controllerHandler")
        assertThat(
                rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            .isNotEqualTo(0)
        assertThat(rig.field<Boolean>("grabbedInput")).isTrue()
        assertThat(stopped(handler)).isFalse()

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        assertThat(
                rig.game.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
            .isEqualTo(0)
        assertThat(rig.field<Boolean>("grabbedInput")).isFalse()
        assertThat(stopped(handler)).isTrue()
    }

    @Test
    fun `an ended stream is in the audit trail with its reason`() {
        rig.streaming()
        rig.audit.clear()

        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        val end = rig.audit.single()
        assertThat(end.type).isEqualTo(AuditType.SESSION_END)
        assertThat(end.outcome).isEqualTo(AuditOutcome.FAILURE)
        assertThat(end.detail).startsWith("reason=error code=-5")
    }

    @Test
    fun `a host that ends the stream gracefully is in the audit trail as the host ending it`() {
        rig.streaming()
        rig.audit.clear()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()

        val end = rig.audit.single()
        assertThat(end.outcome).isEqualTo(AuditOutcome.SUCCESS)
        assertThat(end.detail).startsWith("reason=host_ended")
    }

    @Test
    fun `a stream that ends after the activity stopped shows no failure and stops nothing again`() {
        rig.streaming()
        rig.stop()
        val stops = ShadowRecordingBridge.named("stopConnection").size

        rig.game.connectionTerminated(-5)

        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)
        assertThat(ShadowRecordingBridge.named("stopConnection")).hasSize(stops)
    }

    @Test
    fun `a stream that ends twice shows the first end only`() {
        rig.streaming()
        rig.game.connectionTerminated(-5)
        rig.awaitStop()

        rig.game.connectionTerminated(MoonBridge.ML_ERROR_GRACEFUL_TERMINATION)
        rig.awaitStop()

        assertThat(rig.game.isFinishing).isFalse()
        assertThat(rig.phase).isEqualTo(failure(FailureKind.STOPPED, "Error -5"))
    }

    @Test
    fun `a termination that the connection thread reports reaches the activity on the main thread`() {
        rig.streaming()

        onConnectionThread { rig.game.connectionTerminated(-5) }
        // The port test runs where the callback runs: it does network I/O.
        assertThat(ShadowRecordingBridge.named("testClientConnectivity")).hasSize(1)
        assertThat(rig.phase).isInstanceOf(StreamPhase.Streaming::class.java)

        ShadowLooper.idleMainLooper()
        rig.awaitStop()
        assertThat(rig.phase).isEqualTo(failure(FailureKind.STOPPED, "Error -5"))
    }
}
