package io.github.magalz.borealis.library.testing

import io.github.magalz.borealis.library.domain.PairingInstructions
import io.github.magalz.borealis.library.domain.PairingOutcome
import io.github.magalz.borealis.library.domain.PairingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred

/** One call of [FakePairingService.pair]: it stays pending until [outcome] is completed. */
class PairCall(val hostId: String, val pin: String, val restarted: Boolean) {
    val outcome = CompletableDeferred<PairingOutcome>()

    /** True when the calling coroutine was cancelled, which aborts the real host request. */
    var cancelled = false
        internal set
}

class FakePairingService : PairingService {
    /** What [prepare] answers, one entry per call, in order. */
    val prepared = ArrayDeque<PairingInstructions?>()

    val prepareCalls = mutableListOf<String>()
    val pairCalls = mutableListOf<PairCall>()
    val expiredHostIds = mutableListOf<String>()

    override suspend fun prepare(hostId: String): PairingInstructions? {
        prepareCalls += hostId
        return prepared.removeFirst()
    }

    override suspend fun pair(hostId: String, pin: String, restarted: Boolean): PairingOutcome {
        val call = PairCall(hostId, pin, restarted)
        pairCalls += call
        try {
            return call.outcome.await()
        } catch (e: CancellationException) {
            call.cancelled = true
            throw e
        }
    }

    override fun recordExpired(hostId: String) {
        expiredHostIds += hostId
    }
}
