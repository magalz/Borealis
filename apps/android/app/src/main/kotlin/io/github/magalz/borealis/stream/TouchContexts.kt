package io.github.magalz.borealis.stream

import android.view.View
import com.limelight.binding.input.touch.AbsoluteTouchContext
import com.limelight.binding.input.touch.RelativeTouchContext
import com.limelight.binding.input.touch.TouchContext
import com.limelight.binding.input.touch.TwoFingerTap
import com.limelight.nvstream.NvConnection
import com.limelight.preferences.PreferenceConfiguration

/** The touch contexts of the stream and the touch mode they follow. */
object TouchContexts {
    /** Only 2 touches are supported. */
    const val COUNT = 2

    // The reference screen the trackpad's pointer speed is scaled against.
    private const val REFERENCE_WIDTH = 1280
    private const val REFERENCE_HEIGHT = 720

    /** The mode the user's settings start with. */
    @JvmStatic
    fun initialMode(prefs: PreferenceConfiguration): TouchMode =
        if (prefs.touchscreenTrackpad) TouchMode.TRACKPAD else TouchMode.DIRECT

    /**
     * The contexts for [mode], one per supported touch; null for [TouchMode.OFF], which has none:
     * the activity ignores the touchscreen then.
     */
    @JvmStatic
    fun build(
        mode: TouchMode,
        connection: NvConnection,
        streamView: View,
        prefs: PreferenceConfiguration,
    ): Array<TouchContext>? =
        when (mode) {
            TouchMode.TRACKPAD -> {
                // The contexts share one two-finger tap: the one that decides it needs the time and
                // the movement of both fingers
                val twoFingerTap = TwoFingerTap()
                Array(COUNT) {
                    RelativeTouchContext(
                        connection,
                        it,
                        REFERENCE_WIDTH,
                        REFERENCE_HEIGHT,
                        streamView,
                        prefs,
                        twoFingerTap,
                    )
                }
            }
            // Native touch has the contexts of direct touch: the stream falls back to them when the
            // host takes no touch
            TouchMode.DIRECT,
            TouchMode.NATIVE -> Array(COUNT) { AbsoluteTouchContext(connection, it, streamView) }
            TouchMode.OFF -> null
        }

    /** The touch mode in force: the one of [session] while it streams, else the settings'. */
    @JvmStatic
    fun mode(session: StreamSession, prefs: PreferenceConfiguration): TouchMode =
        (session.state.value.phase as? StreamPhase.Streaming)?.touchMode ?: initialMode(prefs)
}
