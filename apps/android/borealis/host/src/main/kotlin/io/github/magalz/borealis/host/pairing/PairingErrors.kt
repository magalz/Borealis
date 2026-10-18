package io.github.magalz.borealis.host.pairing

import android.content.Context
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.host.launch.HostError
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.NativeHostRules
import io.github.magalz.borealis.nativecore.PairAction
import io.github.magalz.borealis.nativecore.PairCategory

/** What `doPair` shows after a failed pairing step and whether it pairs again. */
data class PairingFailure(val message: String?, val restart: Boolean)

/**
 * What [PairingErrors.describe] takes from its surroundings: the host rules and the app strings.
 */
internal class PairingEnvironment(val rules: HostRules, val getString: (Int) -> String)

/** The host's pairing errors as the user should see them (host contract 4.2 and 4.3). */
object PairingErrors {
    /**
     * [error] is the host answer behind the failure, or null for any other exception; [fallback] is
     * the upstream text that stays for everything this class does not know. [restarted] is true for
     * the attempt that already is the automatic restart. [host] labels the audit events.
     */
    @JvmStatic
    fun describe(
        context: Context,
        error: HostError?,
        fallback: String?,
        restarted: Boolean,
        host: String?,
    ): PairingFailure =
        describe(
            PairingEnvironment(NativeHostRules) { context.getString(it) },
            error,
            fallback,
            restarted,
            host,
        )

    internal fun describe(
        environment: PairingEnvironment,
        error: HostError?,
        fallback: String?,
        restarted: Boolean,
        host: String? = null,
    ): PairingFailure {
        val decision = error?.let { environment.rules.classifyPairing(it.code, it.message) }
        val text = decision?.let { textFor(it.category, restarted) }
        if (error == null || decision == null || text == null) {
            PairingAudit.failed(host, error?.code, decision, restarted)
            return PairingFailure(fallback, restart = false)
        }
        // Status and category only: the host text and anything near a PIN stay out of the log.
        BorealisLog.i(
            LogTag.PAIR,
            "host pairing error classified",
            "status" to error.code,
            "category" to decision.category,
            "action" to decision.action,
            "restarted" to restarted,
        )
        val restart = decision.action == PairAction.RESTART_ONCE && !restarted
        PairingAudit.failed(host, error.code, decision, restarted)
        if (restart) PairingAudit.restarting(host, decision)
        return PairingFailure(environment.getString(text), restart)
    }

    /** The message of [category]; null keeps the upstream text. */
    private fun textFor(category: PairCategory, restarted: Boolean): Int? =
        when (category) {
            PairCategory.UNKNOWN -> null
            PairCategory.STALE_SESSION,
            PairCategory.EXCHANGE_INVALIDATED,
            PairCategory.HOST_RESET ->
                if (restarted) R.string.pair_restart_failed_again else R.string.pair_restart_cleared
            PairCategory.IN_PROGRESS -> R.string.pair_host_session_in_progress
            PairCategory.IDENTITY_MISMATCH -> R.string.pair_host_other_device
            PairCategory.TOO_MANY_PENDING -> R.string.pair_host_too_many_pending
            PairCategory.PEER_MISMATCH -> R.string.pair_host_peer_mismatch
            PairCategory.SUPERSEDED -> R.string.pair_host_superseded
        }
}
