package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.Intent
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.Game
import com.limelight.binding.input.touch.TouchContext
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.host.launch.SessionAudit
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.domain.WakeResult
import io.github.magalz.borealis.library.shell.LibraryActivity
import io.github.magalz.borealis.logging.AuditEvent
import io.github.magalz.borealis.logging.AuditSink
import io.github.magalz.borealis.logging.AuditType
import io.github.magalz.borealis.logging.BorealisAudit
import io.github.magalz.borealis.logging.Redactor
import io.github.magalz.borealis.stream.host.HeldKeys
import io.github.magalz.borealis.stream.input.GamepadChrome
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.keys.Shortcuts
import io.github.magalz.borealis.testing.unconfinedScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

private class RecordingTouch : TouchContext {
    var cancelled = 0

    override val actionIndex: Int = 0

    override fun setPointerCount(pointerCount: Int) = Unit

    override fun touchDownEvent(
        eventX: Int,
        eventY: Int,
        eventTime: Long,
        isNewFinger: Boolean,
    ): Boolean = false

    override fun touchMoveEvent(eventX: Int, eventY: Int, eventTime: Long): Boolean = false

    override fun touchUpEvent(eventX: Int, eventY: Int, eventTime: Long) = Unit

    override fun cancelTouch() {
        cancelled++
    }

    override val isCancelled: Boolean = false
}

private class RecordingGamepad : GamepadChrome {
    val calls = mutableListOf<String>()

    override fun setCaptured(captured: Boolean) {
        calls += "captured $captured"
    }

    override fun setMouseEmulation(enabled: Boolean) {
        calls += "mouse $enabled"
    }

    override fun setPointerSpeed(factor: Float) {
        calls += "pointerSpeed $factor"
    }
}

@RunWith(RobolectricTestRunner::class)
class GameStreamActionsTest {
    private lateinit var activity: Activity
    private val input = RecordingInput()
    private val held = HeldKeys()
    private val entryPoint = FakeEntryPoint()
    private val first = RecordingTouch()
    private val second = RecordingTouch()
    private val gamepad = RecordingGamepad()
    private val touches = arrayOf<TouchContext?>(first, second)
    private val builtFor = mutableListOf<TouchMode>()
    private val replacements = arrayOf<TouchContext>(RecordingTouch(), RecordingTouch())
    private val panelEvents = mutableListOf<String>()
    private lateinit var streamView: View
    private lateinit var actions: GameStreamActions
    private val audit = mutableListOf<AuditEvent>()

    private fun controls() =
        StreamControls(
            controller = gamepad,
            touchContexts = touches,
            streamView = streamView,
            closePanel = { panelEvents += "close focused=${streamView.hasFocus()}" },
            buildTouch = { mode ->
                builtFor += mode
                if (mode == TouchMode.OFF) null else replacements
            },
        )

    private fun actionsFor(target: Activity) =
        GameStreamActions(
            target,
            input,
            controls(),
            held,
            { entryPoint },
            unconfinedScope(),
        )

    @Before
    fun setUp() {
        val intent = Intent().putExtra(Game.EXTRA_PC_UUID, "host-1")
        activity = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        streamView = View(activity).apply { isFocusableInTouchMode = true }
        activity.setContentView(streamView)
        actions = actionsFor(activity)
        BorealisAudit.install(AuditSink { audit += it }, Redactor(), "test")
        SessionAudit.started("Den PC", "192.168.1.5", 0x0100, 1920, 1080, 60)
        audit.clear()
    }

    @After
    fun tearDown() {
        SessionAudit.stopped()
        BorealisAudit.uninstall()
    }

    @Test
    fun `Ctrl+Shift+Esc goes down in order and up in reverse`() {
        actions.send(
            Shortcuts.sendKeys
                .single { it.chord.vk == VK_ESCAPE && it.chord.modifiers.size == 2 }
                .chord
        )

        assertThat(input.events)
            .containsExactly(
                "down 80A2 mods 02 flags 0",
                "down 80A0 mods 03 flags 0",
                "down 801B mods 03 flags 0",
                "up 801B mods 03 flags 0",
                "up 80A0 mods 02 flags 0",
                "up 80A2 mods 00 flags 0",
            )
            .inOrder()
    }

    @Test
    fun `Win+Tab from the Send keys row and from the keyboard row presses Tab with the Windows key held`() {
        val expected =
            listOf(
                "down 805B mods 08 flags 0",
                "down 8009 mods 08 flags 0",
                "up 8009 mods 08 flags 0",
                "up 805B mods 00 flags 0",
            )

        // The chip right after Alt+Tab in each row.
        actions.send(Shortcuts.sendKeys[1].chord)
        assertThat(input.events).containsExactlyElementsIn(expected).inOrder()

        input.events.clear()
        actions.send(Shortcuts.keyboardRow[1].chord)
        assertThat(input.events).containsExactlyElementsIn(expected).inOrder()
    }

    @Test
    fun `Win+D presses D with the Windows key held`() {
        actions.send(KeyChord(VK_D, setOf(KeyModifier.WIN)))

        assertThat(input.events)
            .containsExactly(
                "down 805B mods 08 flags 0",
                "down 8044 mods 08 flags 0",
                "up 8044 mods 08 flags 0",
                "up 805B mods 00 flags 0",
            )
            .inOrder()
    }

    @Test
    fun `a chord leaves no key held`() {
        actions.send(KeyChord(VK_D, setOf(KeyModifier.WIN)))
        input.events.clear()

        actions.releaseHeldInput(0)

        assertThat(input.events).isEmpty()
    }

    @Test
    fun `text is typed on the host`() {
        actions.sendText("hello")

        assertThat(input.events).containsExactly("text hello")
    }

    @Test
    fun `taking the input releases keys in reverse, the mouse buttons and the touches`() {
        held.down(0x41)
        held.down(0xA0)

        actions.releaseHeldInput(MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY)

        assertThat(input.events)
            .containsExactly(
                "up 80A0 mods 00 flags 0",
                "up 8041 mods 00 flags 0",
                "mouse up 1",
                "mouse up 3",
            )
            .inOrder()
        assertThat(first.cancelled).isEqualTo(1)
        assertThat(second.cancelled).isEqualTo(1)
    }

    @Test
    fun `leaving finishes the activity`() {
        actions.leave()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `disconnect finishes the activity`() {
        actions.disconnect("Desktop")

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `quitting ends the app on the PC of this host, then finishes`() {
        actions.quitApp {}

        assertThat(entryPoint.apps.quits).containsExactly("host-1")
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a quit that worked leaves the Library the notice to show`() = runTest {
        entryPoint.notice.attach()

        actions.quitApp {}

        assertThat(withTimeoutOrNull(1) { entryPoint.notice.notices.first() }).isNotNull()
    }

    @Test
    fun `a failed quit leaves the Library no notice`() = runTest {
        entryPoint.notice.attach()
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp {}

        assertThat(withTimeoutOrNull(1) { entryPoint.notice.notices.first() }).isNull()
    }

    @Test
    fun `a failed quit shows the failure and keeps streaming`() {
        entryPoint.apps.result = QuitResult.Failed("The host said no")

        actions.quitApp {}

        assertThat(activity.isFinishing).isFalse()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("The host said no")
    }

    @Test
    fun `a failed quit tells the session`() {
        var failures = 0
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp { failures++ }

        assertThat(failures).isEqualTo(1)
    }

    @Test
    fun `a quit that worked does not tell the session it failed`() {
        var failures = 0

        actions.quitApp { failures++ }

        assertThat(failures).isEqualTo(0)
    }

    @Test
    fun `a failed quit without a text shows the generic one`() {
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp {}

        assertThat(activity.isFinishing).isFalse()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("Failed to quit.")
    }

    @Test
    fun `waking sends Wake-on-LAN to this host`() {
        actions.wakeHost()

        assertThat(entryPoint.hosts.wakes).containsExactly("host-1")
    }

    @Test
    fun `pairing again opens the library on the pairing of this host and finishes`() {
        actions.pairAgain()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component?.className).isEqualTo(LibraryActivity::class.java.name)
        assertThat(LibraryLaunchRequest.fromExtras(started.extras))
            .isEqualTo(LibraryLaunchRequest.Pair("host-1"))
        assertThat(started.flags and LIBRARY_FLAGS).isEqualTo(LIBRARY_FLAGS)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `diagnostics open the library on Privacy and diagnostics and finish`() {
        actions.openDiagnostics()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component?.className).isEqualTo(LibraryActivity::class.java.name)
        assertThat(LibraryLaunchRequest.fromExtras(started.extras))
            .isEqualTo(LibraryLaunchRequest.Privacy)
        assertThat(started.flags and LIBRARY_FLAGS).isEqualTo(LIBRARY_FLAGS)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `cancelling the question finishes, the other answers do not`() {
        actions.answerConflict(ConflictChoice.JOIN)
        actions.answerConflict(ConflictChoice.QUIT_AND_RELAUNCH)
        assertThat(activity.isFinishing).isFalse()

        actions.answerConflict(ConflictChoice.CANCEL)
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `the menu hint is shown until it was seen`() {
        assertThat(GameStreamActions.menuHintPending(activity)).isTrue()

        actions.menuHintShown()

        assertThat(GameStreamActions.menuHintPending(activity)).isFalse()
    }

    private fun retryOf(intent: Intent): Pair<Activity, Intent?> {
        val target = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        val retrying = actionsFor(target)
        retrying.retry()
        retrying.retry()
        val started = shadowOf(target).nextStartedActivity
        assertThat(shadowOf(target).nextStartedActivity).isNull()
        return target to started
    }

    @Test
    fun `retrying starts the library on the launch of this app while the stream is in front`() {
        val intent = Intent().putExtra(Game.EXTRA_PC_UUID, "host-1").putExtra(Game.EXTRA_APP_ID, 42)

        val (target, started) = retryOf(intent)

        assertThat(started?.component?.className).isEqualTo(LibraryActivity::class.java.name)
        assertThat(LibraryLaunchRequest.fromExtras(started?.extras))
            .isEqualTo(LibraryLaunchRequest.Launch("host-1", 42))
        assertThat(started?.flags?.and(LIBRARY_FLAGS)).isEqualTo(LIBRARY_FLAGS)
        assertThat(target.isFinishing).isTrue()
    }

    @Test
    fun `retrying without an app opens the apps of the host, without a host the hosts`() {
        val noApp = Intent().putExtra(Game.EXTRA_PC_UUID, "host-1")
        assertThat(LibraryLaunchRequest.fromExtras(retryOf(noApp).second?.extras))
            .isEqualTo(LibraryLaunchRequest.Apps("host-1"))

        val noHost = Intent().putExtra(Game.EXTRA_APP_ID, 42)
        assertThat(LibraryLaunchRequest.fromExtras(retryOf(noHost).second?.extras))
            .isEqualTo(LibraryLaunchRequest.Hosts)
    }

    @Test
    fun `waking tells what happened`() {
        val texts =
            mapOf(
                WakeResult.SENT to "Wake-up sent. The PC can take a minute to appear.",
                WakeResult.ALREADY_ONLINE to "The PC is already on.",
                WakeResult.NO_MAC_ADDRESS to
                    "Borealis does not know this PC's network address yet. Pair it once while it is on.",
                WakeResult.FAILED to "Could not send the wake-up.",
            )
        for ((result, text) in texts) {
            entryPoint.hosts.result = result

            actions.wakeHost()

            assertWithMessage(result.name).that(ShadowToast.getTextOfLatestToast()).isEqualTo(text)
        }
    }

    @Test
    fun `a second quit while the first is under way does nothing`() {
        val gate = CompletableDeferred<Unit>()
        entryPoint.apps.gate = gate

        actions.quitApp {}
        actions.quitApp {}
        gate.complete(Unit)

        assertThat(entryPoint.apps.quits).containsExactly("host-1")
    }

    @Test
    fun `a failed quit can be tried again`() {
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp {}
        actions.quitApp {}

        assertThat(entryPoint.apps.quits).containsExactly("host-1", "host-1")
    }

    private fun endReasons() = audit.filter { it.type == AuditType.SESSION_END }.map { it.detail }

    @Test
    fun `disconnect writes the session end with its own reason, then finishes`() {
        actions.disconnect("Desktop")

        assertThat(endReasons()).containsExactly("reason=disconnect duration_s=0")
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a quit writes the session end with its own reason`() {
        actions.quitApp {}

        assertThat(endReasons()).containsExactly("reason=quit duration_s=0")
    }

    @Test
    fun `a quit that failed writes no session end and keeps the stream on`() {
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp {}

        assertThat(endReasons()).isEmpty()
    }

    @Test
    fun `the host ending the stream while the quit is under way is still the quit`() {
        val gate = CompletableDeferred<Unit>()
        entryPoint.apps.gate = gate

        actions.quitApp {}
        SessionAudit.terminated(0)
        gate.complete(Unit)

        assertThat(endReasons()).containsExactly("reason=quit duration_s=0")
    }

    @Test
    fun `after a failed quit the next end by the host is a host end`() {
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp {}
        SessionAudit.terminated(0)

        assertThat(endReasons()).containsExactly("reason=host_ended duration_s=0")
    }

    @Test
    fun `asking for a keyframe asks the connection`() {
        actions.requestKeyframe()

        assertThat(input.events).containsExactly("keyframe")
    }

    @Test
    fun `the pointer speed scales the gamepad mouse of the handler`() {
        actions.setPointerSpeed(PointerSpeed.SLOW)
        actions.setPointerSpeed(PointerSpeed.NORMAL)
        actions.setPointerSpeed(PointerSpeed.FAST)

        assertThat(gamepad.calls)
            .containsExactly("pointerSpeed 0.5", "pointerSpeed 1.0", "pointerSpeed 2.0")
            .inOrder()
    }

    @Test
    fun `the gamepad as a mouse switches the gamepad handler`() {
        actions.setGamepadMouse(true)
        actions.setGamepadMouse(false)

        assertThat(gamepad.calls).containsExactly("mouse true", "mouse false").inOrder()
    }

    @Test
    fun `a new touch mode cancels the touches and replaces the contexts`() {
        actions.setTouchMode(TouchMode.DIRECT)

        assertThat(builtFor).containsExactly(TouchMode.DIRECT)
        assertThat(first.cancelled).isEqualTo(1)
        assertThat(second.cancelled).isEqualTo(1)
        assertThat(touches[0]).isSameInstanceAs(replacements[0])
        assertThat(touches[1]).isSameInstanceAs(replacements[1])
    }

    @Test
    fun `switching the touchscreen off cancels the touches and keeps the contexts`() {
        actions.setTouchMode(TouchMode.OFF)

        assertThat(builtFor).containsExactly(TouchMode.OFF)
        assertThat(first.cancelled).isEqualTo(1)
        assertThat(second.cancelled).isEqualTo(1)
        assertThat(touches[0]).isSameInstanceAs(first)
        assertThat(touches[1]).isSameInstanceAs(second)
    }

    @Test
    fun `the Android keyboard opens after the panel closes, on the focused stream view`() {
        actions.openSystemKeyboard()

        assertThat(panelEvents).containsExactly("close focused=false")
        assertThat(streamView.hasFocus()).isTrue()
        val ime = activity.getSystemService(InputMethodManager::class.java)
        // Posted, so it runs after the chrome has handed the focus back.
        assertThat(shadowOf(ime).isSoftInputVisible).isFalse()
        shadowOf(Looper.getMainLooper()).idle()
        assertThat(shadowOf(ime).isSoftInputVisible).isTrue()
    }

    @Test
    fun `the overlay needs nothing from the activity`() {
        actions.setOverlay(OverlayMode.DETAILED)

        assertThat(input.events).isEmpty()
        assertThat(gamepad.calls).isEmpty()
        assertThat(builtFor).isEmpty()
    }

    private companion object {
        // An open Library gets the request through onNewIntent instead of a second instance.
        const val LIBRARY_FLAGS = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        const val VK_ESCAPE = 0x1B
        const val VK_D = 0x44
    }
}
