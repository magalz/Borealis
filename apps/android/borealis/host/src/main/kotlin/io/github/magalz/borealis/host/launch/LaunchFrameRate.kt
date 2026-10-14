package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.NativeHostRules

object LaunchFrameRate {
    @JvmStatic
    fun forLaunch(isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
        forLaunch(NativeHostRules, isGfe, launchFps, streamFps)

    internal fun forLaunch(rules: HostRules, isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
        TODO("not implemented")
}
