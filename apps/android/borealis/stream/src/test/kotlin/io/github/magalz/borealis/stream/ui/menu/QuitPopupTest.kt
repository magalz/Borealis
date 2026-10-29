package io.github.magalz.borealis.stream.ui.menu

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.paneTitles
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.assertWidthNear
import io.github.magalz.borealis.stream.testing.setChrome
import io.github.magalz.borealis.stream.ui.LayerFixture
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val DESKTOP_QUIT = "Ends the stream.\nThe display and controls go back to the PC."

/**
 * The Quit popup of the in-game menu (issue #100): a panel in the chrome layer, centred over the
 * menu, not a dialog window and not a part of the menu's side panel. What it says and what its
 * buttons do is in `InGameMenuTest`; this is where it stands and how the focus and the touch stay
 * in it.
 */
@RunWith(AndroidJUnit4::class)
class QuitPopupTest {
    @get:Rule val composeRule = createComposeRule()

    private fun text(text: String) = composeRule.onNodeWithText(text)

    /** The popup, which a screen reader announces by the question that is its title. */
    private fun popup() =
        composeRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Quit Desktop?")
        )

    /** The menu of [formFactor] with Quit chosen, the way that device chooses it. */
    private fun openPopup(formFactor: FormFactor): LayerFixture {
        val phone = formFactor == FormFactor.Phone
        val fixture =
            LayerFixture(
                composeRule,
                formFactor,
                if (phone) aStreaming(Panel.MENU)
                else aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false),
                aPerf(),
                keyboardMode = !phone,
            )
        fixture.show()
        if (phone) {
            text("Quit Desktop").performScrollTo().performClick()
        } else {
            text("Quit Desktop").performScrollTo().requestFocus()
            text("Quit Desktop").performKeyInput { pressKey(Key.DirectionCenter) }
        }
        return fixture
    }

    @Test
    @Config(qualifiers = TV)
    fun `the popup is a panel centred in the layer and not a dialog window`() {
        openPopup(FormFactor.Tv)

        composeRule.onAllNodes(isDialog()).assertCountEquals(0)
        val panel = popup().getUnclippedBoundsInRoot()
        val layer = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertThat(panel.left.value + panel.right.value).isWithin(1f).of(layer.width.value)
        assertThat(panel.top.value + panel.bottom.value).isWithin(1f).of(layer.height.value)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the popup is 420dp wide, wider than the side panel behind it`() {
        openPopup(FormFactor.Tv)

        popup().assertWidthNear(420.dp)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld the popup is 420dp wide`() {
        openPopup(FormFactor.Handheld)

        popup().assertWidthNear(420.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the popup is the window less 16dp on each side`() {
        openPopup(FormFactor.Phone)

        popup().assertWidthNear(328.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the popup is announced with the question, which is a heading, over a plain body`() {
        openPopup(FormFactor.Phone)

        assertThat(composeRule.paneTitles()).contains("Quit Desktop?")
        // The unmerged tree of the popup: the title and the body are read together, but only the
        // title is a heading. The menu behind it is in this tree too, with its own Quit line.
        val inPopup =
            hasAnyAncestor(
                SemanticsMatcher.expectValue(SemanticsProperties.PaneTitle, "Quit Desktop?")
            )
        composeRule
            .onNode(
                hasText("Quit Desktop?") and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading) and
                    inPopup,
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
        composeRule
            .onNode(
                hasText(DESKTOP_QUIT) and
                    SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading) and
                    inPopup,
                useUnmergedTree = true,
            )
            .assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the popup announces its body with its title when it opens`() {
        openPopup(FormFactor.Phone)

        composeRule
            .onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
            )
            .assert(hasText("Quit Desktop?") and hasText(DESKTOP_QUIT))
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Cancel and Quit sit side by side, also on a phone`() {
        openPopup(FormFactor.Phone)

        val cancel = text("Cancel").getUnclippedBoundsInRoot()
        val quit = text("Quit").getUnclippedBoundsInRoot()
        assertThat(cancel.top).isEqualTo(quit.top)
        assertThat(cancel.right).isAtMost(quit.left)
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `a tap on the scrim closes the popup and does not reach the menu behind it`() {
        val fixture = openPopup(FormFactor.Phone)

        composeRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

        popup().assertDoesNotExist()
        text("Quit Desktop").assertIsDisplayed()
        assertThat(fixture.intents.calls).isEmpty()
    }

    // The side panel is under the right of the scrim: its own scrim must not take this tap.
    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `a tap on the scrim over the side panel closes only the popup`() {
        val fixture = openPopup(FormFactor.Phone)

        composeRule.onRoot().performTouchInput { click(Offset(width - 10f, 10f)) }

        popup().assertDoesNotExist()
        text("Quit Desktop").assertIsDisplayed()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a tap inside the popup does not close it`() {
        val fixture = openPopup(FormFactor.Phone)

        text(DESKTOP_QUIT).performClick()

        popup().assertIsDisplayed()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a tap on the scrim puts the focus back on the Quit row, as Cancel does`() {
        val fixture = openPopup(FormFactor.Tv)

        composeRule.onRoot().performTouchInput { click(Offset(10f, 10f)) }

        popup().assertDoesNotExist()
        text("Quit Desktop").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Left and Right move between Cancel and Quit`() {
        openPopup(FormFactor.Tv)
        text("Cancel").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        text("Quit").assertIsFocused()

        composeRule.onRoot().performKeyInput { pressKey(Key.DirectionLeft) }
        text("Cancel").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `Up and Down keep the focus in the popup, the menu behind has none to give`() {
        val fixture = openPopup(FormFactor.Tv)

        composeRule.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
            pressKey(Key.DirectionUp)
        }

        text("Cancel").assertIsFocused()
        assertThat(fixture.intents.calls).isEmpty()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the menu behind the popup cannot be reached by the screen reader or the D-pad`() {
        openPopup(FormFactor.Tv)

        text("Back to stream").assertDoesNotExist()
        text("Disconnect").assertDoesNotExist()
        text("Desktop · Gaming PC").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a key that takes the window out of touch mode puts the focus on Cancel, not behind`() {
        lateinit var inputMode: InputModeManager
        composeRule.setChrome(FormFactor.Phone) {
            inputMode = LocalInputModeManager.current
            Box(Modifier.fillMaxSize()) {
                InGameMenu(
                    "Desktop",
                    "Gaming PC",
                    aStreaming(Panel.MENU),
                    aPerf(),
                    RecordingIntents(),
                )
            }
        }
        text("Quit Desktop").performScrollTo().performClick()
        popup().assertIsDisplayed()

        composeRule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        composeRule.waitForIdle()

        text("Cancel").assertIsFocused()
    }
}
