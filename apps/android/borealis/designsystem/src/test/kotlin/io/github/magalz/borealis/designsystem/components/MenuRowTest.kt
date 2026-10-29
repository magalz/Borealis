package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class MenuRowTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0

    @Test
    fun `a tap clicks and the row is announced as a button`() {
        composeRule.setContent { Specimen { MenuRow("Back to stream", { clicks++ }) } }

        composeRule
            .onNodeWithText("Back to stream")
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `the consequence shows under the title`() {
        composeRule.setContent {
            Specimen { MenuRow("Quit", {}, consequence = "Closes the app on the host") }
        }

        composeRule.onNodeWithText("Quit", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText("Closes the app on the host", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `a disabled row does not click`() {
        composeRule.setContent {
            Specimen { MenuRow("Mute microphone", { clicks++ }, enabled = false) }
        }

        composeRule.onNodeWithText("Mute microphone").assertIsNotEnabled().performClick()

        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a disabled row stays focusable so its note can be read on a TV`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Mute microphone",
                    { clicks++ },
                    enabled = false,
                    note = "The host has no microphone",
                )
            }
        }

        composeRule.onNodeWithText("Mute microphone").requestFocus().assertIsFocused()
        composeRule.onNodeWithText("Mute microphone").performKeyInput {
            pressKey(Key.DirectionCenter)
        }

        composeRule.onNodeWithText("Mute microphone").assertIsFocused()
        composeRule
            .onNodeWithText("The host has no microphone", useUnmergedTree = true)
            .assertIsDisplayed()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `an enabled row takes OK`() {
        composeRule.setContentInKeyboardMode { Specimen { MenuRow("Resume", { clicks++ }) } }
        composeRule.onNodeWithText("Resume").requestFocus()

        composeRule.onNodeWithText("Resume").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a danger row has the danger ink and a normal row the ink`() {
        composeRule.setContent {
            Specimen {
                MenuRow("Quit", {}, style = MenuRowStyle.Danger)
                MenuRow("Resume", {})
            }
        }

        assertThat(composeRule.textColor("Quit")).isEqualTo(BorealisTokens.Color.DangerInk)
        assertThat(composeRule.textColor("Resume")).isEqualTo(BorealisTokens.Color.Ink)
    }

    @Test
    fun `a row is 48dp high on a TV`() {
        composeRule.setContent {
            Specimen { MenuRow("Resume", {}, modifier = Modifier.testTag(ROW)) }
        }

        assertThat(composeRule.onNodeWithTag(ROW).getUnclippedBoundsInRoot().height)
            .isEqualTo(48.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `a row is 44dp high on a phone`() {
        composeRule.setContent {
            Specimen(FormFactor.Phone) { MenuRow("Resume", {}, modifier = Modifier.testTag(ROW)) }
        }

        assertThat(composeRule.onNodeWithTag(ROW).getUnclippedBoundsInRoot().height)
            .isEqualTo(44.dp)
    }

    // Owner decision of 2026-10-28 (issue #98): the menu has no lateral movement, so the control at
    // the end of a row is never a focus stop. The row is the one stop, and its button changes it.
    @Test
    fun `the segments at the end of a row are not focus stops and Right keeps the focus on the row`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow("Bitrate", {}, trailing = { LowHigh() })
                MenuRow("Refresh", {})
            }
        }
        composeRule.onNodeWithText("Bitrate").requestFocus()

        composeRule.onNodeWithText("Bitrate").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Bitrate").assertIsFocused()

        composeRule.onNodeWithText("Low").assertIsDisplayed()
        composeRule.onNodeWithText("Bitrate").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Refresh").assertIsFocused()
        composeRule.onNodeWithText("Refresh").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Bitrate").assertIsFocused()
    }

    @Test
    fun `a tap on a segment selects it and does not run the click of the row`() {
        val chosen = mutableListOf<String>()
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Bitrate",
                    { clicks++ },
                    trailing = {
                        SegmentedControl(
                            listOf("Low", "High"),
                            "Low",
                            { it },
                            { chosen += it },
                            focusable = false,
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithText("High").performClick()

        assertThat(chosen).containsExactly("High")
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `OK on a row with segments runs the click of the row, not a segment`() {
        val chosen = mutableListOf<String>()
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Bitrate",
                    { clicks++ },
                    trailing = {
                        SegmentedControl(
                            listOf("Low", "High"),
                            "Low",
                            { it },
                            { chosen += it },
                            focusable = false,
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithText("Bitrate").requestFocus()

        composeRule.onNodeWithText("Bitrate").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(clicks).isEqualTo(1)
        assertThat(chosen).isEmpty()
    }

    @Test
    fun `OK on a row with a switch at the end runs the click of the row and the switch only shows`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Mute microphone",
                    { clicks++ },
                    trailing = { Switch(checked = false, onCheckedChange = null) },
                )
            }
        }
        composeRule.onNodeWithText("Mute microphone").requestFocus()

        composeRule.onNodeWithText("Mute microphone").performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        composeRule.onNodeWithText("Mute microphone").performKeyInput {
            pressKey(Key.DirectionRight)
        }

        assertThat(clicks).isEqualTo(1)
        composeRule.onNodeWithText("Mute microphone").assertIsFocused()
    }

    // Sprint 12 round finding D4: Right on a row whose end is only a mark lost the focus.
    @Test
    fun `Right and Left keep the focus on a row whose trailing is only a mark`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Send keys",
                    {},
                    trailing = { Icon(BorealisIcons.ChevronRight, contentDescription = null) },
                )
                MenuRow("Keyboard", {})
            }
        }
        composeRule.onNodeWithText("Send keys").requestFocus()

        composeRule.onNodeWithText("Send keys").performKeyInput { pressKey(Key.DirectionRight) }
        composeRule.onNodeWithText("Send keys").assertIsFocused()

        composeRule.onNodeWithText("Send keys").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Send keys").assertIsFocused()
    }

    @Test
    fun `a row whose trailing is only a mark still has the rows around it one key away`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow("Keyboard", {})
                MenuRow(
                    "Send keys",
                    {},
                    trailing = { Icon(BorealisIcons.ChevronRight, contentDescription = null) },
                )
                MenuRow("Refresh", {})
            }
        }
        composeRule.onNodeWithText("Send keys").requestFocus()
        composeRule.onNodeWithText("Send keys").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Send keys").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Refresh").assertIsFocused()

        composeRule.onNodeWithText("Refresh").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Send keys").performKeyInput { pressKey(Key.DirectionUp) }
        composeRule.onNodeWithText("Keyboard").assertIsFocused()
    }

    @Composable
    private fun LowHigh() {
        SegmentedControl(
            options = listOf("Low", "High"),
            selected = "Low",
            label = { it },
            onSelect = {},
            focusable = false,
        )
    }

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Tv,
        content: @Composable () -> Unit,
    ) {
        BorealisTheme(formFactor) { Column { content() } }
    }

    private companion object {
        const val ROW = "row"
    }
}
