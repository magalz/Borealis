package io.github.magalz.borealis.host.launch

import io.github.magalz.borealis.nativecore.HostRules
import io.github.magalz.borealis.nativecore.NativeHostRules

/**
 * The frame rate for the `mode=WxHxFPS` parameter of `/launch` (host contract 7.4).
 *
 * LuminalShine rebuilds its virtual display when the `/launch` mode and the RTSP mode differ, and
 * Moonlight announces a different rate over RTSP than the one it asks `/launch` for when frame
 * pacing caps the rate. For hosts other than GFE the rate of the stream therefore goes into
 * `/launch` too; GFE keeps the upstream rule.
 */
object LaunchFrameRate {
    /**
     * [launchFps] is the rate the user selected, [streamFps] the one announced over RTSP. Falls
     * back to the upstream rule when the native rules are unavailable.
     */
    @JvmStatic
    fun forLaunch(isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
        forLaunch(NativeHostRules, isGfe, launchFps, streamFps)

    internal fun forLaunch(rules: HostRules, isGfe: Boolean, launchFps: Int, streamFps: Int): Int =
        rules.launchFps(isGfe, launchFps, streamFps) ?: upstream(isGfe, launchFps)

    /**
     * The expression of upstream `NvHTTP.launchApp`: above 60 Hz GFE would fall back to 720p60, so
     * it gets 0.
     */
    private fun upstream(isGfe: Boolean, launchFps: Int): Int =
        if (isGfe && launchFps > UPSTREAM_GFE_MAX_FPS) 0 else launchFps

    private const val UPSTREAM_GFE_MAX_FPS = 60
}
