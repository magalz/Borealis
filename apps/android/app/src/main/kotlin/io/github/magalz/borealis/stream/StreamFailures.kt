package io.github.magalz.borealis.stream

import android.content.Context
import com.limelight.nvstream.NvConnection
import io.github.magalz.borealis.host.failure.FailureFacts
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure

/** Builds the [StreamFailure] the connection error screen shows from what the connection knows. */
object StreamFailures {
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
    ): FailureFacts = FailureFacts(stage, errorCode, portFlags, terminated)

    /**
     * The classified failure. [portBlocked] is the result of the network test: the connection of
     * this device is blocking Borealis.
     */
    @JvmStatic
    fun failure(context: Context, facts: FailureFacts, portBlocked: Boolean): StreamFailure =
        failure(facts, portBlocked, { id, args -> context.getString(id, *args) }, { "" })

    internal fun failure(
        facts: FailureFacts,
        portBlocked: Boolean,
        text: (Int, Array<Any>) -> String,
        portFlagsText: (Int) -> String,
    ): StreamFailure = StreamFailure(FailureKind.GENERIC, "", null)

    internal fun hint(
        facts: FailureFacts,
        portBlocked: Boolean,
        text: (Int, Array<Any>) -> String,
    ): String? = null
}
