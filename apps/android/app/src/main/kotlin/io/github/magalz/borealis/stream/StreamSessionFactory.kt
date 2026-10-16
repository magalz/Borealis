package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.pm.PackageManager
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.library.UNKNOWN_APP_NAME
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.host.StreamChromeHost

/** Builds what `Game` gives the stream session from the stream preferences and the connection. */
object StreamSessionFactory {
    /** What the stream starts with, from the user's settings. */
    @JvmStatic
    fun defaults(
        prefs: PreferenceConfiguration,
        hasTouchscreen: Boolean,
        showMenuHint: Boolean,
    ): StreamDefaults =
        StreamDefaults(
            overlay = if (prefs.enablePerfOverlay) OverlayMode.DETAILED else OverlayMode.OFF,
            touchMode = if (hasTouchscreen) TouchContexts.initialMode(prefs) else null,
            gamepadMouse = prefs.mouseEmulation,
            showMenuHint = showMenuHint,
        )

    /**
     * The step of the start that the connection's stage [reported] stands for. The first stage the
     * connection reports carries the app name, and it comes before the app is requested; the stages
     * of the stream itself (RTSP handshake, control, video, audio, input) come after it.
     */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun startStage(reported: String, appName: String): StartStage = StartStage.PREPARING_DISPLAY

    /**
     * The session of [activity]'s stream. The docked keyboard is for phones. [pcName] and [appName]
     * are what the intent carried; either can be missing, for example when a running session is
     * resumed. The placeholder name that the Library gives a resumed app it has no name for is
     * dropped here, so the screens never show it.
     */
    @JvmStatic
    fun create(
        activity: Activity,
        prefs: PreferenceConfiguration,
        appName: String?,
        pcName: String?,
        actions: StreamActions,
    ): StreamSession =
        StreamSession(
            appName = appName.takeUnless { it == UNKNOWN_APP_NAME }.orEmpty(),
            hostName = pcName.orEmpty(),
            defaults =
                defaults(
                    prefs,
                    activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
                    GameStreamActions.menuHintPending(activity),
                ),
            dockedKeyboard = StreamChromeHost.isPhone(activity),
            actions = actions,
        )

    /**
     * The negotiated stream: the size and the bitrate limit from [prefs], the [frameRate] and
     * [videoFormat] the connection agreed on, and the [decoder] the renderer picked, when it knows.
     */
    @JvmStatic
    fun sessionInfo(
        prefs: PreferenceConfiguration,
        frameRate: Int,
        videoFormat: Int,
        decoder: String?,
    ): SessionInfo =
        SessionInfo(
            width = prefs.width,
            height = prefs.height,
            frameRate = frameRate,
            codec =
                when {
                    videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264 != 0 -> "H.264"
                    videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265 != 0 -> "HEVC"
                    videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1 != 0 -> "AV1"
                    else -> "Unknown"
                },
            hdr = videoFormat and MoonBridge.VIDEO_FORMAT_MASK_10BIT != 0,
            decoder = decoder,
            bitrateLimitKbps = prefs.bitrate,
        )

    /** The host asked for a retry; [category] is the ordinal of `LaunchCategory` the stack gave. */
    @JvmStatic
    fun launchRetry(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int): LaunchRetry =
        LaunchRetry(LaunchCategory.entries.getOrNull(category), attempt, maxAttempts, delayMs)
}
