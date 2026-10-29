package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.LaunchCategory
import java.io.IOException
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import org.xmlpull.v1.XmlPullParserException

/** What to do with a failed stage reported by moonlight-common-c. */
enum class StageFailure {
    /** Swallow it: the start is restarted once. */
    HOLD,

    /** Show it, without the firewall paragraph: another device was starting a stream. */
    REPORT_COLLISION,

    /** Show it unchanged. */
    REPORT,
}

/**
 * Handles a second device that joins while LuminalShine is still building another device's virtual
 * display (host contract 7.3, luminalshine#205). The host answers the second device's slow
 * `/serverinfo`, accepts its `/resume`, and then cannot decrypt its RTSP messages. Waiting a few
 * seconds before `/resume`, and restarting the whole start once when RTSP still fails right after
 * an accepted `/resume`, lets the second device in; when that fails too, the error says what
 * happened instead of blaming the firewall.
 *
 * One instance per connection. Blocks while waiting, so call it off the main thread.
 */
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
    ) : this(
        cancelled,
        progress,
        host,
        { System.nanoTime() / NANOS_PER_MILLI },
        { Thread.sleep(it) },
    )

    // The connection thread and the thread that runs the native handshake never overlap (the
    // handshake runs inside the connection thread's call). Game reads collided() on the UI thread.
    private var hostBusy = false
    private var serverInfoMs = 0L
    private var resumeAcceptedAt = NONE
    private var heldError = 0
    private var heldSinceResumeMs = 0L
    private var restarts = 0
    @Volatile private var held = false
    @Volatile private var collision = false

    /**
     * Reads `/serverinfo` with the timeout retry, and notes whether the host was slow to answer:
     * that means it was busy with another device's launch.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readServerInfo(reader: ServerInfoReader): String {
        var attempts = 0
        val startedAt = clock()
        val answer =
            ServerInfoRetry.read(
                {
                    attempts++
                    reader.read()
                },
                cancelled,
            )
        serverInfoMs = clock() - startedAt
        hostBusy = serverInfoMs >= SLOW_SERVERINFO_MS || attempts > 1
        return answer
    }

    /**
     * Waits before `/resume` when the host was busy or this start is the restart. False when the
     * start was cancelled or interrupted during the wait.
     */
    fun settleBeforeResume(): Boolean {
        val restart = isRestart()
        if (!hostBusy && !restart) return true
        if (restart) {
            progress.onRetry(
                LaunchCategory.PENDING.ordinal,
                restarts,
                MAX_RESTARTS,
                SETTLE_WAIT_MS.toInt(),
            )
        } else {
            BorealisLog.i(
                LogTag.LAUNCH,
                "host busy, waiting before resume",
                "serverinfo_ms" to serverInfoMs,
                "wait_ms" to SETTLE_WAIT_MS,
            )
        }
        return waitInSlices(SETTLE_WAIT_MS)
    }

    /**
     * A `/resume` of the app that this start asked for was accepted, whether the user chose Resume
     * in the Library or answered Join in the dialog. Not for a Join of another app: a restart would
     * not find that app again.
     */
    fun resumeAccepted(isGfe: Boolean) {
        resumeAcceptedAt = if (isGfe) NONE else clock()
    }

    /** Decides what happens to a failed stage; see [StageFailure]. */
    fun onStageFailed(isRtspStage: Boolean, errorCode: Int): StageFailure {
        val sinceResume = if (resumeAcceptedAt == NONE) NONE else clock() - resumeAcceptedAt
        val collides =
            isRtspStage &&
                errorCode == RTSP_UNREADABLE_REPLY &&
                sinceResume != NONE &&
                sinceResume <= RTSP_FAILURE_WINDOW_MS &&
                !cancelled.asBoolean
        return when {
            !collides -> StageFailure.REPORT
            restarts < MAX_RESTARTS -> {
                heldError = errorCode
                heldSinceResumeMs = sinceResume
                held = true
                StageFailure.HOLD
            }
            else -> {
                collision = true
                BorealisLog.w(
                    LogTag.LAUNCH,
                    "rtsp failed again after the restart, giving up",
                    "error" to errorCode,
                    "since_resume_ms" to sinceResume,
                )
                LaunchAudit.resumeGaveUp(host.get(), errorCode)
                StageFailure.REPORT_COLLISION
            }
        }
    }

    /**
     * True, once, after a held failure: the caller starts again. False when nothing was held or the
     * start was cancelled.
     */
    fun restartAfterFailure(): Boolean {
        if (!held) return false
        held = false
        if (cancelled.asBoolean) return false
        restarts++
        resumeAcceptedAt = NONE
        BorealisLog.i(
            LogTag.LAUNCH,
            "rtsp failed after resume, restarting once",
            "error" to heldError,
            "since_resume_ms" to heldSinceResumeMs,
        )
        LaunchAudit.resumeRestarted(host.get(), heldError, SETTLE_WAIT_MS.toInt())
        return true
    }

    /** Whether the current start is the restart. */
    fun isRestart(): Boolean = restarts > 0

    /**
     * The restart never quits another device's app. Always false; the caller then reports the start
     * as failed, with the collision text.
     */
    fun refuseQuit(): Boolean {
        BorealisLog.w(LogTag.LAUNCH, "restart found another app running, not quitting it")
        collision = true
        LaunchAudit.resumeRefusedQuit(host.get())
        return false
    }

    /**
     * `/serverinfo` showed an app other than the requested one. Returns the result of [resolve],
     * which asks the user; a restart refuses instead. Never quits the other app on its own.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun differentAppRunning(resolve: LaunchAttempt): Boolean =
        if (isRestart()) refuseQuit() else resolve.run()

    /** Whether the start ended because another device was starting its stream. */
    fun collided(): Boolean = collision

    /** Waits in slices; false when cancelled or interrupted before the wait was over. */
    private fun waitInSlices(totalMs: Long): Boolean {
        var left = totalMs
        try {
            while (left > 0) {
                if (cancelled.asBoolean) return false
                val slice = minOf(SLICE_MS, left)
                sleeper(slice)
                left -= slice
            }
        } catch (e: InterruptedException) {
            BorealisLog.d(LogTag.LAUNCH, "resume wait interrupted")
            Thread.currentThread().interrupt()
            return false
        }
        return !cancelled.asBoolean
    }

    companion object {
        /** A `/serverinfo` answer this slow means the host was busy with another launch. */
        const val SLOW_SERVERINFO_MS = 3_000L
        const val SETTLE_WAIT_MS = 3_000L

        /** How soon after an accepted `/resume` an RTSP failure counts as a collision. */
        const val RTSP_FAILURE_WINDOW_MS = 5_000L
        const val MAX_RESTARTS = 1
        const val SLICE_MS = 250L

        /** `STAGE_RTSP_HANDSHAKE` of moonlight-common-c. */
        const val STAGE_RTSP_HANDSHAKE = 4

        /** The error moonlight-common-c gives when it cannot decrypt or parse the RTSP reply. */
        const val RTSP_UNREADABLE_REPLY = -1
        private const val NONE = -1L
        private const val NANOS_PER_MILLI = 1_000_000L

        /** [message], followed by the explanation when another device was starting its stream. */
        @JvmStatic
        fun withHint(collided: Boolean, message: String, getString: (Int) -> String): String =
            if (collided) message + "\n\n" + getString(R.string.launch_resume_collision)
            else message
    }
}
