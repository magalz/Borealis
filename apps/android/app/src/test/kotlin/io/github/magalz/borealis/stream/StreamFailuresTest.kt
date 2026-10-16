package io.github.magalz.borealis.stream

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.host.R as HostR
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.host.failure.FailureFacts
import io.github.magalz.borealis.host.failure.FailureKind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A connection that failed in a given way, as `NvConnection` reports it. */
private class FailedConnection(
    private val cause: CauseKind = CauseKind.NONE,
    private val http: Int = 0,
    private val collision: Boolean = false,
    private val exhausted: Boolean = false,
) :
    NvConnection(
        ApplicationProvider.getApplicationContext<Application>(),
        null,
        0,
        "test",
        null,
        null,
        null,
    ) {
    override fun lastFailureCause(): CauseKind = cause

    override fun lastHttpCode(): Int = http

    override fun endedByResumeCollision(): Boolean = collision

    override fun launchRetriesExhausted(): Boolean = exhausted
}

/** Every string is its resource id, so a test names what it expects by id. */
private fun text(id: Int, args: Array<Any>): String =
    if (args.isEmpty()) "$id" else "$id:${args.joinToString(",")}"

private fun failure(facts: FailureFacts, portBlocked: Boolean = false) =
    StreamFailures.failure(facts, portBlocked, ::text) { "ports$it" }

private fun hint(facts: FailureFacts, portBlocked: Boolean = false) =
    StreamFailures.hint(facts, portBlocked, ::text)

private fun terminated(code: Int) = FailureFacts(null, code, 0, terminated = true)

@RunWith(RobolectricTestRunner::class)
class StreamFailuresTest {
    @Test
    fun `a failed stage carries the stage, the code and the port flags`() {
        val facts = StreamFailures.facts("RTSP handshake", -1, 0x8, false, FailedConnection())

        assertThat(facts).isEqualTo(FailureFacts("RTSP handshake", -1, 0x8, terminated = false))
    }

    @Test
    fun `a terminated connection is marked as terminated and has no stage`() {
        val facts = StreamFailures.facts(null, -100, 0, true, FailedConnection())

        assertThat(facts).isEqualTo(FailureFacts(null, -100, 0, terminated = true))
    }

    @Test
    fun `the facts take the cause, the status and the flags of the connection`() {
        val facts =
            StreamFailures.facts(
                "Launch",
                503,
                0,
                false,
                FailedConnection(CauseKind.HTTP, 503, collision = true, exhausted = true),
            )

        assertThat(facts.cause).isEqualTo(CauseKind.HTTP)
        assertThat(facts.httpCode).isEqualTo(503)
        assertThat(facts.resumeCollision).isTrue()
        assertThat(facts.retriesExhausted).isTrue()
    }

    @Test
    fun `an http status of zero is no status`() {
        val facts = StreamFailures.facts("Launch", 1, 0, false, FailedConnection(CauseKind.TIMEOUT))

        assertThat(facts.cause).isEqualTo(CauseKind.TIMEOUT)
        assertThat(facts.httpCode).isNull()
    }

    @Test
    fun `without a connection nothing is known about the cause`() {
        val facts = StreamFailures.facts("Launch", 1, 0, false, null)

        assertThat(facts).isEqualTo(FailureFacts("Launch", 1, 0, terminated = false))
    }

    @Test
    fun `the facts are classified, so a busy host is a busy host`() {
        val collided = failure(FailureFacts("RTSP handshake", -1, 0, false, resumeCollision = true))
        val offline = failure(FailureFacts("Launch", 1, 0, false, cause = CauseKind.UNREACHABLE))

        assertThat(collided.kind).isEqualTo(FailureKind.HOST_BUSY)
        assertThat(offline.kind).isEqualTo(FailureKind.HOST_OFFLINE)
    }

    @Test
    fun `the resume collision text is the hint of a collision`() {
        val facts = FailureFacts("RTSP handshake", -1, 0, false, resumeCollision = true)

        assertThat(hint(facts)).isEqualTo("${HostR.string.launch_resume_collision}")
    }

    @Test
    fun `a blocked network is the hint of a failed stage, after the collision text`() {
        assertThat(hint(FailureFacts("Control", -1, 0, false), portBlocked = true))
            .isEqualTo("${R.string.nettest_text_blocked}")
        assertThat(
                hint(
                    FailureFacts("RTSP handshake", -1, 0, false, resumeCollision = true),
                    portBlocked = true,
                )
            )
            .isEqualTo(
                "${HostR.string.launch_resume_collision}\n\n${R.string.nettest_text_blocked}"
            )
    }

    @Test
    fun `a failed stage without a collision or a blocked network has no hint`() {
        assertThat(hint(FailureFacts("Control", -1, 0, false))).isNull()
    }

    @Test
    fun `no video traffic and no frame carry the LuminalShine hint after their own text`() {
        val luminalShine = HostR.string.stream_luminalshine_hint

        assertThat(hint(terminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC)))
            .isEqualTo("${R.string.no_video_received_error}\n\n$luminalShine")
        assertThat(hint(terminated(MoonBridge.ML_ERROR_NO_VIDEO_FRAME)))
            .isEqualTo("${R.string.no_frame_received_error}\n\n$luminalShine")
    }

    @Test
    fun `the other known termination codes have their own text`() {
        assertThat(hint(terminated(MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION)))
            .isEqualTo("${R.string.early_termination_error}")
        assertThat(hint(terminated(MoonBridge.ML_ERROR_PROTECTED_CONTENT)))
            .isEqualTo("${R.string.early_termination_error}")
        assertThat(hint(terminated(MoonBridge.ML_ERROR_FRAME_CONVERSION)))
            .isEqualTo("${R.string.frame_conversion_error}")
    }

    @Test
    fun `an unknown termination code has no hint, the raw code says it`() {
        assertThat(hint(terminated(-1234))).isNull()
    }

    @Test
    fun `a blocked network supersedes the text of a termination`() {
        assertThat(hint(terminated(MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC), portBlocked = true))
            .isEqualTo("${R.string.nettest_text_blocked}")
    }

    @Test
    fun `the hint becomes the port hint when the classifier has none`() {
        val failed = failure(terminated(MoonBridge.ML_ERROR_FRAME_CONVERSION))

        assertThat(failed.portHint).isEqualTo("${R.string.frame_conversion_error}")
    }

    @Test
    fun `no video with port flags shows the port text and the LuminalShine hint`() {
        val facts = FailureFacts(null, MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC, 0x8, terminated = true)

        val failed = failure(facts)

        assertThat(failed.portHint)
            .isEqualTo(
                "${HostR.string.failure_port_hint}:ports8\n\n" +
                    "${R.string.no_video_received_error}\n\n${HostR.string.stream_luminalshine_hint}"
            )
    }

    @Test
    fun `a blocked network is shown beside the port text, not instead of it`() {
        val facts = FailureFacts("Control", -1, 0x8, terminated = false)

        val failed = failure(facts, portBlocked = true)

        assertThat(failed.portHint)
            .isEqualTo("${HostR.string.failure_port_hint}:ports8\n\n${R.string.nettest_text_blocked}")
    }

    @Test
    fun `the port text alone stays as it is`() {
        val failed = failure(FailureFacts("Control", -1, 0x8, terminated = false))

        assertThat(failed.portHint).isEqualTo("${HostR.string.failure_port_hint}:ports8")
    }

    @Test
    fun `a failure without a hint and without ports has no port hint`() {
        assertThat(failure(FailureFacts("Control", -1, 0, false)).portHint).isNull()
    }

    @Test
    fun `the raw code of a failure is the classifier one`() {
        val failed = failure(FailureFacts("Control", -1, 0, false))

        assertThat(failed.rawCode).isEqualTo("${HostR.string.failure_raw_stage}:-1,Control")
    }
}
