package io.github.magalz.borealis.host.failure

/** Classifies a failed stream start or an ended stream for the connection error screen. */
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
        TODO("not implemented")
    }
}
