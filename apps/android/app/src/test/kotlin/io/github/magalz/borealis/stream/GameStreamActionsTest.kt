package io.github.magalz.borealis.stream

import android.app.Activity
import android.content.Intent
import android.view.MotionEvent
import com.google.common.truth.Truth.assertThat
import com.limelight.Game
import com.limelight.binding.input.touch.TouchContext
import io.github.magalz.borealis.host.launch.ConflictChoice
import io.github.magalz.borealis.library.LibraryLaunchRequest
import io.github.magalz.borealis.library.domain.QuitResult
import io.github.magalz.borealis.library.shell.LibraryActivity
import io.github.magalz.borealis.settings.BorealisSettingsActivity
import io.github.magalz.borealis.stream.host.HeldKeys
import io.github.magalz.borealis.stream.keys.KeyChord
import io.github.magalz.borealis.stream.keys.KeyModifier
import io.github.magalz.borealis.stream.keys.Shortcuts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

private class RecordingTouch : TouchContext {
    var cancelled = 0

    override fun getActionIndex(): Int = 0

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

    override fun isCancelled(): Boolean = false
}

@RunWith(RobolectricTestRunner::class)
class GameStreamActionsTest {
    private lateinit var activity: Activity
    private val input = RecordingInput()
    private val held = HeldKeys()
    private val entryPoint = FakeEntryPoint()
    private val first = RecordingTouch()
    private val second = RecordingTouch()
    private lateinit var actions: GameStreamActions

    private fun actionsFor(target: Activity) =
        GameStreamActions(
            target,
            input,
            arrayOf(first, null, second),
            held,
            { entryPoint },
            CoroutineScope(Dispatchers.Unconfined),
        )

    @Before
    fun setUp() {
        val intent = Intent().putExtra(Game.EXTRA_PC_UUID, "host-1")
        activity = Robolectric.buildActivity(Activity::class.java, intent).setup().get()
        actions = actionsFor(activity)
    }

    @Test
    fun `Ctrl+Shift+Esc goes down in order and up in reverse`() {
        actions.send(Shortcuts.sendKeys.single { it.chord.vk == VK_ESCAPE && it.chord.modifiers.size == 2 }.chord)

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
        actions.disconnect()

        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `quitting ends the app on the PC of this host, then finishes`() {
        actions.quitApp()

        assertThat(entryPoint.apps.quits).containsExactly("host-1")
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `a failed quit shows the failure and keeps streaming`() {
        entryPoint.apps.result = QuitResult.Failed("The host said no")

        actions.quitApp()

        assertThat(activity.isFinishing).isFalse()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo("The host said no")
    }

    @Test
    fun `a failed quit without a text shows the generic one`() {
        entryPoint.apps.result = QuitResult.Failed(null)

        actions.quitApp()

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
        assertThat(activity.isFinishing).isTrue()
    }

    @Test
    fun `diagnostics open the Borealis settings and keep streaming`() {
        actions.openDiagnostics()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component?.className)
            .isEqualTo(BorealisSettingsActivity::class.java.name)
        assertThat(activity.isFinishing).isFalse()
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

    @Test
    fun `retrying finishes the activity and starts the same intent once it is destroyed`() {
        val controller = Robolectric.buildActivity(Activity::class.java, activity.intent).setup()
        val retrying = actionsFor(controller.get())

        retrying.retry()
        retrying.retry()
        assertThat(controller.get().isFinishing).isTrue()
        controller.pause().stop().destroy()

        val application = shadowOf(RuntimeEnvironment.getApplication())
        val relaunched = application.nextStartedActivity
        assertThat(relaunched.getStringExtra(Game.EXTRA_PC_UUID)).isEqualTo("host-1")
        assertThat(application.nextStartedActivity).isNull()
    }

    private companion object {
        const val VK_ESCAPE = 0x1B
        const val VK_D = 0x44
    }
}
