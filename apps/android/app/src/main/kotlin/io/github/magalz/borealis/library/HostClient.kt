package io.github.magalz.borealis.library

import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.PairingManager.PairState
import java.security.cert.X509Certificate

/** The result of a pairing exchange: the state, and the host certificate to pin on success. */
internal class PairResult(val state: PairState, val serverCert: X509Certificate?)

/**
 * The requests to one host that the adapters make, over upstream `NvHTTP`. All of them block on the
 * network and throw `IOException` or `XmlPullParserException` on failure.
 */
internal interface HostClient {
    fun pairState(): PairState

    /** Asks the host to pair with [pin]; blocks until the user entered it on the host. */
    fun pair(pin: String): PairResult

    /** Asks the host to unpair this device; false when it did not confirm. */
    fun unpair(): Boolean

    /** The GameStream `/cancel`: true when the running app was quit. */
    fun quitApp(): Boolean

    /** Aborts the requests in flight; they fail with an `IOException`. */
    fun cancelPendingRequests()
}

internal fun interface HostClientFactory {
    /** Throws `IOException` when [details] has no active address. */
    fun create(details: ComputerDetails, uniqueId: String): HostClient
}
