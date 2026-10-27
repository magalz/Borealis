package io.github.magalz.borealis.stream.game.keys

import android.graphics.Insets
import android.os.SystemClock
import android.util.SparseArray
import android.view.InputDevice
import android.view.KeyEvent
import android.view.PointerIcon
import android.view.View
import android.view.WindowInsets
import com.limelight.R
import com.limelight.binding.input.KeyboardTranslator
import com.limelight.binding.input.capture.InputCaptureProvider
import com.limelight.nvstream.jni.MoonBridge
import io.github.magalz.borealis.connection.ShadowRecordingBridge
import io.github.magalz.borealis.designsystem.testing.connect
import io.github.magalz.borealis.designsystem.testing.inputDevice
import io.github.magalz.borealis.stream.game.GameRig
import io.github.magalz.borealis.stream.game.session
import java.util.concurrent.TimeUnit
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowInputEvent
import org.robolectric.shadows.ShadowLooper
import org.robolectric.util.ReflectionHelpers

/** The id of the hardware keyboard that the key events come from. */
internal const val KEYBOARD_ID = 11

/** The flag that tells the host that a key code comes from no layout that the app knows. */
internal const val NON_NORMALIZED = MoonBridge.SS_KBE_FLAG_NON_NORMALIZED

/**
 * One key that reached the host: the key code with its prefix, the direction, the byte of the held
 * modifiers and the flags.
 */
internal data class SentKey(
    val code: Int,
    val direction: Byte,
    val modifiers: Byte,
    val flags: Byte,
)

/** The keys that reached the native library since the last [forgetCalls], in order. */
internal fun sentKeys(): List<SentKey> =
    ShadowRecordingBridge.named("sendKeyboardInput").map {
        SentKey(
            (it.args[0] as Short).toInt() and 0xFFFF,
            it.args[1] as Byte,
            it.args[2] as Byte,
            it.args[3] as Byte,
        )
    }

/** The texts that reached the native library as UTF-8 text, in order. */
internal fun sentTexts(): List<String> =
    ShadowRecordingBridge.named("sendUtf8Text").map {
        String(it.args[0] as ByteArray, Charsets.UTF_8)
    }

/** The mouse button events that reached the native library: the event and the button. */
internal fun sentMouseButtons(): List<Pair<Byte, Byte>> =
    ShadowRecordingBridge.named("sendMouseButton").map { it.args[0] as Byte to it.args[1] as Byte }

/** The button flags of the controller packets that reached the native library, in order. */
internal fun sentPadButtons(): List<Int> =
    ShadowRecordingBridge.named("sendMultiControllerInput").map { it.args[2] as Int }

/** The calls that sent something to the host (every native that sends starts with "send"). */
internal fun sentCalls(): List<String> =
    ShadowRecordingBridge.names.filter { it.startsWith("send") }

/** Forgets what reached the native library until now, so a test sees only what it did. */
internal fun forgetCalls() {
    ShadowRecordingBridge.calls.clear()
}

/** Runs what the main looper has scheduled in the next [millis] milliseconds. */
internal fun idle(millis: Long = 0L) {
    ShadowLooper.idleMainLooper(millis, TimeUnit.MILLISECONDS)
}

/**
 * What a key event carries. [unicode] is what the layout makes of the key: the layout of
 * Robolectric knows no accents, and a test that needs one gives it here.
 */
internal data class KeySpec(
    val action: Int,
    val code: Int,
    val meta: Int = 0,
    val repeat: Int = 0,
    val flags: Int = 0,
    val source: Int = InputDevice.SOURCE_KEYBOARD,
    val deviceId: Int = KEYBOARD_ID,
    val unicode: Int? = null,
)

/** A key event that answers with the [KeySpec] where the framework would ask the layout. */
private class ScriptedKeyEvent(private val spec: KeySpec, time: Long) :
    KeyEvent(
        time - (if (spec.action == KeyEvent.ACTION_UP) HELD_MS else 0L),
        time,
        spec.action,
        spec.code,
        spec.repeat,
        spec.meta,
        spec.deviceId,
        0,
        spec.flags,
        spec.source,
    ) {
    override fun getUnicodeChar(): Int = spec.unicode ?: super.getUnicodeChar()

    private companion object {
        // A key that was down for a moment: the pad handler waits for a short press to be long
        // enough.
        const val HELD_MS = 100L
    }
}

/** The event that [spec] describes, made now, with no device. */
internal fun keyEvent(spec: KeySpec): KeyEvent = ScriptedKeyEvent(spec, SystemClock.uptimeMillis())

/** The characters that a software keyboard sends in one event, with no key code of their own. */
@Suppress("DEPRECATION") // ACTION_MULTIPLE is how the activity gets these.
internal fun textKey(characters: String): KeyEvent =
    KeyEvent(SystemClock.uptimeMillis(), characters, KEYBOARD_ID, 0)

/**
 * A key of the hardware keyboard: a device with letters, which the activity sends to the host as a
 * key.
 */
internal fun keyboardKey(spec: KeySpec): KeyEvent =
    keyEvent(spec).also {
        val device =
            inputDevice(
                spec.deviceId,
                InputDevice.SOURCE_KEYBOARD,
                keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
            )
        Shadow.extract<ShadowInputEvent>(it).setDevice(device)
    }

/** A key of the hardware keyboard, with the traits that a test most often changes. */
internal fun keyboardKey(
    action: Int,
    code: Int,
    meta: Int = 0,
    repeat: Int = 0,
    flags: Int = 0,
): KeyEvent = keyboardKey(KeySpec(action, code, meta, repeat, flags))

/**
 * A key of a gamepad, which Android reports without a device of its own and under the id 0: the
 * controller handler has its default context for it.
 */
internal fun padKey(
    action: Int,
    code: Int,
    source: Int = InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_KEYBOARD,
): KeyEvent = keyEvent(KeySpec(action, code, source = source, deviceId = 0))

/** The synthetic Back that Android makes of a right click: a key event from a mouse source. */
internal fun mouseBack(action: Int, source: Int, flags: Int = 0): KeyEvent =
    keyEvent(KeySpec(action, KeyEvent.KEYCODE_BACK, flags = flags, source = source, deviceId = 0))

/** A key press and its release. */
internal fun GameRig.tap(code: Int, meta: Int = 0): List<Boolean> =
    listOf(
        game.dispatchKeyEvent(keyboardKey(KeyEvent.ACTION_DOWN, code, meta)),
        game.dispatchKeyEvent(keyboardKey(KeyEvent.ACTION_UP, code, meta)),
    )

/** The key goes down on the hardware keyboard, as the window delivers it. */
internal fun GameRig.down(code: Int, meta: Int = 0): Boolean =
    game.dispatchKeyEvent(keyboardKey(KeyEvent.ACTION_DOWN, code, meta))

/** The key comes up on the hardware keyboard, as the window delivers it. */
internal fun GameRig.up(code: Int, meta: Int = 0): Boolean =
    game.dispatchKeyEvent(keyboardKey(KeyEvent.ACTION_UP, code, meta))

/** Ctrl, Alt and Shift go down, left ones, in this order. */
internal fun GameRig.holdCtrlAltShift() {
    down(KeyEvent.KEYCODE_CTRL_LEFT)
    down(KeyEvent.KEYCODE_ALT_LEFT)
    down(KeyEvent.KEYCODE_SHIFT_LEFT)
}

/** Shift, Alt and Ctrl come up, in this order. */
internal fun GameRig.releaseCtrlAltShift() {
    up(KeyEvent.KEYCODE_SHIFT_LEFT)
    up(KeyEvent.KEYCODE_ALT_LEFT)
    up(KeyEvent.KEYCODE_CTRL_LEFT)
}

/**
 * The whole chord of [code] with Ctrl, Alt and Shift: the three go down, then the key, the key
 * comes up and the three come up. The keys that the activity eats do not reach the host.
 */
internal fun GameRig.chord(code: Int) {
    holdCtrlAltShift()
    down(code)
    up(code)
    releaseCtrlAltShift()
}

/** The menu opens, and with it the chrome takes the input from the stream. */
internal fun GameRig.openChrome() {
    session.openMenu()
    idle()
}

/** The menu closes, and the chrome gives the input back, from a posted task. */
internal fun GameRig.closeChrome() {
    session.closePanel()
    idle()
}

/** The input capture provider of the activity: where the pointer capture and the cursor go. */
internal val GameRig.capture: InputCaptureProvider
    get() = field("inputCaptureProvider")

/** Replaces the input capture provider of the activity with [provider], which a test observes. */
internal fun GameRig.captureWith(provider: InputCaptureProvider) {
    ReflectionHelpers.setField(game, "inputCaptureProvider", provider)
}

/** Whether the pointer of the mouse is captured: the stream has the input and grabbed it. */
internal val GameRig.pointerCaptured: Boolean
    get() = capture.isCapturingActive()

/**
 * The pointer icon of the stream view: none while the cursor shows, the null icon while it hides.
 */
internal val GameRig.cursorIcon: PointerIcon?
    get() = game.findViewById<View>(R.id.surfaceView).pointerIcon

/** Whether the cursor of the mouse is shown over the stream. */
internal val GameRig.cursorShown: Boolean
    get() = cursorIcon == null

/**
 * Gives the keyboard with the id [deviceId] a layout of its own: [placed] are pairs of the key code
 * that the device sends and the key code of the QWERTY key at its place. The translator reads the
 * layout of a device from the framework, which Robolectric does not know; this puts it where the
 * translator keeps it.
 */
internal fun GameRig.keyboardLayout(deviceId: Int, vararg placed: Pair<Int, Int>) {
    connect(
        application,
        inputDevice(
            deviceId,
            InputDevice.SOURCE_KEYBOARD,
            keyboardType = InputDevice.KEYBOARD_TYPE_ALPHABETIC,
        ),
    )
    val translator = field<KeyboardTranslator>("keyboardTranslator")
    // The translator reads the layout of the device when it is added, which makes an empty one.
    translator.onInputDeviceAdded(deviceId)
    val mappings = ReflectionHelpers.getField<SparseArray<Any>>(translator, "keyboardMappings")
    val table =
        ReflectionHelpers.getField<IntArray>(
            checkNotNull(mappings.get(deviceId)),
            "deviceKeyCodeToQwertyKeyCode",
        )
    for ((deviceKey, qwertyKey) in placed) table[deviceKey] = qwertyKey
}

private const val IME_HEIGHT = 100

/**
 * The Android keyboard shows or hides in the window of the activity, as far as the activity can
 * ask: the insets of the window say so. The window insets of Robolectric know no keyboard, so the
 * insets of the window are the ones that a keyboard on screen would bring.
 */
internal fun GameRig.androidKeyboardShown(shown: Boolean) {
    val decor = game.window.decorView
    val root = checkNotNull(ReflectionHelpers.callInstanceMethod<Any?>(decor, "getViewRootImpl"))
    val insets =
        WindowInsets.Builder()
            .setInsets(WindowInsets.Type.ime(), Insets.of(0, 0, 0, IME_HEIGHT))
            .setVisible(WindowInsets.Type.ime(), shown)
            .build()
    ReflectionHelpers.setField(root, "mLastWindowInsets", insets)
}

/**
 * An input capture provider that notes what the activity asks of it, in order, in place of the one
 * of the stream view: [onEnable] runs when the capture is asked for, before it is noted, so that a
 * test can look at the activity at that moment.
 */
internal class RecordingCapture : InputCaptureProvider() {
    val calls = mutableListOf<String>()
    var onEnable: () -> Unit = {}

    override fun enableCapture() {
        onEnable()
        calls += "enableCapture"
        super.enableCapture()
    }

    override fun disableCapture() {
        calls += "disableCapture"
        super.disableCapture()
    }

    override fun showCursor() {
        calls += "showCursor"
        super.showCursor()
    }

    override fun hideCursor() {
        calls += "hideCursor"
        super.hideCursor()
    }

    override fun onWindowFocusChanged(focusActive: Boolean) {
        calls += "focus $focusActive"
    }
}

/** Puts a [RecordingCapture] in place of the provider of the activity, and returns it. */
internal fun GameRig.recordCapture(): RecordingCapture = RecordingCapture().also { captureWith(it) }
