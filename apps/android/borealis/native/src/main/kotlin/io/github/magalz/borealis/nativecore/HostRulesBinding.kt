package io.github.magalz.borealis.nativecore

/** [HostRules] on top of [HostNative]; every call goes through [NativeLibrary.call]. */
internal class HostRulesBinding(
    private val library: NativeLibrary,
    private val native: HostNative,
) : HostRules {
    override fun parseCaps(appVersion: String?, codecMask: Int): HostCaps? = TODO()

    override fun decideLaunch(
        verb: LaunchVerb,
        status: Int,
        message: String?,
        attemptsInCategory: Int,
    ): LaunchDecision? = TODO()

    override fun launchFps(isGfe: Boolean, launchFps: Int, streamFps: Int): Int? = TODO()
}

/** The host rules backed by `libborealis_jni.so`; all calls return null when it cannot load. */
object NativeHostRules : HostRules by HostRulesBinding(BorealisCore.library, CoreHostNative)
