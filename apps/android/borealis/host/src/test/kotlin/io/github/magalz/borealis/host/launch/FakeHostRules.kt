package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.LaunchAction
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchDecision
import io.github.magalz.borealis.nativecore.LaunchVerb
import io.github.magalz.borealis.nativecore.PairAction
import io.github.magalz.borealis.nativecore.PairCategory
import io.github.magalz.borealis.nativecore.PairDecision
import java.io.IOException

/** A host answer as the upstream client raises it; stands in for `HostHttpResponseException`. */
class HostAnswer(val error: HostError) : IOException("host answered ${error.code}")

/** One call of [HostRules.decideLaunch], as the runner made it. */
data class DecideCall(
    val verb: LaunchVerb,
    val status: Int,
    val message: String?,
    val attempts: Int,
)

/**
 * A [HostRules] that answers like the core's retry table (`core/README.md`), so runner tests see
 * realistic categories, delays and attempt limits without the native library.
 */
class FakeHostRules(
    private val decide: (LaunchVerb, Int, String?, Int) -> LaunchDecision? = ::coreTable,
    private val caps: (String?, Int) -> HostCaps? = { _, _ -> null },
    private val pairing: (Int, String?) -> PairDecision? = ::corePairTable,
    private val fps: (Boolean, Int, Int) -> Int? = { _, _, _ -> null },
) : HostRules {
    val decideCalls = mutableListOf<DecideCall>()
    val parseCalls = mutableListOf<Pair<String?, Int>>()
    val pairCalls = mutableListOf<Pair<Int, String?>>()

    override fun parseCaps(appVersion: String?, codecMask: Int): HostCaps? {
        parseCalls += appVersion to codecMask
        return caps(appVersion, codecMask)
    }

    override fun decideLaunch(
        verb: LaunchVerb,
        status: Int,
        message: String?,
        attemptsInCategory: Int,
    ): LaunchDecision? {
        decideCalls += DecideCall(verb, status, message, attemptsInCategory)
        return decide(verb, status, message, attemptsInCategory)
    }

    override fun classifyPairing(status: Int, message: String?): PairDecision? =
        pairing(status, message).also { pairCalls += status to message }

    override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int? =
        fps(isGfe, launchFps, streamFps)

    companion object {
        private data class PairRule(
            val status: Int,
            val needle: String,
            val action: PairAction,
            val category: PairCategory,
        )

        private val PAIR_RULES =
            listOf(
                PairRule(
                    400,
                    "out of order call to getservercert",
                    PairAction.RESTART_ONCE,
                    PairCategory.STALE_SESSION,
                ),
                PairRule(
                    400,
                    "invalid uniqueid",
                    PairAction.RESTART_ONCE,
                    PairCategory.STALE_SESSION,
                ),
                PairRule(
                    400,
                    "pairing exchange invalidated",
                    PairAction.RESTART_ONCE,
                    PairCategory.EXCHANGE_INVALIDATED,
                ),
                PairRule(
                    410,
                    "host pairing state was reset",
                    PairAction.RESTART_ONCE,
                    PairCategory.HOST_RESET,
                ),
                PairRule(
                    409,
                    "pairing exchange already in progress",
                    PairAction.WAIT_HOST_SESSION,
                    PairCategory.IN_PROGRESS,
                ),
                PairRule(
                    409,
                    "pairing request identity mismatch",
                    PairAction.WAIT_HOST_SESSION,
                    PairCategory.IDENTITY_MISMATCH,
                ),
                PairRule(
                    503,
                    "too many pending pairing requests",
                    PairAction.WAIT_HOST_SESSION,
                    PairCategory.TOO_MANY_PENDING,
                ),
                PairRule(
                    403,
                    "pairing request peer mismatch",
                    PairAction.FAIL,
                    PairCategory.PEER_MISMATCH,
                ),
                PairRule(
                    409,
                    "pairing request superseded by retry",
                    PairAction.FAIL,
                    PairCategory.SUPERSEDED,
                ),
            )

        /** The pairing table of the core (core/src/pair.cpp). */
        fun corePairTable(status: Int, message: String?): PairDecision {
            val text = message.orEmpty().lowercase()
            val rule = PAIR_RULES.firstOrNull { it.status == status && it.needle in text }
            return rule?.let { PairDecision(it.action, it.category) }
                ?: PairDecision(PairAction.FAIL, PairCategory.UNKNOWN)
        }

        private val PENDING_DELAYS = listOf(1000, 2000, 3000, 5000)
        private val RESTORING_DELAYS = listOf(2000, 3000, 5000)
        private val GPU_DELAYS = listOf(5000, 5000)
        private val CAPTURE_DELAYS = listOf(3000)

        fun coreTable(
            verb: LaunchVerb,
            status: Int,
            message: String?,
            attempts: Int,
        ): LaunchDecision {
            val text = message.orEmpty().lowercase()
            val (category, delays) =
                when {
                    verb == LaunchVerb.LAUNCH &&
                        status == 400 &&
                        "an app is already running" in text ->
                        return LaunchDecision(
                            LaunchAction.RESOLVE_CONFLICT,
                            0,
                            LaunchCategory.APP_RUNNING,
                            0,
                        )
                    verb == LaunchVerb.RESUME &&
                        status == 503 &&
                        "no running app to resume" in text ->
                        return LaunchDecision(
                            LaunchAction.FALLBACK_TO_LAUNCH,
                            0,
                            LaunchCategory.NO_RUNNING_APP,
                            0,
                        )
                    status == 409 -> LaunchCategory.PENDING to PENDING_DELAYS
                    status == 503 && "restoring the display" in text ->
                        LaunchCategory.RESTORING_DISPLAY to RESTORING_DELAYS
                    status == 503 && "gpu/display stack is not ready" in text ->
                        LaunchCategory.GPU_NOT_READY to GPU_DELAYS
                    status == 503 -> LaunchCategory.CAPTURE_OR_DISPLAY to CAPTURE_DELAYS
                    else -> return LaunchDecision(LaunchAction.FAIL, 0, LaunchCategory.PERMANENT, 0)
                }
            return if (attempts < delays.size) {
                LaunchDecision(LaunchAction.RETRY, delays[attempts], category, delays.size)
            } else {
                LaunchDecision(LaunchAction.FAIL, 0, category, delays.size)
            }
        }
    }
}
