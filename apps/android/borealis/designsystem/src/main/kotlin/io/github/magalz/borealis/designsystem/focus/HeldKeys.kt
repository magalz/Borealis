package io.github.magalz.borealis.designsystem.focus

import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * Consumes the key events of a press that began before the element entered the composition: a
 * surface opened by a held key (OK held on a tile, X, Menu) gets that key's repeats and its release
 * once it has the focus, and the release would click the item that has it. A press that begins
 * after the surface opened works as usual, and so does a press whose first event the element saw.
 * Put it on the root of a sheet or a dialog.
 *
 * Events without a down time (synthetic ones) and the volume and media keys are never consumed.
 */
fun Modifier.ignoreKeysHeldOnOpen(): Modifier = this then IgnoreKeysHeldOnOpenElement

private object IgnoreKeysHeldOnOpenElement : ModifierNodeElement<IgnoreKeysHeldOnOpenNode>() {
    override fun create() = IgnoreKeysHeldOnOpenNode()

    override fun update(node: IgnoreKeysHeldOnOpenNode) = Unit

    override fun equals(other: Any?) = other === this

    override fun hashCode() = System.identityHashCode(this)

    override fun InspectorInfo.inspectableProperties() {
        name = "ignoreKeysHeldOnOpen"
    }
}

private class IgnoreKeysHeldOnOpenNode : Modifier.Node(), KeyInputModifierNode {
    private var openedAt = 0L

    /** The keys whose press began under this element: their repeats and release are its own. */
    private val began = mutableSetOf<Int>()

    override fun onAttach() {
        openedAt = SystemClock.uptimeMillis()
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        val native = event.nativeKeyEvent
        val code = native.keyCode
        if (code in SYSTEM_KEYS) return false
        val ownPress =
            when {
                native.action == AndroidKeyEvent.ACTION_DOWN && native.repeatCount == 0 ->
                    true.also { began += code }
                native.action == AndroidKeyEvent.ACTION_UP -> began.remove(code)
                else -> code in began
            }
        return !ownPress && native.downTime in 1 until openedAt
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}

/** The volume and media keys stay the system's, whatever press they belong to. */
private val SYSTEM_KEYS =
    setOf(
        AndroidKeyEvent.KEYCODE_VOLUME_UP,
        AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
        AndroidKeyEvent.KEYCODE_VOLUME_MUTE,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_STOP,
        AndroidKeyEvent.KEYCODE_MEDIA_NEXT,
        AndroidKeyEvent.KEYCODE_MEDIA_PREVIOUS,
        AndroidKeyEvent.KEYCODE_MEDIA_REWIND,
        AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_PLAY,
        AndroidKeyEvent.KEYCODE_MEDIA_PAUSE,
        AndroidKeyEvent.KEYCODE_MEDIA_CLOSE,
        AndroidKeyEvent.KEYCODE_MEDIA_EJECT,
        AndroidKeyEvent.KEYCODE_MEDIA_RECORD,
        AndroidKeyEvent.KEYCODE_MEDIA_AUDIO_TRACK,
        AndroidKeyEvent.KEYCODE_MEDIA_TOP_MENU,
        AndroidKeyEvent.KEYCODE_MEDIA_SKIP_FORWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_STEP_FORWARD,
        AndroidKeyEvent.KEYCODE_MEDIA_STEP_BACKWARD,
    )
