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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.lifecycleScope
import io.github.magalz.borealis.designsystem.adaptive.formFactorOf
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.stream.ChromeMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.StreamSession
import io.github.magalz.borealis.stream.input.ChromeChord
import io.github.magalz.borealis.stream.input.MenuChordDetector
import io.github.magalz.borealis.stream.ui.StreamChromeRoot
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
 * [onCaptured] runs when the chrome starts taking the input (the mode becomes MODAL): the caller
 * releases what is held and drops the pointer capture. [onReleased] runs, posted, when it stops:
 * the caller asks for the pointer capture again. Both run on the main thread.
 *
 * Back is routed here too, through an `OnBackPressedCallback`, so the activity must keep the
 * predictive back opt-out (the key event reaches `onBackPressed`).
 *
 * While MODAL the host also takes the gamepad keys itself, whatever pad they come from (see
 * [GamepadKey]), and a hold of Select and Start for one second closes the open panel.
 */
@SuppressLint("ViewConstructor") // Created in code by the stream activity, never inflated.
class StreamChromeHost(
    private val activity: ComponentActivity,
    private val session: StreamSession,
    private val streamView: View,
    private val onCaptured: Runnable,
    private val onReleased: Runnable,
) : FrameLayout(activity) {
    /** The time the guard reads, in the [SystemClock.uptimeMillis] timebase. */
    internal var clock: () -> Long = SystemClock::uptimeMillis
    private var mode: ChromeMode? = null
    private var menuHandleBounds: Rect? = null
    private var ownsGesture = false
    private var collecting: Job? = null
    private var composeLaidOut = false
    private val chord = ChromeChord(::closeOpenPanel)
    private val chordCheck = Runnable {
        chord.check(SystemClock.uptimeMillis())
        scheduleChordCheck()
    }

    private val pipListener =
        Consumer<PictureInPictureModeChangedInfo> { session.onPip(it.isInPictureInPictureMode) }

    private val composeView =
        ComposeView(activity).apply {
            setContent { StreamChromeRoot(session, ::setMenuHandleBounds) }
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
            if (previous != ChromeMode.MODAL) onCaptured.run()
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
     * to the system, whose fallbacks would click the focused item for Start.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
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

        /**
         * Whether the device is a phone, where the keyboard docks under the stream instead of
         * covering it: the same rule the Library uses for its layouts.
         */
        @Suppress("ConfigurationScreenWidthHeight")
        fun isPhone(context: Context): Boolean {
            val configuration = context.resources.configuration
            return formFactorOf(
                isTvFeature =
                    context.packageManager.hasSystemFeature(
                        android.content.pm.PackageManager.FEATURE_LEANBACK
                    ),
                uiModeType = configuration.uiMode and Configuration.UI_MODE_TYPE_MASK,
                widthDp = configuration.screenWidthDp,
                touchscreen = configuration.touchscreen,
                navigation = configuration.navigation,
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
        is StreamPhase.Streaming ->
            if (phase.panel == Panel.NONE) session.openMenu() else session.closePanel()
    }
}
