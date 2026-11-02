package io.github.magalz.borealis.computers

import android.net.ConnectivityManager
import android.net.Network
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowConnectivityManager

/**
 * The stock Robolectric shadow of `ConnectivityManager`, with one call that can be made to throw,
 * as a platform does that refuses a call. Set [failing] before the call; [thrown] says whether it
 * threw, so that a test cannot pass because the call was never made.
 *
 * Use it with `@Config(shadows = [ShadowFailingConnectivityManager::class])` and read it with
 * `Shadow.extract`.
 */
@Implements(ConnectivityManager::class)
class ShadowFailingConnectivityManager : ShadowConnectivityManager() {
    /** The calls that can throw. */
    enum class Call {
        /** `getAllNetworks`. */
        ALL_NETWORKS,

        /** `bindProcessToNetwork` with a network. Freeing the process (null) does not throw. */
        BIND_TO_NETWORK,

        /** `bindProcessToNetwork(null)`, which gives the process back to the platform. */
        UNBIND,
    }

    /** The call that throws, or null when none does. */
    var failing: Call? = null

    /** How many calls threw. */
    var thrown = 0
        private set

    @Implementation
    override fun getAllNetworks(): Array<Network> {
        fail(Call.ALL_NETWORKS)
        return super.getAllNetworks()
    }

    @Implementation
    override fun bindProcessToNetwork(network: Network?): Boolean {
        fail(if (network == null) Call.UNBIND else Call.BIND_TO_NETWORK)
        return super.bindProcessToNetwork(network)
    }

    private fun fail(call: Call) {
        if (failing == call) {
            thrown++
            throw SecurityException("The platform refuses $call")
        }
    }
}
