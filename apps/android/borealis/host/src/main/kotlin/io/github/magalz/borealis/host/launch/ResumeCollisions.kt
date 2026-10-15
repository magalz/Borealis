package io.github.magalz.borealis.host.launch

import java.util.function.BooleanSupplier
import java.util.function.Supplier

/** What to do with a failed stage reported by moonlight-common-c. */
enum class StageFailure {
    /** Swallow it: the start is restarted once. */
    HOLD,

    /** Show it, without the firewall paragraph: another device was starting a stream. */
    REPORT_COLLISION,

    /** Show it unchanged. */
    REPORT,
}

class ResumeCollisions
internal constructor(
    private val cancelled: BooleanSupplier,
    private val progress: RetryProgress,
    private val host: Supplier<String?>,
    private val clock: () -> Long,
    private val sleeper: (Long) -> Unit,
) {
    constructor(
        cancelled: BooleanSupplier,
        progress: RetryProgress,
        host: Supplier<String?>,
    ) : this(cancelled, progress, host, { System.nanoTime() / NANOS_PER_MILLI }, { Thread.sleep(it) })

    fun readServerInfo(reader: ServerInfoReader): String = reader.read()

    fun settleBeforeResume(): Boolean = true

    fun resumeAccepted(isGfe: Boolean) = Unit

    fun onStageFailed(isRtspStage: Boolean, errorCode: Int): StageFailure = StageFailure.REPORT

    fun restartAfterFailure(): Boolean = false

    fun isRestart(): Boolean = false

    fun refuseQuit(): Boolean = false

    fun collided(): Boolean = false

    companion object {
        const val SLOW_SERVERINFO_MS = 3_000L
        const val SETTLE_WAIT_MS = 3_000L
        const val RTSP_FAILURE_WINDOW_MS = 5_000L
        const val MAX_RESTARTS = 1
        const val SLICE_MS = 250L
        const val STAGE_RTSP_HANDSHAKE = 4
        const val RTSP_UNREADABLE_REPLY = -1
        private const val NANOS_PER_MILLI = 1_000_000L

        @JvmStatic
        fun withHint(collided: Boolean, message: String, getString: (Int) -> String): String =
            message
    }
}
