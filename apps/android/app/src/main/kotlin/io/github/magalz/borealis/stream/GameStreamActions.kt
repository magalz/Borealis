package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.core.content.edit
import com.limelight.Game
import com.limelight.nvstream.NvConnection
import com.limelight.nvstream.StreamConfiguration
import com.limelight.nvstream.input.KeyboardPacket
import com.limelight.nvstream.input.MouseButtonPacket
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.host.launch.SessionAudit
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.R as LibraryR
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.library.shell.LeaveNotice
import io.github.magalz.borealis.library.shell.LibraryActivity
import io.github.magalz.borealis.stream.host.HeldKeys
import io.github.magalz.borealis.stream.keys.KeyChord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** The input the stream sends to the PC. [NvConnection] backs it in the app. */
interface InputSink {
    fun sendKeyboardInput(keyMap: Short, direction: Byte, modifiers: Byte, flags: Byte)

    fun sendUtf8Text(text: String)

    fun sendMouseButtonUp(button: Byte)

    fun requestKeyframe()
}

private class NvConnectionInput(private val connection: NvConnection) : InputSink {
    override fun sendKeyboardInput(keyMap: Short, direction: Byte, modifiers: Byte, flags: Byte) =
        connection.sendKeyboardInput(keyMap, direction, modifiers, flags)

    override fun sendUtf8Text(text: String) = connection.sendUtf8Text(text)

    override fun sendMouseButtonUp(button: Byte) = connection.sendMouseButtonUp(button)

    override fun requestKeyframe() = connection.requestKeyframe()
}

/**
 * What the stream chrome asks of the stream activity ([StreamActions]). Every call arrives on the
 * main thread. The overlay needs nothing from here: the session state shows it, and `PerfGate`
 * turns the decoder numbers on while it is visible.
 */
@Suppress("TooManyFunctions") // One call per thing the chrome can ask of the stream.
class GameStreamActions
internal constructor(
    private val activity: Activity,
    private val input: InputSink,
    private val controls: StreamControls,
    private val heldKeys: HeldKeys,
    private val entryPoint: () -> StreamEntryPoint,
    private val scope: CoroutineScope,
) : StreamActions {
    constructor(
        activity: Activity,
        connection: NvConnection,
        controls: StreamControls,
        heldKeys: HeldKeys,
    ) : this(
        activity,
        NvConnectionInput(connection),
        controls,
        heldKeys,
        { StreamEntryPoint.from(activity) },
        appScope,
    )

    private val hostId: String? = activity.intent.getStringExtra(Game.EXTRA_PC_UUID)
    private var retried = false
    private var quitting = false

    override fun send(chord: KeyChord) {
        val modifiers = chord.modifiers.toList()
        var held = 0
        for (modifier in modifiers) {
            held = held or modifier.flag
            sendKey(modifier.vk, KeyboardPacket.KEY_DOWN, held)
        }
        sendKey(chord.vk, KeyboardPacket.KEY_DOWN, held)
        sendKey(chord.vk, KeyboardPacket.KEY_UP, held)
        for (modifier in modifiers.asReversed()) {
            held = held and modifier.flag.inv()
            sendKey(modifier.vk, KeyboardPacket.KEY_UP, held)
        }
    }

    private fun sendKey(vk: Int, direction: Byte, modifiers: Int) {
        if (direction == KeyboardPacket.KEY_DOWN) heldKeys.down(vk) else heldKeys.up(vk)
        input.sendKeyboardInput((KEY_PREFIX or vk).toShort(), direction, modifiers.toByte(), 0)
    }

    /**
     * The chrome takes the input: releases every key held down, the mouse buttons in [mouseButtons]
     * (`MotionEvent.BUTTON_*`, as the activity tracks them) and the touches in progress, so the PC
     * is not left with a stuck key or button. Gamepad zeroing is the gamepad handler's own
     * (`GamepadChrome.setCaptured`, called by the activity).
     */
    fun releaseHeldInput(mouseButtons: Int) {
        heldKeys.releaseAll { vk ->
            input.sendKeyboardInput((KEY_PREFIX or vk).toShort(), KeyboardPacket.KEY_UP, 0, 0)
        }
        for ((androidButtons, button) in MOUSE_BUTTONS) {
            if (mouseButtons and androidButtons != 0) input.sendMouseButtonUp(button)
        }
        controls.touchContexts.forEach { it?.cancelTouch() }
    }

    override fun sendText(text: String) = input.sendUtf8Text(text)

    /**
     * Closes the panel, which hands the input back, then focuses the stream view and shows the
     * Android keyboard on it. The keyboard is shown from a posted task, after the chrome's own
     * focus hand-off has run.
     */
    override fun openSystemKeyboard() {
        controls.closePanel.run()
        val view = controls.streamView
        view.requestFocus()
        view.post {
            activity.getSystemService(InputMethodManager::class.java)?.showSoftInput(view, 0)
        }
    }

    override fun requestKeyframe() = input.requestKeyframe()

    /**
     * Cancels the touches in progress, so no context is left with a finger down, then gives the
     * activity the contexts of [mode]. [TouchMode.OFF] has none: the activity ignores the touch
     * screen and keeps the contexts it has.
     */
    override fun setTouchMode(mode: TouchMode) {
        val contexts = controls.touchContexts
        contexts.forEach { it?.cancelTouch() }
        val built = controls.buildTouch(mode) ?: return
        for (i in contexts.indices) contexts[i] = built.getOrNull(i)
    }

    override fun setGamepadMouse(enabled: Boolean) = controls.controller.setMouseEmulation(enabled)

    override fun setPointerSpeed(speed: PointerSpeed) =
        controls.controller.setPointerSpeed(speed.factor)

    override fun setOverlay(mode: OverlayMode) = Unit

    override fun menuHintShown() {
        preferences(activity).edit { putBoolean(KEY_MENU_HINT_SHOWN, true) }
    }

    override fun disconnect() {
        SessionAudit.disconnected()
        activity.finish()
    }

    override fun quitApp(onFailed: () -> Unit) {
        if (quitting) return
        quitting = true
        // The host ends the stream with the app, possibly before the answer arrives.
        SessionAudit.quitRequested()
        val id = hostId
        scope.launch {
            val result =
                if (id == null) QuitResult.Failed(null) else entryPoint().appRepository().quit(id)
            when (result) {
                QuitResult.Quit -> {
                    SessionAudit.quit()
                    // The Library behind the stream shows the notice that the Apps sheet shows.
                    entryPoint().quitNotice().post(LeaveNotice.Quit)
                    activity.finish()
                }
                is QuitResult.Failed -> {
                    // The stream goes on, so the user can try again.
                    quitting = false
                    SessionAudit.quitFailed()
                    onFailed()
                    toast(
                        result.message
                            ?: activity.getString(LibraryR.string.apps_message_quit_failed)
                    )
                }
            }
        }
    }

    override fun leave() = activity.finish()

    /**
     * Opens the Library on the launch of this app and finishes while the stream is still in front,
     * so Android does not take it for a start from the background. The Library starts the app
     * again, through its own apps and conflict flow.
     */
    override fun retry() {
        if (retried) return
        retried = true
        val id = hostId
        val appId =
            activity.intent.getIntExtra(Game.EXTRA_APP_ID, StreamConfiguration.INVALID_APP_ID)
        openLibrary(
            when {
                id == null -> LibraryLaunchRequest.Hosts
                appId == StreamConfiguration.INVALID_APP_ID -> LibraryLaunchRequest.Apps(id)
                else -> LibraryLaunchRequest.Launch(id, appId)
            }
        )
    }

    override fun wakeHost() {
        val id = hostId
        scope.launch {
            val result = id?.let { entryPoint().hostRepository().wake(it) } ?: WakeResult.FAILED
            toast(
                activity.getString(
                    when (result) {
                        WakeResult.SENT -> LibraryR.string.hosts_message_wake_sent
                        WakeResult.ALREADY_ONLINE -> LibraryR.string.hosts_message_already_online
                        WakeResult.NO_MAC_ADDRESS -> LibraryR.string.hosts_message_no_mac
                        WakeResult.FAILED -> LibraryR.string.hosts_message_wake_failed
                    }
                )
            )
        }
    }

    override fun pairAgain() {
        val id = hostId ?: return
        openLibrary(LibraryLaunchRequest.Pair(id))
    }

    // An open Library takes the request through onNewIntent instead of a second instance.
    private fun openLibrary(request: LibraryLaunchRequest) {
        activity.startActivity(
            Intent(activity, LibraryActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtras(request.toExtras())
        )
        activity.finish()
    }

    private fun toast(text: String) =
        Toast.makeText(activity.applicationContext, text, Toast.LENGTH_LONG).show()

    /**
     * Opens the Library on Privacy and diagnostics and finishes while the stream is still in front,
     * as [retry] does.
     */
    override fun openDiagnostics() = openLibrary(LibraryLaunchRequest.Privacy)

    /**
     * The choice itself is audited and handed to the connection by the session. Cancelling leaves
     * the session at the question, so the activity ends here.
     */
    override fun answerConflict(choice: ConflictChoice) {
        if (choice == ConflictChoice.CANCEL) activity.finish()
    }

    companion object {
        private const val KEY_PREFIX = 0x8000
        private const val PREFERENCES = "BorealisStream"
        private const val KEY_MENU_HINT_SHOWN = "menu_hint_shown"

        // Outlives the activity: a quit that is under way must finish after the stream screen is
        // gone.
        private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        private val MOUSE_BUTTONS =
            listOf(
                MotionEvent.BUTTON_PRIMARY to MouseButtonPacket.BUTTON_LEFT,
                (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_STYLUS_PRIMARY) to
                    MouseButtonPacket.BUTTON_RIGHT,
                (MotionEvent.BUTTON_TERTIARY or MotionEvent.BUTTON_STYLUS_SECONDARY) to
                    MouseButtonPacket.BUTTON_MIDDLE,
                MotionEvent.BUTTON_BACK to MouseButtonPacket.BUTTON_X1,
                MotionEvent.BUTTON_FORWARD to MouseButtonPacket.BUTTON_X2,
            )

        private fun preferences(context: Context) =
            context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

        /** True until the user has seen the menu hint once. */
        @JvmStatic
        fun menuHintPending(context: Context): Boolean =
            !preferences(context).getBoolean(KEY_MENU_HINT_SHOWN, false)
    }
}
