package io.github.magalz.borealis.nativecore

import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogTag

/** [HostRules] on top of [HostNative]; every call goes through [NativeLibrary.call]. */
internal class HostRulesBinding(
    private val library: NativeLibrary,
    private val native: HostNative,
) : HostRules {
    override fun parseCaps(appVersion: String?, codecMask: Int): HostCaps? {
        val bytes = appVersion?.encodeToByteArray()
        if (bytes != null && bytes.size > APP_VERSION_MAX_BYTES) {
            // Never log the input itself, only its size.
            BorealisLog.w(LogTag.CORE, "appversion too long", "bytes" to bytes.size)
            return null
        }
        return library.call("parseCaps") {
            HostMarshalling.decodeCaps(native.parseCaps(bytes, codecMask))
        }
    }

    override fun decideLaunch(
        verb: LaunchVerb,
        status: Int,
        message: String?,
        attemptsInCategory: Int,
    ): LaunchDecision? =
        library.call("decideLaunch") {
            val raw =
                native.decideLaunch(
                    verb.ordinal,
                    status,
                    HostMarshalling.encodeMessage(message),
                    HostMarshalling.clampAttempts(attemptsInCategory),
                )
            HostMarshalling.decodeDecision(raw)
        }

    override fun classifyPairing(status: Int, message: String?): PairDecision? =
        library.call("classifyPairing") {
            HostMarshalling.decodePairDecision(
                native.classifyPairing(status, HostMarshalling.encodeMessage(message))
            )
        }

    override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int? =
        library.call("launchFps") { native.launchFps(isGfe, launchFps, streamFps) }
}

/** The host rules backed by `libborealis_jni.so`; all calls return null when it cannot load. */
object NativeHostRules : HostRules by HostRulesBinding(BorealisCore.library, CoreHostNative)
