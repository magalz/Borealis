package io.github.magalz.borealis.host.failure

/**
 * The words that name a step of the HTTP start in the raw code of a failure, for support. They are
 * English and never change, and they are never the name of the app.
 */
object StartSteps {
    /** Asking the host for its state, before anything is requested. */
    const val SERVER_INFO = "serverinfo"

    /** The `/launch` and `/resume` requests use their own verb: `launch` and `resume`. */
    const val LAUNCH = "launch"
    const val RESUME = "resume"
}
