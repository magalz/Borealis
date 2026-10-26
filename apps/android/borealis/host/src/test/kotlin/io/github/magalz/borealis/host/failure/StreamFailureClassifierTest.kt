package io.github.magalz.borealis.host.failure

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import org.junit.Test

class StreamFailureClassifierTest {
    /** Renders a string as `id:arg|arg` so a test sees which resource got which arguments. */
    private val text: (Int, Array<Any>) -> String = { id, args -> "$id:" + args.joinToString("|") }
    private val ports: (Int) -> String = { "ports=$it" }

    private val rawStage = R.string.failure_raw_stage
    private val rawCode = R.string.failure_raw_code
    private val rawHttp = R.string.failure_raw_http
    private val portHint = R.string.failure_port_hint

    private fun classify(facts: FailureFacts) = StreamFailureClassifier.classify(facts, text, ports)

    /** A failure of the Desktop launch that says nothing about its cause. */
    private val started =
        FailureFacts(stage = "Desktop", errorCode = 0, portFlags = 0, terminated = false)

    @Test
    fun `a pinned certificate mismatch is a changed certificate`() {
        val failure = classify(started.copy(cause = CauseKind.CERT_MISMATCH))

        assertThat(failure.kind).isEqualTo(FailureKind.CERTIFICATE_CHANGED)
    }

    @Test
    fun `a certificate mismatch wins over a busy host`() {
        val failure =
            classify(started.copy(cause = CauseKind.CERT_MISMATCH, resumeCollision = true))

        assertThat(failure.kind).isEqualTo(FailureKind.CERTIFICATE_CHANGED)
    }

    @Test
    fun `a resume collision is a busy host`() {
        val failure = classify(started.copy(resumeCollision = true))

        assertThat(failure.kind).isEqualTo(FailureKind.HOST_BUSY)
    }

    @Test
    fun `exhausted launch retries are a busy host`() {
        val failure =
            classify(started.copy(cause = CauseKind.HTTP, httpCode = 503, retriesExhausted = true))

        assertThat(failure.kind).isEqualTo(FailureKind.HOST_BUSY)
    }

    @Test
    fun `a timeout before the first frame is a busy host`() {
        val failure = classify(started.copy(cause = CauseKind.TIMEOUT, portFlags = 0x3))

        assertThat(failure.kind).isEqualTo(FailureKind.HOST_BUSY)
    }

    @Test
    fun `an unreachable host is offline`() {
        val failure = classify(started.copy(cause = CauseKind.UNREACHABLE))

        assertThat(failure.kind).isEqualTo(FailureKind.HOST_OFFLINE)
    }

    @Test
    fun `an unreachable host on the HTTP ports is offline and names the ports`() {
        val failure = classify(started.copy(cause = CauseKind.UNREACHABLE, portFlags = 0x3))

        assertThat(failure.kind).isEqualTo(FailureKind.HOST_OFFLINE)
        assertThat(failure.portHint).isEqualTo("$portHint:ports=3")
    }

    @Test
    fun `a failure without a known cause is generic`() {
        assertThat(classify(started).kind).isEqualTo(FailureKind.GENERIC)
        assertThat(classify(started.copy(cause = CauseKind.HTTP, httpCode = 500)).kind)
            .isEqualTo(FailureKind.GENERIC)
    }

    @Test
    fun `a stream that ended is generic`() {
        val failure =
            classify(
                FailureFacts(stage = null, errorCode = -100, portFlags = 0x0100, terminated = true)
            )

        assertThat(failure.kind).isEqualTo(FailureKind.GENERIC)
    }

    private fun ended(
        errorCode: Int = -1,
        streamed: Boolean,
        portFlags: Int = 0,
        cause: CauseKind = CauseKind.NONE,
    ) =
        FailureFacts(
            stage = null,
            errorCode = errorCode,
            portFlags = portFlags,
            terminated = true,
            cause = cause,
            streamed = streamed,
        )

    @Test
    fun `a stream that ended after the first frame stopped`() {
        val failure = classify(ended(streamed = true))

        assertThat(failure.kind).isEqualTo(FailureKind.STOPPED)
        assertThat(failure.rawCode).isEqualTo("$rawCode:-1")
    }

    @Test
    fun `a stream that ended before the first frame did not start`() {
        assertThat(classify(ended(streamed = false)).kind).isEqualTo(FailureKind.GENERIC)
    }

    @Test
    fun `a failed start never stopped, even when the facts say a frame was shown`() {
        assertThat(classify(started.copy(streamed = true)).kind).isEqualTo(FailureKind.GENERIC)
    }

    @Test
    fun `a stopped stream names the ports that may be blocked`() {
        val failure = classify(ended(streamed = true, portFlags = 0x100))

        assertThat(failure.portHint).isEqualTo("$portHint:ports=256")
    }

    @Test
    fun `busy, offline and a changed certificate keep their kind after a frame was shown`() {
        val offline = classify(ended(streamed = true, cause = CauseKind.UNREACHABLE))
        val cert = classify(ended(streamed = true, cause = CauseKind.CERT_MISMATCH))
        val collided = classify(ended(streamed = true).copy(resumeCollision = true))

        assertThat(offline.kind).isEqualTo(FailureKind.HOST_OFFLINE)
        assertThat(cert.kind).isEqualTo(FailureKind.CERTIFICATE_CHANGED)
        assertThat(collided.kind).isEqualTo(FailureKind.HOST_BUSY)
    }

    @Test
    fun `a stage failure shows the error code and the stage`() {
        val failure =
            classify(
                FailureFacts(
                    stage = "RTSP handshake",
                    errorCode = -1,
                    portFlags = 0,
                    terminated = false,
                )
            )

        assertThat(failure.rawCode).isEqualTo("$rawStage:-1|RTSP handshake")
    }

    @Test
    fun `a termination shows the error code alone`() {
        val failure =
            classify(
                FailureFacts(stage = "ignored", errorCode = -100, portFlags = 0, terminated = true)
            )

        assertThat(failure.rawCode).isEqualTo("$rawCode:-100")
    }

    @Test
    fun `an HTTP error adds its status to the support text`() {
        val failure =
            classify(started.copy(cause = CauseKind.HTTP, httpCode = 503, errorCode = 503))

        assertThat(failure.rawCode).isEqualTo("$rawHttp:$rawStage:503|Desktop|503")
    }

    @Test
    fun `a start failure without a stage shows the error code alone`() {
        val failure =
            classify(FailureFacts(stage = null, errorCode = 0, portFlags = 0, terminated = false))

        assertThat(failure.rawCode).isEqualTo("$rawCode:0")
    }

    @Test
    fun `the port hint is there for generic and offline failures that have port flags`() {
        val generic = classify(started.copy(portFlags = 0x3))
        val offline = classify(started.copy(cause = CauseKind.UNREACHABLE, portFlags = 0x100))

        assertThat(generic.portHint).isEqualTo("$portHint:ports=3")
        assertThat(offline.portHint).isEqualTo("$portHint:ports=256")
    }

    @Test
    fun `the port hint is absent without port flags`() {
        assertThat(classify(started).portHint).isNull()
        assertThat(classify(started.copy(cause = CauseKind.UNREACHABLE)).portHint).isNull()
    }

    @Test
    fun `the port hint is absent for busy and changed-certificate failures`() {
        assertThat(classify(started.copy(cause = CauseKind.TIMEOUT, portFlags = 0x3)).portHint)
            .isNull()
        assertThat(classify(started.copy(resumeCollision = true, portFlags = 0x3)).portHint)
            .isNull()
        assertThat(
                classify(started.copy(cause = CauseKind.CERT_MISMATCH, portFlags = 0x3)).portHint
            )
            .isNull()
    }

    @Test
    fun `an unreadable RTSP reply shows no port hint, even with port flags`() {
        val failure =
            classify(started.copy(portFlags = 0x3, errorCode = -1, rtspReplyUnreadable = true))

        assertThat(failure.kind).isEqualTo(FailureKind.GENERIC)
        assertThat(failure.portHint).isNull()
        assertThat(failure.rawCode).isEqualTo("$rawStage:-1|Desktop")
    }

    @Test
    fun `the same stage with another code still shows the port hint`() {
        val failure = classify(started.copy(portFlags = 0x3, errorCode = -2))

        assertThat(failure.portHint).isEqualTo("$portHint:ports=3")
    }

    @Test
    fun `an error -1 without the unreadable reply fact still shows the port hint`() {
        val failure = classify(started.copy(portFlags = 0x3, errorCode = -1))

        assertThat(failure.portHint).isEqualTo("$portHint:ports=3")
    }
}
