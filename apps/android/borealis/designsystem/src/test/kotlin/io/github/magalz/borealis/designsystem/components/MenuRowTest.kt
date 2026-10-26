package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Column
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

    @Test
    fun `the trailing content shows and the D-pad reaches it from the row`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Bitrate",
                    {},
                    trailing = {
                        SegmentedControl(
                            options = listOf("Low", "High"),
                            selected = "Low",
                            label = { it },
                            onSelect = {},
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithText("Bitrate").requestFocus()

        composeRule.onNodeWithText("Bitrate").performKeyInput { pressKey(Key.DirectionRight) }

        composeRule.onNodeWithText("Low").assertIsFocused()
    }

    @Test
    fun `Left moves between the trailing segments and from the first one back to the row`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Bitrate",
                    {},
                    trailing = {
                        SegmentedControl(
                            options = listOf("Low", "High"),
                            selected = "Low",
                            label = { it },
                            onSelect = {},
                        )
                    },
                )
            }
        }
        composeRule.onNodeWithText("High").requestFocus()

        composeRule.onNodeWithText("High").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Low").assertIsFocused()

        composeRule.onNodeWithText("Low").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Bitrate").assertIsFocused()
    }

    @Test
    fun `Left from the first segment of each stacked row returns to its own row`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                MenuRow(
                    "Codec",
                    {},
                    trailing = {
                        SegmentedControl(
                            listOf("Auto", "Always use H.264 or HEVC"),
                            "Auto",
                            { it },
                            {},
                        )
                    },
                )
                MenuRow(
                    "Bitrate",
                    {},
                    trailing = {
                        SegmentedControl(listOf("Low", "High"), "Low", { it }, {})
                    },
                )
            }
        }

        composeRule.onNodeWithText("Low").requestFocus()
        composeRule.onNodeWithText("Low").performKeyInput { pressKey(Key.DirectionLeft) }
        composeRule.onNodeWithText("Bitrate").assertIsFocused()

        composeRule.onNodeWithText("Auto").requestFocus()
        composeRule.onNodeWithText("Auto").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        composeRule.onNodeWithText("Codec").assertIsFocused()
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
