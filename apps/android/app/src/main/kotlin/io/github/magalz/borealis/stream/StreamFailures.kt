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
import io.github.magalz.borealis.host.video.StreamErrorHints

/**
 * Builds the [StreamFailure] the connection error screen shows from what the connection knows.
 *
 * The explanations the stream activity used to put in its error dialog (another device was starting
 * its stream, the network blocks Borealis, no video arrived, the LuminalShine hint) are not lost:
 * they go into the `portHint` that the error screen shows under the raw code, after the
 * classifier's own port text when it has one, separated by a blank line.
 */
object StreamFailures {
    private const val SEPARATOR = "\n\n"

    /**
     * The facts of a failed stage ([terminated] false, [stage] set) or of a connection that ended
     * ([terminated] true, no stage). [connection] says why a start failed; it may be missing.
     */
    @JvmStatic
    fun facts(
        stage: String?,
        errorCode: Int,
        portFlags: Int,
        terminated: Boolean,
        connection: NvConnection?,
    ): FailureFacts =
        FailureFacts(
            stage = stage,
            errorCode = errorCode,
            portFlags = portFlags,
            terminated = terminated,
            cause = connection?.lastFailureCause() ?: CauseKind.NONE,
            httpCode = connection?.lastHttpCode()?.takeIf { it != 0 },
            resumeCollision = connection?.endedByResumeCollision() ?: false,
            retriesExhausted = connection?.launchRetriesExhausted() ?: false,
        )

    /**
     * The facts of a failed stage or of a connection that ended, with [streamed] (the first frame
     * was on screen) and the fixed [note] the start path meant for the user.
     */
    @JvmStatic
    fun facts(
        stage: String?,
        errorCode: Int,
        portFlags: Int,
        terminated: Boolean,
        streamed: Boolean,
        note: String?,
        connection: NvConnection?,
    ): FailureFacts = facts(stage, errorCode, portFlags, terminated, connection)

    /**
     * The classified failure. [portBlocked] is the result of the network test: the connection of
     * this device is blocking Borealis.
     */
    @JvmStatic
    fun failure(context: Context, facts: FailureFacts, portBlocked: Boolean): StreamFailure =
        failure(
            facts,
            portBlocked,
            { id, args -> context.getString(id, *args) },
            { MoonBridge.stringifyPortFlags(it, ", ") },
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
                        else null,
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
