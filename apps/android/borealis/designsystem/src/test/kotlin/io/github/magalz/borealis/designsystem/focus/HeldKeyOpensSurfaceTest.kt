package io.github.magalz.borealis.designsystem.focus

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.InternalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.components.ActionSheet
import io.github.magalz.borealis.designsystem.components.ActionSheetAction
import io.github.magalz.borealis.designsystem.components.BorealisDialog
import io.github.magalz.borealis.designsystem.components.DialogAction
import io.github.magalz.borealis.designsystem.components.Text
import io.github.magalz.borealis.designsystem.components.settings.ChoiceOption
import io.github.magalz.borealis.designsystem.components.settings.ChoiceSheet
import io.github.magalz.borealis.designsystem.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import java.time.Duration
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

private const val TV = "w960dp-h540dp-land-television-xhdpi"

/**
 * A key held down when a sheet or a dialog opens: the repeats and the release of that press reach
 * the new surface once it has the focus. They must not act in it (the release would click the
 * focused item); a press that begins after it opened works as usual.
 *
 * The events are built by hand, with the down time of the press and the flags the platform sets, so
 * that the ones after the opening reach the window of the surface, as they do on a device.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class HeldKeyOpensSurfaceTest {
    @get:Rule val composeRule = createComposeRule()
    private var open by mutableStateOf(false)
    private var tileClicks = 0
    private var actions = 0
    private var surfaceText = ""

    @Test
    fun `holding OK that opens the action sheet does not run its focused action on release`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }

        holdThroughOpening(Key.DirectionCenter, "Resume")

        assertSurfaceHeld("Resume")
    }

    @Test
    fun `a fresh OK press runs the focused action of the action sheet`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }
        holdThroughOpening(Key.DirectionCenter, "Resume")

        pressFresh(Key.DirectionCenter)

        assertThat(actions).isEqualTo(1)
    }

    @Test
    fun `holding OK that opens a choice sheet does not choose on release`() {
        composeRule.setContentInKeyboardMode { Tile { ChoiceSheetSpecimen() } }

        holdThroughOpening(Key.DirectionCenter, "Low")

        assertSurfaceHeld("Low")
    }

    @Test
    fun `a fresh OK press chooses in the choice sheet`() {
        composeRule.setContentInKeyboardMode { Tile { ChoiceSheetSpecimen() } }
        holdThroughOpening(Key.DirectionCenter, "Low")

        pressFresh(Key.DirectionCenter)

        assertThat(actions).isEqualTo(1)
    }

    @Test
    fun `holding OK that opens a dialog does not press its focused button on release`() {
        composeRule.setContentInKeyboardMode { Tile { DialogSpecimen() } }

        holdThroughOpening(Key.DirectionCenter, "Confirm")

        assertSurfaceHeld("Confirm")
    }

    @Test
    fun `a fresh OK press presses the focused button of the dialog`() {
        composeRule.setContentInKeyboardMode { Tile { DialogSpecimen() } }
        holdThroughOpening(Key.DirectionCenter, "Confirm")

        pressFresh(Key.DirectionCenter)

        assertThat(actions).isEqualTo(1)
    }

    // X opens the options on its first down, and its repeats and release then reach the sheet. X is
    // not a click key, so these two guard the opening path (the stale events do not disturb the
    // sheet, and a later press works); they do not reproduce the click bug of a held OK.
    @Test
    fun `X held through the opening of the action sheet leaves it open and runs nothing`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }

        holdThroughOpening(Key.ButtonX, "Resume")

        assertSurfaceHeld("Resume")
    }

    @Test
    fun `a fresh OK press after the X hold opened the sheet runs the focused action`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }
        holdThroughOpening(Key.ButtonX, "Resume")

        pressFresh(Key.DirectionCenter)

        assertThat(actions).isEqualTo(1)
    }

    @Test
    fun `a press that begins in the sheet works whatever clock its down time is on`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }
        open = true
        composeRule.waitForIdle()
        surfaceText = "Resume"

        // The Compose test injector stamps its events with its own clock, which is far behind.
        sendToSurface(Key.DirectionCenter, AndroidKeyAction.Down, repeat = 0, downTime = 16)
        sendToSurface(Key.DirectionCenter, AndroidKeyAction.Up, repeat = 0, downTime = 16)
        composeRule.waitForIdle()

        assertThat(actions).isEqualTo(1)
    }

    @Test
    fun `system keys held through the opening are not consumed`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }
        holdThroughOpening(Key.DirectionCenter, "Resume")

        val handled =
            listOf(
                    Key.VolumeUp,
                    Key(android.view.KeyEvent.KEYCODE_MUTE),
                    Key(android.view.KeyEvent.KEYCODE_CHANNEL_UP),
                    Key(android.view.KeyEvent.KEYCODE_HEADSETHOOK),
                )
                .map { sendToSurface(it, AndroidKeyAction.Down, repeat = 1, downTime = 1) }

        assertThat(handled).containsExactly(false, false, false, false)
    }

    @Test
    fun `D-pad navigation works in the sheet after the held key was let go`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }
        holdThroughOpening(Key.DirectionCenter, "Resume")

        pressFresh(Key.DirectionDown, expectActions = false)

        composeRule.onNodeWithText("Quit").assertIsFocused()
    }

    @Test
    fun `Back after the opening closes the sheet`() {
        composeRule.setContentInKeyboardMode { Tile { ActionSheetSpecimen() } }
        holdThroughOpening(Key.DirectionCenter, "Resume")

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(open).isFalse()
    }

    /**
     * Holds [key] on the tile until the surface opens, lets the clock run, and sends the repeats
     * and the release to the surface. The surface is open and has its first item focused when the
     * release lands.
     */
    private fun holdThroughOpening(key: Key, surfaceText: String) {
        this.surfaceText = surfaceText
        composeRule.onNodeWithTag(TILE).requestFocus()
        val downTime = SystemClock.uptimeMillis()
        send(tile(), HeldKey(key, AndroidKeyAction.Down, repeat = 0, downTime = downTime))
        // The long click of a held OK fires after the long-press timeout of the Compose clock.
        advanceClock(LONG_PRESS_MILLIS)
        composeRule.mainClock.advanceTimeBy(LONG_PRESS_MILLIS)
        composeRule.waitForIdle()
        assertThat(open).isTrue()
        sendToSurface(
            key,
            AndroidKeyAction.Down,
            repeat = 1,
            downTime = downTime,
            flags = android.view.KeyEvent.FLAG_LONG_PRESS,
        )
        advanceClock(REPEAT_MILLIS)
        sendToSurface(key, AndroidKeyAction.Down, repeat = 2, downTime = downTime)
        advanceClock(REPEAT_MILLIS)
        sendToSurface(key, AndroidKeyAction.Down, repeat = 3, downTime = downTime)
        advanceClock(REPEAT_MILLIS)
        sendToSurface(key, AndroidKeyAction.Up, repeat = 0, downTime = downTime)
        composeRule.waitForIdle()
    }

    private fun pressFresh(key: Key, expectActions: Boolean = true) {
        advanceClock(FRESH_MILLIS)
        val downTime = SystemClock.uptimeMillis()
        sendToSurface(key, AndroidKeyAction.Down, repeat = 0, downTime = downTime)
        advanceClock(SHORT_PRESS_MILLIS)
        sendToSurface(key, AndroidKeyAction.Up, repeat = 0, downTime = downTime)
        composeRule.waitForIdle()
        if (!expectActions) assertThat(actions).isEqualTo(0)
    }

    private fun assertSurfaceHeld(focusedText: String) {
        assertThat(open).isTrue()
        assertThat(actions).isEqualTo(0)
        assertThat(tileClicks).isEqualTo(0)
        composeRule.onNodeWithText(focusedText).assertIsDisplayed().assertIsFocused()
    }

    /** Sends to the window of the surface, the one that has the focus once it opened. */
    private fun sendToSurface(
        key: Key,
        action: AndroidKeyAction,
        repeat: Int,
        downTime: Long,
        flags: Int = 0,
    ): Boolean = send(surfaceNode(), HeldKey(key, action, repeat, downTime, flags))

    /** One key event of a press that is held: the key, its action, its repeat and its flags. */
    private data class HeldKey(
        val key: Key,
        val action: AndroidKeyAction,
        val repeat: Int,
        val downTime: Long,
        val flags: Int = 0,
    )

    @OptIn(InternalTestApi::class, ExperimentalTestApi::class)
    private fun send(node: SemanticsNodeInteraction, held: HeldKey): Boolean {
        val event =
            android.view.KeyEvent(
                held.downTime,
                SystemClock.uptimeMillis(),
                held.action.code,
                held.key.nativeKeyCode,
                held.repeat,
                0,
                0,
                0,
                held.flags,
            )
        val root = checkNotNull(node.fetchSemanticsNode().root)
        return root.sendKeyEvent(androidx.compose.ui.input.key.KeyEvent(event))
    }

    private fun advanceClock(millis: Long) {
        ShadowSystemClock.advanceBy(Duration.ofMillis(millis))
    }

    private fun tile() = composeRule.onNodeWithTag(TILE)

    /**
     * The node of the open surface that [surfaceText] names: it gives the window of the surface.
     */
    private fun surfaceNode() = composeRule.onNodeWithText(surfaceText)

    @Composable
    private fun Tile(surface: @Composable () -> Unit) {
        BorealisTheme(FormFactor.Tv) {
            Box(
                Modifier.size(80.dp)
                    .testTag(TILE)
                    .optionsClickable(onClick = { tileClicks++ }, onOptions = { open = true })
            )
            if (open) surface()
        }
    }

    @Composable
    private fun ActionSheetSpecimen() {
        ActionSheet(
            title = "Desktop",
            actions =
                listOf(
                    ActionSheetAction("resume", "Resume", "Back to the running desktop"),
                    ActionSheetAction("quit", "Quit", "Closes the app on the host"),
                ),
            onAction = { actions++ },
            onDismiss = { open = false },
        )
    }

    @Composable
    private fun ChoiceSheetSpecimen() {
        ChoiceSheet(
            title = "Quality",
            options = listOf(ChoiceOption("low", "Low"), ChoiceOption("high", "High")),
            selected = "low",
            onSelect = { actions++ },
            onDismiss = { open = false },
        )
    }

    @Composable
    private fun DialogSpecimen() {
        BorealisDialog(
            title = "Forget this host?",
            onDismissRequest = { open = false },
            confirm = DialogAction("Confirm") { actions++ },
            dismiss = DialogAction("Cancel") { open = false },
        ) {
            Text("Its pairing is removed.")
        }
    }

    private companion object {
        const val TILE = "tile"
        const val LONG_PRESS_MILLIS = 600L
        const val REPEAT_MILLIS = 50L
        const val FRESH_MILLIS = 200L
        const val SHORT_PRESS_MILLIS = 40L
    }
}

private enum class AndroidKeyAction(val code: Int) {
    Down(android.view.KeyEvent.ACTION_DOWN),
    Up(android.view.KeyEvent.ACTION_UP),
}
