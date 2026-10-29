package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.pm.PackageManager
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.library.UNKNOWN_APP_NAME
import io.github.magalz.borealis.nativecore.LaunchCategory
import io.github.magalz.borealis.settings.appearance.borealisLook
import io.github.magalz.borealis.settings.catalog.Settings
import io.github.magalz.borealis.settings.repository.SettingsSnapshot
import io.github.magalz.borealis.settings.store.SettingsSnapshots
import io.github.magalz.borealis.stream.host.StreamChromeHost
import java.util.function.BooleanSupplier

/** Builds what `Game` gives the stream session from the stream preferences and the connection. */
object StreamSessionFactory {
    /** What the stream starts with, from the user's settings. */
    @JvmStatic
    fun defaults(
        prefs: PreferenceConfiguration,
        perfOverlay: String,
        hasTouchscreen: Boolean,
        showMenuHint: Boolean,
    ): StreamDefaults =
        StreamDefaults(
            overlay = overlayOf(perfOverlay),
            touchMode = if (hasTouchscreen) TouchContexts.initialMode(prefs) else null,
            gamepadMouse = prefs.mouseEmulation,
            showMenuHint = showMenuHint,
        )

    /**
     * The step of the start that the connection's stage [reported] stands for. The first stage the
     * connection reports carries the app name and only says that the start began, so Connecting
     * stays current until the app is requested (`launchStarting`); the stages of the stream itself
     * (RTSP handshake, control, video, audio, input) come after it.
     */
    @JvmStatic
    fun startStage(reported: String, appName: String): StartStage =
        if (reported == appName) StartStage.CONNECTING else StartStage.PREPARING_DISPLAY

    /** The overlay that a stored `borealis_perf_overlay` value stands for; unknown means off. */
    private fun overlayOf(value: String): OverlayMode =
        when (value) {
            Settings.PERF_OVERLAY_COMPACT -> OverlayMode.COMPACT
            Settings.PERF_OVERLAY_DETAILED -> OverlayMode.DETAILED
            else -> OverlayMode.OFF
        }

    /**
     * The session of [activity]'s stream. Its keyboard docks on a phone with no game controller,
     * decided each time the keyboard opens; [usbPadHeld] tells then whether the app's USB driver
     * holds a pad. [pcName] and [appName] are what the intent carried; either can be missing, for
     * example when a running session is resumed. The placeholder name that the Library gives a
     * resumed app it has no name for is dropped here, so the screens never show it.
     */
    @JvmStatic
    @Suppress("LongParameterList") // The call site in Game.kt passes what the activity has.
    fun create(
        activity: Activity,
        prefs: PreferenceConfiguration,
        appName: String?,
        pcName: String?,
        actions: StreamActions,
        usbPadHeld: BooleanSupplier,
    ): StreamSession =
        StreamSession(
            appName = appName.takeUnless { it == UNKNOWN_APP_NAME }.orEmpty(),
            hostName = pcName.orEmpty(),
            defaults =
                defaults(
                    prefs,
                    SettingsSnapshots.current(activity)[Settings.PERF_OVERLAY],
                    activity.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
                    GameStreamActions.menuHintPending(activity),
                ),
            dockKeyboard = { StreamChromeHost.dockedKeyboard(activity, usbPadHeld.asBoolean) },
            actions = actions,
        )

    /** What the stream draws besides the video, from [snapshot]: Blur, Moving and the style. */
    @JvmStatic fun visuals(snapshot: SettingsSnapshot): StreamVisuals = StreamVisuals()

    /**
     * The look that the stream chrome draws with: the one that the settings store now, mapped as
     * the Library maps it. `Game` reads it once, when the stream starts.
     */
    @JvmStatic
    fun look(activity: Activity): BorealisLook = borealisLook(SettingsSnapshots.current(activity))

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
