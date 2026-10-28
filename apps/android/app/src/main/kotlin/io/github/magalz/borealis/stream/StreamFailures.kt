package io.github.magalz.borealis.stream

import android.content.Context
import com.limelight.R
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.host.R as HostR
import io.github.magalz.borealis.host.failure.CauseKind
import io.github.magalz.borealis.host.failure.FailureFacts
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.host.failure.StreamFailureClassifier
import io.github.magalz.borealis.host.launch.ResumeCollisions
import io.github.magalz.borealis.host.video.StreamErrorHints

/**
 * Builds the [StreamFailure] the connection error screen shows from what the connection knows.
 *
 * The explanations the stream activity used to put in its error dialog (another device was starting
 * its stream, the network blocks Borealis, no video arrived, the LuminalShine hint) are not lost:
 * they go into the `portHint` that the error screen shows under the raw code, after the
 * classifier's own port text when it has one, separated by a blank line. The fixed texts of the
 * start path (for example that the session was not started by this device) join them. The text of
 * an exception never does: it goes to the log, where addresses are masked.
 */
object StreamFailures {
    private const val SEPARATOR = "\n\n"

    /**
     * What the activity knows of its session when the connection fails or ends. [streamed] says
     * that the first frame was on screen before it ended, and [note] is the fixed text the start
     * meant for the user. [connection] says why a start failed; it may be missing.
     */
    class SessionState(val streamed: Boolean, val note: String?, val connection: NvConnection?)

    /**
     * The facts of a failed [stage], or of a connection that ended (no stage: it is terminated).
     */
    @JvmStatic
    @JvmOverloads
    fun facts(
        stage: String?,
        errorCode: Int,
        portFlags: Int,
        session: SessionState,
        rtspStageName: () -> String? = {
            MoonBridge.getStageName(ResumeCollisions.STAGE_RTSP_HANDSHAKE)
        },
    ): FailureFacts {
        val terminated = stage == null
        val connection = session.connection
        return FailureFacts(
            stage = stage,
            errorCode = errorCode,
            portFlags = portFlags,
            terminated = terminated,
            cause = connection?.lastFailureCause() ?: CauseKind.NONE,
            httpCode = connection?.lastHttpCode()?.takeIf { it != 0 },
            resumeCollision = connection?.endedByResumeCollision() ?: false,
            retriesExhausted = connection?.launchRetriesExhausted() ?: false,
            rtspReplyUnreadable =
                !terminated &&
                    errorCode == ResumeCollisions.RTSP_UNREADABLE_REPLY &&
                    stage == rtspStageName(),
            streamed = session.streamed,
            note = session.note,
        )
    }

    /**
     * The classified failure. [portBlocked] is the result of the network test: the connection of
     * this device is blocking Borealis.
     */
    @JvmStatic
    @Suppress("SpreadOperator") // Kotlin copies the array to pass it to Java's getString varargs.
    fun failure(context: Context, facts: FailureFacts, portBlocked: Boolean): StreamFailure =
        failure(
            facts,
            portBlocked,
            { id, args -> context.getString(id, *args) },
            { checkNotNull(MoonBridge.stringifyPortFlags(it, ", ")) },
        )

    internal fun failure(
        facts: FailureFacts,
        portBlocked: Boolean,
        text: (Int, Array<Any>) -> String,
        portFlagsText: (Int) -> String,
    ): StreamFailure {
        val classified = StreamFailureClassifier.classify(facts, text, portFlagsText)
        val portHint =
            listOfNotNull(classified.portHint, hint(facts, portBlocked, text))
                .joinToString(SEPARATOR)
                .ifEmpty { null }
        return classified.copy(portHint = portHint)
    }

    internal fun hint(
        facts: FailureFacts,
        portBlocked: Boolean,
        text: (Int, Array<Any>) -> String,
    ): String? {
        val plain = { id: Int -> text(id, emptyArray()) }
        val blocked = if (portBlocked) plain(R.string.nettest_text_blocked) else null
        return when {
            !facts.terminated ->
                listOfNotNull(
                        if (facts.resumeCollision) plain(HostR.string.launch_resume_collision)
                        else if (facts.rtspReplyUnreadable)
                            plain(HostR.string.failure_reply_unreadable)
                        else null,
                        facts.note,
                        blocked,
                    )
                    .joinToString(SEPARATOR)
                    .ifEmpty { null }
            // A blocked network supersedes any other explanation of an ended stream.
            blocked != null -> blocked
            else ->
                terminationText(facts.errorCode, plain)?.let {
                    StreamErrorHints.withHint(facts.errorCode, it, plain)
                }
        }
    }

    private fun terminationText(errorCode: Int, plain: (Int) -> String): String? =
        when (errorCode) {
            MoonBridge.ML_ERROR_NO_VIDEO_TRAFFIC -> plain(R.string.no_video_received_error)
            MoonBridge.ML_ERROR_NO_VIDEO_FRAME -> plain(R.string.no_frame_received_error)
            MoonBridge.ML_ERROR_UNEXPECTED_EARLY_TERMINATION,
            MoonBridge.ML_ERROR_PROTECTED_CONTENT -> plain(R.string.early_termination_error)
            MoonBridge.ML_ERROR_FRAME_CONVERSION -> plain(R.string.frame_conversion_error)
            else -> null
        }
}
