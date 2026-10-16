package io.github.magalz.borealis.stream.host

import android.view.KeyEvent
import android.view.MotionEvent
import io.github.magalz.borealis.stream.ChromeMode

/**
 * Decides whether a key or a motion event goes to the chrome or to the PC. [mode] is read again for
 * every event. The stream activity asks first and returns false for an event that is the chrome's,
 * so the activity default runs: Back reaches `onBackPressed`, and Compose gets the rest.
 */
class InputRoute(private val mode: () -> ChromeMode) {
    /** True when [event] belongs to the chrome. */
    fun toChrome(event: KeyEvent): Boolean = keyToChrome(mode(), event.keyCode)

    /** True when [event] belongs to the chrome. */
    @Suppress("UnusedParameter") // Overload of the key form; the mode alone decides.
    fun toChrome(event: MotionEvent): Boolean = motionToChrome(mode())

    companion object {
        /**
         * Only the modal chrome takes keys, all of them but the volume and the media keys, which
         * stay the system's. A docked panel leaves the keys to the stream.
         */
        fun keyToChrome(mode: ChromeMode, keyCode: Int): Boolean =
            mode == ChromeMode.MODAL && keyCode !in SYSTEM_KEYS

        /** Only the modal chrome takes motion: the stream keeps touches in every other mode. */
        fun motionToChrome(mode: ChromeMode): Boolean = mode == ChromeMode.MODAL

        private val SYSTEM_KEYS =
            setOf(
                KeyEvent.KEYCODE_VOLUME_UP,
                KeyEvent.KEYCODE_VOLUME_DOWN,
                KeyEvent.KEYCODE_VOLUME_MUTE,
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                KeyEvent.KEYCODE_MEDIA_STOP,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_REWIND,
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                KeyEvent.KEYCODE_MEDIA_PLAY,
                KeyEvent.KEYCODE_MEDIA_PAUSE,
                KeyEvent.KEYCODE_MEDIA_CLOSE,
                KeyEvent.KEYCODE_MEDIA_EJECT,
                KeyEvent.KEYCODE_MEDIA_RECORD,
                KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
                KeyEvent.KEYCODE_MEDIA_TOP_MENU,
                KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
                KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
            )
    }
}
