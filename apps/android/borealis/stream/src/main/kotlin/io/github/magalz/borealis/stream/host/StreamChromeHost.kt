package io.github.magalz.borealis.stream.host

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import io.github.magalz.borealis.designsystem.adaptive.FormFactorFacts
import io.github.magalz.borealis.designsystem.adaptive.formFactorOf
import io.github.magalz.borealis.designsystem.adaptive.isGamepadConnected
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.input.CaptureEnd
import io.github.magalz.borealis.stream.input.ChromeChord
import io.github.magalz.borealis.stream.input.MenuChordDetector
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
import io.github.magalz.borealis.stream.ui.glass.FrozenFrame
import io.github.magalz.borealis.stream.ui.glass.LocalFrozenFrame
import io.github.magalz.borealis.stream.ui.glass.LocalVideoSource
import io.github.magalz.borealis.stream.ui.glass.PixelCopySource
import io.github.magalz.borealis.stream.ui.glass.originOnScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The stream chrome over the stream view: a [ComposeView] that shows [StreamChromeRoot] for
 * [session], and the rules for who gets the input. What it takes depends on `session.chromeMode`:
 * - IDLE and OVERLAY: nothing. It does not take the focus, and it returns false for touch and
 *   motion, so they reach the stream below, except a gesture that starts on the menu handle.
 * - MODAL: all of it, the focus included. The stream keeps rendering below.
 * - DOCKED: the touches inside the chrome, not the keys.
 * - HIDDEN (picture-in-picture): nothing, and nothing is drawn.
 *
 * The chrome draws with the [look] that the viewer chose, read by the caller when the stream
 * starts: a change of a setting during a stream shows in the next one.
 *
 * The in-game menu and its Quit popup are glass over a live copy of [streamView], which is the
 * `SurfaceView` that the decoder renders to: the chrome gets the view as its video source, and it
 * copies it only while the menu is open (ADR 0061). The popup of the error stands on one more copy,
 * the last frame of the stream: the activity asks for it with [freezeFrame] when the stream ends in
 * error, before the decoder is released, and the host lets it go when the activity is destroyed.
 *
 * [onCaptured] runs when the chrome starts taking the input (the mode becomes MODAL): the caller
 * releases what is held and drops the pointer capture. [onReleased] runs, posted, when it stops:
 * the caller asks for the pointer capture again. Both run on the main thread.
 *
 * Back is routed here too, through an `OnBackPressedCallback`, so the activity must keep the
 * predictive back opt-out (the key event reaches `onBackPressed`).
 *
 * While MODAL the host also takes the gamepad keys itself, whatever pad they come from (see
 * [GamepadKey]), and a hold of Select and Start for one second closes the open panel.
 *
 * A key that was down when the chrome took the input (the OK held for the menu) does nothing in it
 * until it has been let go: its repeats and its release are consumed, and a new press works at
 * once. Otherwise the release would click the row the menu opened on.
 *
 * When the window gets its focus back from a system window (a permission prompt, the notification
 * shade, an IME), the confirm keys are consumed for [CONFIRM_GUARD_MS]: the press that answered
 * that window, or one meant to continue, must not land on whatever the chrome has focused (Cancel
 * on the Starting screen).
 */
@SuppressLint("ViewConstructor") // Created in code by the stream activity, never inflated.
@Suppress("TooManyFunctions") // One override per input path of the view.
class StreamChromeHost(
    private val activity: ComponentActivity,
    private val session: StreamSession,
    private val streamView: View,
    private val onCaptured: Runnable,
    private val onReleased: Runnable,
    look: BorealisLook = BorealisLook(),
) : FrameLayout(activity) {
    /** The time the guard reads, in the [SystemClock.uptimeMillis] timebase. */
    internal var clock: () -> Long = SystemClock::uptimeMillis
    private var mode: ChromeMode? = null
    private var windowFocusLost = false
    private var confirmKeysIgnoredUntil = 0L
    private var menuHandleBounds: Rect? = null
    private var ownsGesture = false
    private var collecting: Job? = null
    private var composeLaidOut = false
    private val modalSince = CaptureEnd()
    private val chord = ChromeChord(::closeOpenPanel)
    private val chordCheck = Runnable {
        chord.check(SystemClock.uptimeMillis())
        scheduleChordCheck()
    }

    private val pipListener =
        Consumer<PictureInPictureModeChangedInfo> { session.onPip(it.isInPictureInPictureMode) }

    private val videoSource = PixelCopySource(streamView)

    internal val frozenFrame = FrozenFrame(videoSource, { originOnScreen() })

    private val composeView =
        ComposeView(activity).apply {
            setContent {
                // The video that the menu glass copies is the stream view below this one, and the
                // frame that the error popup stands on is the copy of it that was taken at the end.
                CompositionLocalProvider(
                    LocalVideoSource provides videoSource,
                    LocalFrozenFrame provides frozenFrame,
                ) {
                    StreamChromeRoot(session, ::setMenuHandleBounds, look)
                }
            }
            // Compose cannot take the focus before its first layout (its root nodes are not
            // attached
            // yet), so the focus waits for it.
            setOnHierarchyChangeListener(
                object : ViewGroup.OnHierarchyChangeListener {
                    override fun onChildViewAdded(parent: View?, child: View?) {
                        child?.addOnLayoutChangeListener(FirstLayout())
                    }

                    override fun onChildViewRemoved(parent: View?, child: View?) = Unit
                }
            )
        }

    init {
        // Above the on-screen controller, which Game adds again when the screen rotates.
        translationZ = CHROME_Z
        descendantFocusability = FOCUS_BLOCK_DESCENDANTS
        addView(composeView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        activity.onBackPressedDispatcher.addCallback(
            activity,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = handleBack(session)
            },
        )
        // Nothing draws the frame once the activity is destroyed, which is also how Try again ends
        // it: the new stream is another activity, with a host of its own.
        activity.lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) = frozenFrame.release()
            }
        )
    }

    /** Takes the focus for the modal chrome once the Compose view has had its first layout. */
    private inner class FirstLayout : View.OnLayoutChangeListener {
        override fun onLayoutChange(
            v: View,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
            oldLeft: Int,
            oldTop: Int,
            oldRight: Int,
            oldBottom: Int,
        ) {
            v.removeOnLayoutChangeListener(this)
            composeLaidOut = true
            requestModalFocus()
        }
    }

    /** Copies the last frame of the stream view, for the popup of the error that is coming. */
    fun freezeFrame() = frozenFrame.request()

    /** The menu handle's bounds in the chrome's coordinates, or null when it is not shown. */
    internal fun setMenuHandleBounds(bounds: Rect?) {
        menuHandleBounds = bounds
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        activity.addOnPictureInPictureModeChangedListener(pipListener)
        collecting =
            activity.lifecycleScope.launch {
                session.state.map { session.chromeMode }.distinctUntilChanged().collect(::applyMode)
            }
    }

    override fun onDetachedFromWindow() {
        collecting?.cancel()
        collecting = null
        activity.removeOnPictureInPictureModeChangedListener(pipListener)
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            windowFocusLost = true
        } else if (windowFocusLost) {
            windowFocusLost = false
            confirmKeysIgnoredUntil = clock() + CONFIRM_GUARD_MS
        }
    }

    private fun applyMode(next: ChromeMode) {
        val previous = mode
        mode = next
        if (next != previous) {
            chord.reset()
            removeCallbacks(chordCheck)
        }
        if (next == ChromeMode.MODAL) {
            descendantFocusability = FOCUS_AFTER_DESCENDANTS
            requestModalFocus()
            if (previous != ChromeMode.MODAL) {
                modalSince.ended(SystemClock.uptimeMillis())
                onCaptured.run()
            }
        } else {
            descendantFocusability = FOCUS_BLOCK_DESCENDANTS
            if (previous == ChromeMode.MODAL || previous == ChromeMode.DOCKED) {
                clearFocus()
                streamView.requestFocus()
            }
            if (previous == ChromeMode.MODAL) post(onReleased)
        }
    }

    private fun requestModalFocus() {
        if (mode == ChromeMode.MODAL && composeLaidOut && !hasFocus()) requestFocus()
    }

    /**
     * While MODAL, the gamepad keys are the host's: A is OK, B is Back unless a screen takes it, Y
     * is the screen's, and Start, Select, X, L3, R3 and Mode are used up here. None of them is left
     * to the system, whose fallbacks would click the focused item for Start. Right after the window
     * gets its focus back, the confirm keys of any source are consumed first.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (session.chromeMode == ChromeMode.MODAL && isSpent(event)) return true
        val key = GamepadKey.of(event.keyCode)
        if (key == null || session.chromeMode != ChromeMode.MODAL) {
            return super.dispatchKeyEvent(event)
        }
        when (key) {
            GamepadKey.OK -> super.dispatchKeyEvent(GamepadKey.asOk(event))
            GamepadKey.BACK ->
                if (
                    !super.dispatchKeyEvent(event) &&
                        event.action == KeyEvent.ACTION_DOWN &&
                        event.repeatCount == 0
                ) {
                    activity.onBackPressedDispatcher.onBackPressed()
                }
            GamepadKey.SCREEN_ONLY -> super.dispatchKeyEvent(event)
            GamepadKey.UNUSED -> {
                chord.onKey(event)
                scheduleChordCheck()
            }
        }
        return true
    }

    /**
     * [dispatchKeyEvent] for a key the gamepad handler made up for a pad on the USB driver. Call it
     * on the main thread. Such a key reaches the chrome without passing the window's input
     * pipeline, which is what takes the window out of touch mode for the navigation and typing keys
     * of a pad Android knows. A phone's window starts in touch mode, where Compose gives the focus
     * to no clickable row, so the keys of the pad would move and click nothing. Every key that goes
     * down while the chrome is modal leaves touch mode if the window is in it, and then acts, as on
     * a TV: the first one is not consumed to show the focus, and the focus starts on the first row.
     */
    fun dispatchUsbPadKey(event: KeyEvent): Boolean {
        if (
            session.chromeMode == ChromeMode.MODAL &&
                event.action == KeyEvent.ACTION_DOWN &&
                isInTouchMode
        ) {
            composeView.requestFocusFromTouch()
        }
        return dispatchKeyEvent(event)
    }

    /** Whether the modal chrome consumes [event] without acting on it. */
    private fun isSpent(event: KeyEvent): Boolean =
        isHeldThrough(event) ||
            (GamepadKey.isConfirm(event.keyCode) && clock() < confirmKeysIgnoredUntil)

    /**
     * Whether [event] belongs to a press that began before the chrome took the input: repeats keep
     * the down time of the press, so it is older than the moment the mode became modal. An event
     * built without a down time and the system keys (volume, media) belong to no such press.
     */
    private fun isHeldThrough(event: KeyEvent): Boolean =
        event.downTime > 0 &&
            InputRoute.keyToChrome(ChromeMode.MODAL, event.keyCode) &&
            modalSince.began(event.downTime)

    private fun scheduleChordCheck() {
        removeCallbacks(chordCheck)
        val deadline = chord.deadline()
        if (deadline != MenuChordDetector.NO_DEADLINE) {
            postDelayed(chordCheck, maxOf(0L, deadline - SystemClock.uptimeMillis()))
        }
    }

    /** The chord returns to the stream from a panel; with no panel open it does nothing. */
    private fun closeOpenPanel() {
        val phase = session.state.value.phase
        if (phase is StreamPhase.Streaming && phase.panel != Panel.NONE) session.closePanel()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        when (mode) {
            ChromeMode.MODAL -> {
                // Whatever Compose does with it, the stream below gets no touch.
                super.dispatchTouchEvent(ev)
                true
            }
            ChromeMode.DOCKED -> super.dispatchTouchEvent(ev)
            ChromeMode.IDLE,
            ChromeMode.OVERLAY -> dispatchToMenuHandle(ev)
            ChromeMode.HIDDEN,
            null -> false
        }

    /** Takes a gesture that starts on the menu handle, all of it, and nothing else. */
    private fun dispatchToMenuHandle(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            ownsGesture = menuHandleBounds?.contains(Offset(ev.x, ev.y)) == true
        }
        if (!ownsGesture) return false
        val taken = super.dispatchTouchEvent(ev)
        if (
            ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            ownsGesture = false
        }
        return taken
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean =
        when (mode) {
            // Pointer sources never reach the stream below. Joystick and hat motion is left
            // unconsumed when Compose does not take it, so that Android turns it into a D-pad.
            ChromeMode.MODAL ->
                if (ev.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
                    super.dispatchGenericMotionEvent(ev)
                    true
                } else {
                    super.dispatchGenericMotionEvent(ev)
                }
            ChromeMode.DOCKED -> super.dispatchGenericMotionEvent(ev)
            else -> false
        }

    companion object {
        /** Above the on-screen controller and every other view of the stream layout. */
        private const val CHROME_Z = 1000f

        /** How long the confirm keys are ignored once the window has its focus back. */
        private const val CONFIRM_GUARD_MS = 1000L

        /**
         * Whether the keyboard that opens now docks under the stream instead of covering it: on a
         * phone (the form factor the Library uses for its layouts), and only while no game
         * controller is connected, whether Android has it or the app's USB driver holds it
         * ([usbPadHeld]; Android has no input device for such a pad). A pad cannot reach a docked
         * keyboard, because the chrome takes touches only and the keys of the pad go to the stream,
         * so with a pad the keyboard covers the stream and the pad navigates it. The caller asks
         * when the keyboard opens, so a pad that connects later is counted.
         */
        fun dockedKeyboard(context: Context, usbPadHeld: Boolean): Boolean {
            if (usbPadHeld || isGamepadConnected()) return false
            val configuration = context.resources.configuration
            return formFactorOf(
                FormFactorFacts(
                    isTvFeature =
                        context.packageManager.hasSystemFeature(
                            android.content.pm.PackageManager.FEATURE_LEANBACK
                        ),
                    uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK,
                    widthDp = configuration.screenWidthDp,
                    touchscreen = configuration.touchscreen,
                    // A connected pad returned above, so none counts here.
                    gamepadConnected = false,
                )
            ) == FormFactor.Phone
        }
    }
}

/**
 * What Back does in [session]: opens the menu while streaming, closes the open panel, answers
 * Cancel to the question about the running app, and leaves while starting or after a failure.
 */
internal fun handleBack(session: StreamSession) {
    when (val phase = session.state.value.phase) {
        is StreamPhase.Starting,
        is StreamPhase.Quitting,
        is StreamPhase.Failed -> session.leave()
        is StreamPhase.Conflict -> session.answerConflict(ConflictChoice.CANCEL)
        is StreamPhase.Disconnecting -> Unit
        is StreamPhase.Streaming ->
            if (phase.panel == Panel.NONE) session.openMenu() else session.closePanel()
    }
}
