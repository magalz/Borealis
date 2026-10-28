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
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// The rows of the menu that have nothing of their own to the right of the title.
private val ROWS_WITHOUT_CONTROL =
    listOf(
        "Back to stream",
        "Keyboard",
        "Send keys",
        "Refresh the picture",
        "Session info",
        "Display for next launch",
        "Disconnect",
        "Quit Desktop",
    )

/**
 * Left and Right in the in-game menu (UC-004) never take the focus off the menu: Sprint 12 round
 * finding D4, where Right on Send keys or Session info left no row focused and OK did nothing until
 * the menu reopened.
 */
@RunWith(AndroidJUnit4::class)
class InGameMenuFocusTest {
    @get:Rule val composeRule = createComposeRule()

    private fun tvMenu() =
        LayerFixture(
                composeRule,
                FormFactor.Tv,
                aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false),
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

    /** Presses [key] [times] times, as a user taps a direction. */
    private fun press(key: Key, times: Int) = repeat(times) { press(key) }

    private fun holdsFocus(title: String) =
        text(title).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Focused) == true

    private fun focusedNodes() = composeRule.onAllNodes(isFocused()).fetchSemanticsNodes().size

    /** The rows of [titles] that do not hold the focus after [key], pressed with it on them. */
    private fun rowsThatLoseFocusOn(key: Key, titles: List<String>) = titles.filter { title ->
        text(title).performScrollTo().requestFocus()
        press(key)
        !holdsFocus(title)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Right keeps the focus on every row that has no control`() {
        tvMenu()

        assertThat(rowsThatLoseFocusOn(Key.DirectionRight, ROWS_WITHOUT_CONTROL)).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Left keeps the focus on every row that has no control`() {
        tvMenu()

        assertThat(rowsThatLoseFocusOn(Key.DirectionLeft, ROWS_WITHOUT_CONTROL)).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Right and Left keep the focus on Send keys and Session info while they are open`() {
        tvMenu()
        text("Send keys").performClick()
        text("Session info").performScrollTo().performClick()
        text("Alt+Tab").performScrollTo().assertIsDisplayed()
        text("Codec").performScrollTo().assertIsDisplayed()
        val open = listOf("Send keys", "Session info")

        assertThat(rowsThatLoseFocusOn(Key.DirectionRight, open)).isEmpty()
        assertThat(rowsThatLoseFocusOn(Key.DirectionLeft, open)).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Right on an option row enters its segments and Left comes back to the row`() {
        tvMenu()

        // Right lands on the selected segment, which is the second of Gamepad mouse (On, Off) and
        // the first of Overlay (Off, Compact, Detailed): Left walks back to the first and then out.
        for ((title, lefts) in listOf("Gamepad mouse" to 2, "Overlay" to 1)) {
            text(title).performScrollTo().requestFocus()

            press(Key.DirectionRight)
            assertThat(focusedNodes()).isEqualTo(1)
            assertThat(holdsFocus(title)).isFalse()

            press(Key.DirectionLeft, lefts)
            text(title).assertIsFocused()
        }
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a phone with keys keeps the focus on its rows too`() {
        phoneMenu()

        assertThat(rowsThatLoseFocusOn(Key.DirectionRight, ROWS_WITHOUT_CONTROL)).isEmpty()
        assertThat(rowsThatLoseFocusOn(Key.DirectionLeft, ROWS_WITHOUT_CONTROL)).isEmpty()
        text("Touch").performScrollTo().requestFocus()
        press(Key.DirectionRight)
        assertThat(holdsFocus("Touch")).isFalse()
        assertThat(focusedNodes()).isEqualTo(1)
        // Right lands on the selected segment, Trackpad, the first of Touch: one Left is enough.
        press(Key.DirectionLeft)
        text("Touch").assertIsFocused()
    }

    // The round finding D4: Down several times, OK, Right, OK, and Quit was never reached.
    @Test
    @Config(qualifiers = TV)
    fun `Down, OK, Right, OK on Send keys and then Down reaches Quit and OK asks to quit`() {
        val fixture = tvMenu()

        press(Key.DirectionDown, 2)
        text("Send keys").assertIsFocused()
        press(Key.DirectionCenter)
        text("Alt+Tab").assertIsDisplayed()
        press(Key.DirectionRight)
        text("Send keys").assertIsFocused()
        press(Key.DirectionCenter)
        text("Alt+Tab").assertDoesNotExist()
        // Gamepad mouse, Overlay, Refresh the picture, Session info, Display, Disconnect, Quit.
        press(Key.DirectionDown, 7)
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
        press(Key.DirectionDown, 6)
        text("Session info").assertIsFocused()
        press(Key.DirectionCenter)
        text("Codec").assertIsDisplayed()
        press(Key.DirectionRight)
        text("Session info").assertIsFocused()
        press(Key.DirectionCenter)
        text("Codec").assertDoesNotExist()
        // Display for next launch, Disconnect, Quit.
        press(Key.DirectionDown, 3)
        text("Quit Desktop").assertIsFocused()
        press(Key.DirectionCenter)

        text("Quit Desktop?").assertIsDisplayed()
        text("Cancel").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()
    }
}
