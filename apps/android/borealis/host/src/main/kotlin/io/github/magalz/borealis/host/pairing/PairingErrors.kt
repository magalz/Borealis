package io.github.magalz.borealis.host.pairing

import android.content.Context
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.NativeHostRules

/** What `doPair` shows after a failed pairing step and whether it pairs again. */
data class PairingFailure(val message: String?, val restart: Boolean)

/** The host's pairing errors as the user should see them (host contract 4.2 and 4.3). */
object PairingErrors {
    /**
     * [error] is the host answer behind the failure, or null for any other exception; [fallback] is
     * the upstream text that stays for everything this class does not know. [restarted] is true for
     * the attempt that already is the automatic restart.
     */
    @JvmStatic
    fun describe(
        context: Context,
        error: HostError?,
        fallback: String?,
        restarted: Boolean,
    ): PairingFailure =
        describe(NativeHostRules, error, fallback, restarted) { context.getString(it) }

    internal fun describe(
        rules: HostRules,
        error: HostError?,
        fallback: String?,
        restarted: Boolean,
        getString: (Int) -> String,
    ): PairingFailure = PairingFailure(fallback, restart = false)
}
