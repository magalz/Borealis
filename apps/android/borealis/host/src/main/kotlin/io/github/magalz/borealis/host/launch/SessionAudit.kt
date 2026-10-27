package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.logging.AuditOutcome
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import java.util.concurrent.atomic.AtomicReference

/**
 * Audit events of a stream session (ADR 0050): the start with what was negotiated, and exactly one
 * end however many paths report it (a stop by the user and a termination by the connection can both
 * arrive). The first end names the reason: `disconnect` and `quit` when the user chose them in the
 * menu, `user` when the user left the stream screen in another way, `host_ended` and `error` when
 * the connection ended. Nothing about the stream content is recorded.
 */
object SessionAudit {
    private class Active(val host: String?, val startedAtMillis: Long)

    private val active = AtomicReference<Active?>(null)

    // A quit ends the stream on the host, so any termination may arrive before the quit answer.
    @Volatile private var quitPending = false

    internal var clock: () -> Long = System::currentTimeMillis

    internal fun reset() {
        active.set(null)
        quitPending = false
        clock = System::currentTimeMillis
    }

    /**
     * [videoFormat] is the negotiated `MoonBridge.VIDEO_FORMAT_*` value of the upstream decoder;
     * [name] and [address] label the host.
     */
    @Suppress("LongParameterList") // The call site in Game.kt passes the six values.
    @JvmStatic
    fun started(
        name: String?,
        address: String?,
        videoFormat: Int,
        width: Int,
        height: Int,
        fps: Int,
    ) {
        val host = BorealisAudit.hostLabel(name, address)
        quitPending = false
        active.set(Active(host, clock()))
        val hdr = videoFormat and MASK_10BIT != 0
        BorealisAudit.record(
            AuditType.SESSION_START,
            AuditOutcome.SUCCESS,
            host,
            "codec=${codec(videoFormat)} hdr=$hdr resolution=${width}x$height fps=$fps",
        )
    }

    /** The user left the stream. */
    @JvmStatic
    fun stopped() {
        end(AuditOutcome.SUCCESS) { "reason=user duration_s=$it" }
    }

    /** The user chose Disconnect in the menu: the stream ends and the app keeps running. */
    @JvmStatic
    fun disconnected() {
        end(AuditOutcome.SUCCESS) { "reason=disconnect duration_s=$it" }
    }

    /**
     * The user asked to quit the app on the host. A termination that follows, of any code, is the
     * quit.
     */
    @JvmStatic
    fun quitRequested() {
        quitPending = true
    }

    /** The quit did not happen and the stream goes on. */
    @JvmStatic
    fun quitFailed() {
        quitPending = false
    }

    /** The app on the host ended on the user's request, and with it the stream. */
    @JvmStatic
    fun quit() {
        end(AuditOutcome.SUCCESS) { "reason=quit duration_s=$it" }
    }

    /**
     * The connection ended; [errorCode] 0 is the host ending it gracefully. While a quit is under
     * way the end is the quit whatever the code: LuminalShine ends the stream with -1 as soon as it
     * gets the cancel, and answers the request seconds later.
     */
    @JvmStatic
    fun terminated(errorCode: Int) {
        if (quitPending) {
            end(AuditOutcome.SUCCESS) { "reason=quit duration_s=$it" }
        } else if (errorCode == GRACEFUL_TERMINATION) {
            end(AuditOutcome.SUCCESS) { "reason=host_ended duration_s=$it" }
        } else {
            end(AuditOutcome.FAILURE) { "reason=error code=$errorCode duration_s=$it" }
        }
    }

    private fun end(outcome: AuditOutcome, detail: (Long) -> String) {
        val session = active.getAndSet(null) ?: return
        val seconds = (clock() - session.startedAtMillis).coerceAtLeast(0) / MILLIS_PER_SECOND
        BorealisAudit.record(AuditType.SESSION_END, outcome, session.host, detail(seconds))
    }

    private fun codec(format: Int): String =
        when {
            format and MASK_H264 != 0 -> "H.264"
            format and MASK_H265 != 0 -> "HEVC"
            format and MASK_AV1 != 0 -> "AV1"
            else -> "unknown"
        }

    // The upstream MoonBridge.VIDEO_FORMAT_MASK_* values and ML_ERROR_GRACEFUL_TERMINATION; the
    // upstream class is not visible from this module.
    private const val MASK_H264 = 0x000F
    private const val MASK_H265 = 0x0F00
    private const val MASK_AV1 = 0xF000
    private const val MASK_10BIT = 0x2200
    private const val GRACEFUL_TERMINATION = 0
    private const val MILLIS_PER_SECOND = 1000L
}
