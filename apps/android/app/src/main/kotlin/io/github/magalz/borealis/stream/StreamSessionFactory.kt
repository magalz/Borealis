package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.pm.PackageManager
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.stream.host.StreamChromeHost
import kotlin.math.abs

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
            touchMode =
                when {
                    !hasTouchscreen -> null
                    prefs.touchscreenTrackpad -> TouchMode.TRACKPAD
                    else -> TouchMode.DIRECT
                },
            gamepadMouse = prefs.mouseEmulation,
            showMenuHint = showMenuHint,
        )

    /**
     * The session of [activity]'s stream. The docked keyboard is for phones. [pcName] and [appName]
     * are what the intent carried; either can be missing, for example when a running session is
     * resumed.
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
            appName = appName.orEmpty(),
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

    /** The negotiated stream; the decoder is not known to the activity yet. */
    @JvmStatic
    fun sessionInfo(
        width: Int,
        height: Int,
        frameRate: Int,
        videoFormat: Int,
        bitrateLimitKbps: Int,
    ): SessionInfo =
        SessionInfo(
            width = width,
            height = height,
            frameRate = frameRate,
            codec =
                when {
                    videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H264 != 0 -> "H.264"
                    videoFormat and MoonBridge.VIDEO_FORMAT_MASK_H265 != 0 -> "HEVC"
                    videoFormat and MoonBridge.VIDEO_FORMAT_MASK_AV1 != 0 -> "AV1"
                    else -> "Unknown"
                },
            hdr = videoFormat and MoonBridge.VIDEO_FORMAT_MASK_10BIT != 0,
            decoder = null,
            bitrateLimitKbps = bitrateLimitKbps,
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
        sessionInfo(prefs.width, prefs.height, frameRate, videoFormat, prefs.bitrate)

    /**
     * A failure of the connection, unclassified for now. The raw code is the support text: the
     * error code, large codes in hex as upstream shows them, and the [stage] when there is one.
     */
    @JvmStatic
    fun failure(stage: String?, errorCode: Int, portHint: String?): StreamFailure {
        val code =
            if (abs(errorCode) > HEX_THRESHOLD) Integer.toHexString(errorCode) else "$errorCode"
        val rawCode = if (stage == null) "Error $code" else "Error $code · $stage"
        return StreamFailure(FailureKind.GENERIC, rawCode, portHint?.takeIf { it.isNotEmpty() })
    }

    /** The host asked for a retry; [category] is the ordinal of `LaunchCategory` the stack gave. */
    @JvmStatic
    fun launchRetry(category: Int, attempt: Int, maxAttempts: Int, delayMs: Int): LaunchRetry =
        LaunchRetry(LaunchCategory.entries.getOrNull(category), attempt, maxAttempts, delayMs)

    private const val HEX_THRESHOLD = 1000
}
