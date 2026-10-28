package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class SmallComponentsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `a kicker is set in capitals`() {
        composeRule.setContent { BorealisTheme { Kicker("Living room PC") } }

        composeRule.onNodeWithText("LIVING ROOM PC").assertIsDisplayed()
    }

    @Test
    fun `a PIN is announced digit by digit`() {
        composeRule.setContent { BorealisTheme { PinDisplay("4817") } }

        composeRule.onNodeWithContentDescription("4, 8, 1, 7").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("4, 8, 1, 7")
            .assertContentDescriptionEquals("4, 8, 1, 7")
    }

    @Test
    fun `an empty state shows its title and body`() {
        composeRule.setContent {
            BorealisTheme { EmptyState(title = "Looking for hosts", body = "Keep the PC on.") }
        }

        composeRule.onNodeWithText("Looking for hosts").assertIsDisplayed()
        composeRule.onNodeWithText("Keep the PC on.").assertIsDisplayed()
    }

    @Test
    fun `an empty state action runs`() {
        var runs = 0
        composeRule.setContent {
            BorealisTheme {
                EmptyState(
                    title = "Looking for hosts",
                    body = "Keep the PC on.",
                    action = EmptyStateAction("Add host") { runs++ },
                )
            }
        }

        composeRule.onNodeWithText("Add host").performClick()

        assertThat(runs).isEqualTo(1)
    }

    @Test
    fun `an empty state without an action has no button`() {
        composeRule.setContent {
            BorealisTheme { EmptyState(title = "Looking for hosts", body = "Keep the PC on.") }
        }

        composeRule.onNodeWithText("Add host").assertDoesNotExist()
    }

    @Test
    fun `the text field shows its label and the typed text`() {
        composeRule.setContent { Field() }

        composeRule.onNodeWithText("Address").assertIsDisplayed()
        composeRule.onNodeWithText("Address").performTextInput("desk.local")

        composeRule.onNodeWithText("desk.local").assertIsDisplayed()
    }

    @Test
    fun `the text field shows its error`() {
        composeRule.setContent { Field(error = "Enter an IP address or a host name") }

        composeRule.onNodeWithText("Enter an IP address or a host name").assertIsDisplayed()
    }

    @Test
    fun `the text field shows no error by default`() {
        composeRule.setContent { Field() }

        composeRule.onNodeWithText("Enter an IP address or a host name").assertDoesNotExist()
    }

    @Test
    fun `the IME Done action calls the callback`() {
        var done = 0
        composeRule.setContent { Field(onDone = { done++ }) }

        composeRule.onNodeWithText("Address").performImeAction()

        assertThat(done).isEqualTo(1)
    }

    @Test
    fun `the rail shows every item and marks the selected one`() {
        composeRule.setContent { Rail(selected = 1) }

        composeRule.onNodeWithText("Home").assertIsNotSelected()
        composeRule.onNodeWithText("Settings").assertIsSelected()
    }

    @Test
    fun `choosing a rail item reports its index`() {
        val chosen = mutableListOf<Int>()
        composeRule.setContent { Rail(selected = 0, onSelect = { chosen += it }) }

        composeRule.onNodeWithText("Settings").performClick()

        assertThat(chosen).containsExactly(1)
    }

    @Composable
    private fun Field(error: String? = null, onDone: () -> Unit = {}) {
        var value by remember { mutableStateOf("") }
        BorealisTheme {
            Column {
                BorealisTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Address",
                    error = error,
                    onDone = onDone,
                )
            }
        }
    }

    @Test
    fun `a D-pad that enters the rail lands on the selected item, not the nearest`() {
        composeRule.setContentInKeyboardMode {
            Row(Modifier.fillMaxHeight()) {
                Box(Modifier.width(200.dp)) { Rail(selected = 0) }
                // At the bottom, so the nearest item is the last one.
                Box(Modifier.size(40.dp).testTag("content").focusable().align(Alignment.Bottom))
            }
        }
        composeRule.onNodeWithTag("content").requestFocus()

        composeRule.onNodeWithTag("content").performKeyInput { pressKey(Key.DirectionLeft) }

        composeRule.onNode(isFocused()).assert(hasText("Home"))
    }

    @Test
    fun `a direct focus request on a rail item is not redirected to the selected one`() {
        composeRule.setContentInKeyboardMode { Box(Modifier.width(200.dp)) { Rail(selected = 0) } }

        composeRule.onNodeWithText("Settings").requestFocus()

        composeRule.onNode(isFocused()).assert(hasText("Settings"))
    }

    @Test
    fun `the rail is 220dp wide on a TV`() {
        composeRule.setContent { Rail(selected = 0, formFactor = FormFactor.Tv, tag = "rail") }

        val rail = composeRule.onNodeWithTag("rail").getUnclippedBoundsInRoot()

        assertThat(rail.right - rail.left).isEqualTo(220.dp)
    }

    @Test
    fun `the rail is 200dp wide on a handheld`() {
        composeRule.setContent {
            Rail(selected = 0, formFactor = FormFactor.Handheld, tag = "rail")
        }

        val rail = composeRule.onNodeWithTag("rail").getUnclippedBoundsInRoot()

        assertThat(rail.right - rail.left).isEqualTo(200.dp)
    }

    @Composable
    private fun Rail(
        selected: Int,
        onSelect: (Int) -> Unit = {},
        formFactor: FormFactor = FormFactor.Tv,
        tag: String = "",
    ) {
        BorealisTheme(formFactor) {
            NavigationRail(
                items = listOf(RailItem("Home", ICON), RailItem("Settings", ICON)),
                selectedIndex = selected,
                onSelect = onSelect,
                modifier = Modifier.testTag(tag),
            )
        }
    }

    private companion object {
        val ICON: ImageVector =
            ImageVector.Builder(
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .path {
                    moveTo(4f, 4f)
                    lineTo(20f, 4f)
                    lineTo(20f, 20f)
                    lineTo(4f, 20f)
                    close()
                }
                .build()
    }
}
