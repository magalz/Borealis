package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.nativecore.HostCaps
import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.LaunchAction
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.nativecore.LaunchDecision
import io.github.magalz.borealis.nativecore.LaunchVerb
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
    private val fps: (Boolean, Int, Int) -> Int? = { _, _, _ -> null },
) : HostRules {
    val decideCalls = mutableListOf<DecideCall>()
    val parseCalls = mutableListOf<Pair<String?, Int>>()

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

    override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int? =
        fps(isGfe, launchFps, streamFps)

    companion object {
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
                    verb == LaunchVerb.RESUME && status == 503 && "no running app" in text ->
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
