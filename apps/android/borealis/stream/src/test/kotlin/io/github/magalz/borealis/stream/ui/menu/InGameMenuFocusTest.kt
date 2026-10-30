package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.PointerSpeed
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The rows of the menu of a TV, with Send keys and Session info closed, from the first to the last.
private val TV_ROWS =
    listOf(
        "Back to stream",
        "Keyboard",
        "Send keys",
        "Gamepad mouse",
        "Overlay",
        "Refresh the picture",
        "Session info",
        "Display for next launch",
        "Disconnect",
        "Quit Desktop",
    )

// The same on a phone, where Touch takes the place of Gamepad mouse.
private val PHONE_ROWS = TV_ROWS.map { if (it == "Gamepad mouse") "Touch" else it }

// The chips of Send keys, in reading order.
private val CHIPS =
    listOf(
        "Alt+Tab",
        "Win+Tab",
        "Win",
        "Ctrl+Shift+Esc",
        "Alt+F4",
        "Win+D",
        "Win+G",
        "Esc",
        "Win+Alt+B",
    )

/** [rows] with the chips of Send keys after the row that opens them. */
private fun withChips(rows: List<String>): List<String> {
    val after = rows.indexOf("Send keys") + 1
    return rows.take(after) + CHIPS + rows.drop(after)
}

/**
 * The in-game menu (UC-004) has no lateral movement (owner decision of 2026-10-28, issue #98): Left
 * and Right do nothing and are not left to the host, Up and Down alone reach everything in one
 * vertical order, and an option row changes with OK, never with its segments.
 */
@RunWith(AndroidJUnit4::class)
class InGameMenuFocusTest {
    @get:Rule val composeRule = createComposeRule()

    private fun tvMenu(gamepadMouse: Boolean = false) =
        LayerFixture(
                composeRule,
                FormFactor.Tv,
                aStreaming(Panel.MENU, touchMode = null, gamepadMouse = gamepadMouse),
                aPerf(),
                keyboardMode = true,
            )
            .also { it.show() }

    private fun phoneMenu() =
        LayerFixture(
                composeRule,
                FormFactor.Phone,
                aStreaming(Panel.MENU),
                aPerf(),
                // A phone with a keyboard or a gamepad attached: the keys reach the rows.
                keyboardMode = true,
            )
            .also { it.show() }

    private fun text(text: String) = composeRule.onNodeWithText(text)

    private fun press(key: Key) = composeRule.onRoot().performKeyInput { pressKey(key) }

    /**
     * What the focused row, chip or segment says first, or null unless exactly one has the focus.
     */
    private fun focusedTitle() =
        composeRule
            .onAllNodes(isFocused())
            .fetchSemanticsNodes()
            .singleOrNull()
            ?.config
            ?.getOrNull(SemanticsProperties.Text)
            ?.firstOrNull()
            ?.text

    /** Presses [key] [times] times and returns where the focus was after each press. */
    private fun walk(key: Key, times: Int) = List(times) { press(key).let { focusedTitle() } }

    /** The titles of [titles] whose focus is gone, or moved, after [key] with the focus on them. */
    private fun movedBy(key: Key, titles: List<String>) = titles.filter { title ->
        text(title).performScrollTo().requestFocus()
        press(key)
        focusedTitle() != title
    }

    private fun assertLateralKeysDoNothing(fixture: LayerFixture, titles: List<String>) {
        assertThat(movedBy(Key.DirectionRight, titles)).isEmpty()
        assertThat(movedBy(Key.DirectionLeft, titles)).isEmpty()
        // Nothing is left over for the host, which would give it to the game or to the PC.
        assertThat(fixture.keysSeenOutside).containsNoneOf(Key.DirectionLeft, Key.DirectionRight)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Left and Right do nothing on every row of the menu`() {
        val fixture = tvMenu()

        assertLateralKeysDoNothing(fixture, TV_ROWS)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Left and Right do nothing on every row of the menu of a phone with keys`() {
        val fixture = phoneMenu()

        assertLateralKeysDoNothing(fixture, PHONE_ROWS)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Left and Right do nothing on the rows and the chips while Send keys and Session info are open`() {
        val fixture = tvMenu()
        text("Send keys").performClick()
        text("Session info").performScrollTo().performClick()
        text("Codec").performScrollTo().assertIsDisplayed()

        assertLateralKeysDoNothing(fixture, withChips(TV_ROWS))
    }

    @Test
    @Config(qualifiers = TV)
    fun `Down and Up walk the rows of the menu in their order and stop at the ends`() {
        tvMenu()
        assertThat(focusedTitle()).isEqualTo("Back to stream")

        assertThat(walk(Key.DirectionDown, TV_ROWS.size - 1)).isEqualTo(TV_ROWS.drop(1))
        assertThat(walk(Key.DirectionDown, 1)).containsExactly("Quit Desktop")
        assertThat(walk(Key.DirectionUp, TV_ROWS.size - 1))
            .isEqualTo(TV_ROWS.dropLast(1).reversed())
        assertThat(walk(Key.DirectionUp, 1)).containsExactly("Back to stream")
    }

    @Test
    @Config(qualifiers = TV)
    fun `Down walks the chips of Send keys in reading order and Up walks back`() {
        tvMenu()
        text("Send keys").performClick()
        text("Back to stream").requestFocus()
        val order = withChips(TV_ROWS)

        assertThat(walk(Key.DirectionDown, order.size - 1)).isEqualTo(order.drop(1))
        assertThat(walk(Key.DirectionUp, order.size - 1)).isEqualTo(order.dropLast(1).reversed())
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Down walks the chips of Send keys in reading order on a narrow phone too`() {
        phoneMenu()
        text("Send keys").performClick()
        text("Back to stream").requestFocus()
        val order = withChips(PHONE_ROWS)

        assertThat(walk(Key.DirectionDown, order.size - 1)).isEqualTo(order.drop(1))
        assertThat(walk(Key.DirectionUp, order.size - 1)).isEqualTo(order.dropLast(1).reversed())
    }

    @Test
    @Config(qualifiers = TV)
    fun `Session info open has nothing to focus, and Down goes on to the next row`() {
        tvMenu()
        text("Session info").performScrollTo().performClick()
        text("Codec").performScrollTo().assertIsDisplayed()
        text("Session info").requestFocus()

        assertThat(walk(Key.DirectionDown, 1)).containsExactly("Display for next launch")
        assertThat(walk(Key.DirectionUp, 2)).containsExactly("Session info", "Refresh the picture")
    }

    @Test
    @Config(qualifiers = TV)
    fun `OK on the Pointer speed row cycles Normal, Fast, Slow and back to Normal`() {
        val fixture = tvMenu(gamepadMouse = true)
        text("Pointer speed").performScrollTo().requestFocus()

        press(Key.DirectionCenter)
        fixture.phase = fixture.phase.copy(pointerSpeed = PointerSpeed.FAST)
        composeRule.waitForIdle()
        press(Key.DirectionCenter)
        fixture.phase = fixture.phase.copy(pointerSpeed = PointerSpeed.SLOW)
        composeRule.waitForIdle()
        press(Key.DirectionCenter)

        assertThat(fixture.intents.calls)
            .containsExactly(
                "setPointerSpeed FAST",
                "setPointerSpeed SLOW",
                "setPointerSpeed NORMAL",
            )
            .inOrder()
        text("Pointer speed").assertIsFocused()
    }

    // The round finding D4: Down several times, OK, Right, OK, and Quit was never reached.
    @Test
    @Config(qualifiers = TV)
    fun `Down, OK, Right, OK on Send keys and then Down reaches Quit and OK asks to quit`() {
        val fixture = tvMenu()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        text("Send keys").assertIsFocused()
        press(Key.DirectionCenter)
        text("Alt+Tab").assertIsDisplayed()
        press(Key.DirectionRight)
        text("Send keys").assertIsFocused()
        press(Key.DirectionCenter)
        text("Alt+Tab").assertDoesNotExist()
        assertThat(walk(Key.DirectionDown, 7).last()).isEqualTo("Quit Desktop")
        press(Key.DirectionCenter)

        text("Quit Desktop?").assertIsDisplayed()
        text("Cancel").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Down and OK alone reach Quit through the open chips of Send keys`() {
        val fixture = tvMenu()

        press(Key.DirectionDown)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        val order = withChips(TV_ROWS)
        val fromSendKeys = order.drop(order.indexOf("Send keys") + 1)
        assertThat(walk(Key.DirectionDown, fromSendKeys.size)).isEqualTo(fromSendKeys)
        text("Quit Desktop").assertIsFocused()
        press(Key.DirectionCenter)

        text("Quit Desktop?").assertIsDisplayed()
        text("Cancel").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Down, OK, Right, OK on Session info and then Down reaches Quit and OK asks to quit`() {
        val fixture = tvMenu()

        // Keyboard, Send keys, Gamepad mouse, Overlay, Refresh the picture, Session info.
        assertThat(walk(Key.DirectionDown, 6).last()).isEqualTo("Session info")
        press(Key.DirectionCenter)
        text("Codec").assertIsDisplayed()
        press(Key.DirectionRight)
        text("Session info").assertIsFocused()
        press(Key.DirectionCenter)
        text("Codec").assertDoesNotExist()
        // Display for next launch, Disconnect, Quit.
        assertThat(walk(Key.DirectionDown, 3).last()).isEqualTo("Quit Desktop")
        press(Key.DirectionCenter)

        text("Quit Desktop?").assertIsDisplayed()
        text("Cancel").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()
    }
}
