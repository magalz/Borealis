package io.github.magalz.borealis.host.failure

import io.github.magalz.borealis.host.R

/**
 * Classifies a failed stream start or an ended stream for the connection error screen.
 *
 * The first rule that matches wins:
 * - a changed certificate: CERTIFICATE_CHANGED;
 * - another device was starting its stream, or the 409 and 503 launch retries ran out, or a read
 *   timed out after the TCP connection was made (the exhausted `/serverinfo` retry, UC-002-T3):
 *   HOST_BUSY;
 * - nothing answered the TCP connection, which includes a connect timeout (see [FailureCauses.of]),
 *   so the PC is off or asleep: HOST_OFFLINE, where the screen offers Wake PC;
 * - anything else: GENERIC.
 */
object StreamFailureClassifier {
    /**
     * [text] formats a string resource with its arguments; [portFlagsText] is
     * `MoonBridge.stringifyPortFlags` with a comma separator.
     */
    @JvmStatic
    fun classify(
        facts: FailureFacts,
        text: (Int, Array<Any>) -> String,
        portFlagsText: (Int) -> String,
    ): StreamFailure {
        val kind = kindOf(facts)
        val showPorts =
            facts.portFlags != 0 &&
                (kind == FailureKind.GENERIC || kind == FailureKind.HOST_OFFLINE)
        return StreamFailure(
            kind,
            rawCode(facts, text),
            if (showPorts) text(R.string.failure_port_hint, arrayOf(portFlagsText(facts.portFlags)))
            else null,
        )
    }

    // A timeout only comes from the HTTP start path, after ServerInfoRetry gave up or a launch
    // request went unanswered: the host is there but busy.
    private fun kindOf(facts: FailureFacts): FailureKind =
        when {
            facts.cause == CauseKind.CERT_MISMATCH -> FailureKind.CERTIFICATE_CHANGED
            facts.resumeCollision || facts.retriesExhausted -> FailureKind.HOST_BUSY
            facts.cause == CauseKind.TIMEOUT && !facts.terminated -> FailureKind.HOST_BUSY
            facts.cause == CauseKind.UNREACHABLE -> FailureKind.HOST_OFFLINE
            else -> FailureKind.GENERIC
        }

    // Plain digits as text: %d would group or localize a number that support has to read out.
    private fun rawCode(facts: FailureFacts, text: (Int, Array<Any>) -> String): String {
        val code = facts.errorCode.toString()
        val stage = facts.stage
        val base =
            if (facts.terminated || stage == null) text(R.string.failure_raw_code, arrayOf(code))
            else text(R.string.failure_raw_stage, arrayOf(code, stage))
        val http = facts.httpCode ?: return base
        return text(R.string.failure_raw_http, arrayOf(base, http.toString()))
    }
}
