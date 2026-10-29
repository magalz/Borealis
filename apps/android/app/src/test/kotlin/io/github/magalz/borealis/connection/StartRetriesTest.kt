package io.github.magalz.borealis.connection

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.nvhttp.HostReply
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The waits and the retries of a start: `/launch` and `/resume` that the host asks to repeat (409
 * and 503), `/serverinfo` that times out, and a start that the user cancels meanwhile. The retry
 * waits are ten milliseconds here (see [ShadowHostRules]); a read timeout is the seven seconds of
 * the HTTP client.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    shadows =
        [
            ShadowRecordingBridge::class,
            ShadowMonkeyActivityManager::class,
            ShadowLoadedNativeLibrary::class,
            ShadowHostRules::class,
        ]
)
class StartRetriesTest {
    @get:Rule val rig = ConnectionRig()

    private val listener
        get() = rig.listener

    private fun retry(attempt: Int, max: Int, category: Int = PENDING) =
        "launchRetrying category=$category attempt=$attempt max=$max delay=${ShadowHostRules.RETRY_DELAY_MS}"

    @Test
    fun `a launch that the host asks to repeat is repeated, and the listener hears each retry`() {
        rig.script.launchReplies += HostReply.failure("409", "pending")
        rig.script.launchReplies += HostReply.failure("409", "pending")
        val connection = rig.connection()

        rig.start(connection)
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                retry(1, 4),
                retry(2, 4),
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/launch" }).isEqualTo(3)
        assertThat(connection.launchRetriesExhausted()).isFalse()
    }

    @Test
    fun `a host that keeps asking to repeat ends the start with its status, and the retries ran out`() {
        repeat(6) { rig.script.launchReplies += HostReply.failure("409", "pending") }
        val connection = rig.connection()

        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                retry(1, 4),
                retry(2, 4),
                retry(3, 4),
                retry(4, 4),
                "stageFailed launch ports=0 error=409",
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/launch" }).isEqualTo(5)
        assertThat(connection.launchRetriesExhausted()).isTrue()
        assertThat(connection.lastHttpCode()).isEqualTo(409)
        assertThat(connection.lastFailureCause()).isEqualTo(CauseKind.HTTP)
    }

    @Test
    fun `a resume that the host asks to repeat is repeated`() {
        rig.script.running(DESKTOP_ID)
        rig.script.resumeReplies += HostReply.failure("503", "capture is not ready")

        rig.start(rig.resumingConnection())
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=true",
                retry(1, 1, category = CAPTURE_OR_DISPLAY),
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/resume" }).isEqualTo(2)
    }

    @Test
    fun `a GFE host is never asked again`() {
        rig.script.state = HostScript.GFE_FREE
        rig.script.launchReplies += HostReply.failure("409", "pending")
        val connection = rig.connection()

        rig.start(connection)
        listener.await("stageFailed")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "stageFailed launch ports=0 error=409",
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/launch" }).isEqualTo(1)
        assertThat(connection.launchRetriesExhausted()).isFalse()
    }

    @Test
    fun `a launch that is cancelled while it waits to repeat ends without a word`() {
        rig.script.launchReplies += HostReply.failure("409", "pending")
        val connection = rig.connection()
        listener.onEvent = { if (it.startsWith("launchRetrying")) connection.cancelLaunch() }

        rig.start(connection)
        listener.await("launchRetrying")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                retry(1, 4),
            )
            .inOrder()
        assertThat(rig.paths().count { it == "/launch" }).isEqualTo(1)
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a start that was cancelled before it began sends no launch and tells nothing`() {
        val connection = rig.connection()
        connection.cancelLaunch()

        rig.start(connection)
        listener.await("launchStarting")
        listener.awaitEnded()

        assertThat(listener.events)
            .containsExactly("stageStarting $DESKTOP_NAME", "launchStarting resume=false")
            .inOrder()
        assertThat(rig.paths()).containsExactly("/serverinfo")
        assertThat(ShadowRecordingBridge.calls).isEmpty()
    }

    @Test
    fun `a start that is cancelled when it is complete does not take the place of a connection`() {
        val connection = rig.connection()
        listener.onEvent = { if (it.startsWith("stageComplete")) connection.cancelLaunch() }

        rig.start(connection)
        listener.await("stageComplete")
        listener.awaitEnded()

        assertThat(ShadowRecordingBridge.calls).isEmpty()
        assertThat(rig.permits()).isEqualTo(1)
    }

    @Test
    fun `a serverinfo that times out is asked for again`() {
        rig.script.serverInfoReplies += HostReply.HOLD

        rig.start(rig.connection())
        ShadowRecordingBridge.await("startConnection")
        listener.awaitEnded()

        assertThat(rig.paths()).containsExactly("/serverinfo", "/serverinfo", "/launch").inOrder()
        assertThat(listener.events)
            .containsExactly(
                "stageStarting $DESKTOP_NAME",
                "launchStarting resume=false",
                "stageComplete $DESKTOP_NAME",
            )
            .inOrder()
    }

    @Test
    fun `a host that was slow to answer serverinfo gets a pause before the resume`() {
        rig.script.running(DESKTOP_ID)
        rig.script.serverInfoReplies += HostReply.HOLD
        val began = System.nanoTime()

        rig.start(rig.resumingConnection())
        ShadowRecordingBridge.await("startConnection")
        val tookMs = (System.nanoTime() - began) / NANOS_PER_MS
        listener.awaitEnded()

        // The read timeout of the first answer (7 s) and the pause before the resume (3 s).
        assertThat(tookMs).isAtLeast(READ_TIMEOUT_MS + PAUSE_MS - SLACK_MS)
        assertThat(rig.paths()).containsExactly("/serverinfo", "/serverinfo", "/resume").inOrder()
        assertThat(listener.events("launchRetrying")).isEmpty()
    }

    @Test
    fun `a start that is cancelled while serverinfo times out ends without a word`() {
        rig.script.serverInfoReplies += HostReply.HOLD
        val connection = rig.connection()

        rig.start(connection)
        while (rig.host.requests.isEmpty()) Thread.sleep(POLL_MS)
        connection.cancelLaunch()
        listener.awaitEnded()

        assertThat(listener.events).containsExactly("stageStarting $DESKTOP_NAME")
        assertThat(rig.paths()).containsExactly("/serverinfo")
    }

    private companion object {
        /** The ordinals of `LaunchCategory` that the stand-in of the core answers with. */
        const val PENDING = 1
        const val CAPTURE_OR_DISPLAY = 4
        const val NANOS_PER_MS = 1_000_000L
        const val READ_TIMEOUT_MS = 7_000L
        const val PAUSE_MS = 3_000L
        const val SLACK_MS = 500L
        const val POLL_MS = 10L
    }
}
