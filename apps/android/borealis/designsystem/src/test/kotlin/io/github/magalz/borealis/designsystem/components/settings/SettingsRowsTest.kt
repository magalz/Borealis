package io.github.magalz.borealis.designsystem.components.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SettingsRowsTest {
    @get:Rule val composeRule = createComposeRule()
    private val toggles = mutableListOf<Boolean>()
    private var clicks = 0

    @Test
    fun `a tap on an off switch row asks for on`() {
        composeRule.setContent { Specimen { SettingsSwitchRow("HDR", false, { toggles += it }) } }

        composeRule.onNodeWithText("HDR").performClick()

        assertThat(toggles).containsExactly(true)
    }

    @Test
    fun `a tap on an on switch row asks for off`() {
        composeRule.setContent { Specimen { SettingsSwitchRow("HDR", true, { toggles += it }) } }

        composeRule.onNodeWithText("HDR").performClick()

        assertThat(toggles).containsExactly(false)
    }

    @Test
    fun `OK and Enter toggle a switch row`() {
        composeRule.setContentInKeyboardMode {
            Specimen { SettingsSwitchRow("HDR", false, { toggles += it }) }
        }
        composeRule.onNodeWithText("HDR").requestFocus()

        composeRule.onNodeWithText("HDR").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("HDR").performKeyInput { pressKey(Key.Enter) }

        assertThat(toggles).containsExactly(true, true)
    }

    @Test
    fun `a switch row is announced as a switch with its state`() {
        composeRule.setContent {
            Specimen {
                SettingsSwitchRow("HDR", true, {})
                SettingsSwitchRow("Full range", false, {})
            }
        }

        composeRule
            .onNodeWithText("HDR")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(toggleState(ToggleableState.On))
        composeRule.onNodeWithText("Full range").assert(toggleState(ToggleableState.Off))
    }

    @Test
    fun `the summary shows under the title of a switch row`() {
        composeRule.setContent {
            Specimen {
                SettingsSwitchRow("Verbose logging", false, {}, summary = "Writes debug details")
            }
        }

        composeRule.onNodeWithText("Verbose logging").assertIsDisplayed()
        composeRule
            .onNodeWithText("Writes debug details", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun `a disabled switch row ignores a tap and OK and shows its note`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                SettingsSwitchRow(
                    "HDR",
                    false,
                    { toggles += it },
                    enabled = false,
                    disabledNote = "This device has no HDR display",
                )
            }
        }

        composeRule.onNodeWithText("HDR").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("HDR").requestFocus().assertIsFocused()
        composeRule.onNodeWithText("HDR").performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule.onNodeWithText("HDR").assertIsFocused()
        composeRule
            .onNodeWithText("This device has no HDR display", useUnmergedTree = true)
            .assertIsDisplayed()
        assertThat(toggles).isEmpty()
    }

    @Test
    fun `the note of an enabled row stays hidden`() {
        composeRule.setContent {
            Specimen {
                SettingsSwitchRow("HDR", false, {}, disabledNote = "This device has no HDR display")
            }
        }

        composeRule
            .onNodeWithText("This device has no HDR display", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun `a value row shows its title and value`() {
        composeRule.setContent { Specimen { SettingsValueRow("Frame rate", "60 fps", {}) } }

        composeRule.onNodeWithText("Frame rate", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("60 fps", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a tap and OK call onClick and the row is announced as a button`() {
        composeRule.setContentInKeyboardMode {
            Specimen { SettingsValueRow("Frame rate", "60 fps", { clicks++ }) }
        }

        composeRule
            .onNodeWithText("Frame rate")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule.onNodeWithText("Frame rate").requestFocus()
        composeRule.onNodeWithText("Frame rate").performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(clicks).isEqualTo(2)
    }

    @Test
    fun `a value row without a value is a link and shows no value`() {
        composeRule.setContent { Specimen { SettingsValueRow("Audit trail", null, { clicks++ }) } }

        composeRule.onNodeWithText("Audit trail").performClick()

        assertThat(clicks).isEqualTo(1)
        composeRule.onNodeWithText("null", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a danger value row has the danger ink and a normal one the ink`() {
        composeRule.setContent {
            Specimen {
                SettingsValueRow("Erase audit trail", null, {}, tone = SettingsRowTone.Danger)
                SettingsValueRow("Audit trail", null, {})
            }
        }

        assertThat(composeRule.textColor("Erase audit trail"))
            .isEqualTo(BorealisTokens.Color.DangerInk)
        assertThat(composeRule.textColor("Audit trail")).isEqualTo(BorealisTokens.Color.Ink)
    }

    @Test
    fun `a disabled value row ignores OK and shows its note`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                SettingsValueRow(
                    "Codec",
                    "Automatic",
                    { clicks++ },
                    enabled = false,
                    disabledNote = "The host does not offer HEVC",
                )
            }
        }

        composeRule.onNodeWithText("Codec").assertIsNotEnabled().performClick()
        composeRule.onNodeWithText("Codec").requestFocus().assertIsFocused()
        composeRule.onNodeWithText("Codec").performKeyInput { pressKey(Key.DirectionCenter) }

        composeRule
            .onNodeWithText("The host does not offer HEVC", useUnmergedTree = true)
            .assertIsDisplayed()
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `a long title and a long value wrap instead of being cut`() {
        composeRule.setContent {
            Specimen(Modifier.width(280.dp)) {
                SettingsValueRow(
                    "Mouse back and forward buttons for the remote desktop",
                    "Send them to the host as they are pressed",
                    {},
                )
            }
        }

        val title =
            composeRule
                .onNodeWithText(
                    "Mouse back and forward buttons for the remote desktop",
                    useUnmergedTree = true,
                )
                .textLayout()
        val value =
            composeRule
                .onNodeWithText("Send them to the host as they are pressed", useUnmergedTree = true)
                .textLayout()
        assertThat(title.lineCount).isGreaterThan(1)
        assertThat(title.hasVisualOverflow).isFalse()
        assertThat(value.lineCount).isGreaterThan(1)
        assertThat(value.hasVisualOverflow).isFalse()
    }

    @Test
    fun `a value row names what a tap does for a screen reader`() {
        composeRule.setContent {
            Specimen {
                SettingsValueRow("Frame rate", "60 fps", {}, onClickLabel = "Choose the frame rate")
            }
        }

        composeRule
            .onNodeWithText("Frame rate")
            .assert(
                SemanticsMatcher("has the click label") {
                    it.config.getOrNull(SemanticsActions.OnClick)?.label == "Choose the frame rate"
                }
            )
    }

    @Test
    fun `the value sits at the end of the row, next to the chevron`() {
        composeRule.setContent {
            Specimen(Modifier.width(400.dp)) { SettingsValueRow("Frame rate", "60 fps", {}) }
        }

        val value = composeRule.onNodeWithText("60 fps", useUnmergedTree = true).getBoundsInRoot()

        // 16dp of padding, the 20dp chevron icon and a 12dp gap from the end of the row.
        assertThat(value.right.value).isWithin(1f).of(400f - 16f - 20f - 12f)
    }

    @Test
    fun `a short value leaves most of the row to a long title`() {
        composeRule.setContent {
            Specimen(Modifier.width(400.dp)) {
                SettingsValueRow("Mouse back and forward buttons", "Off", {})
            }
        }

        val title =
            composeRule
                .onNodeWithText("Mouse back and forward buttons", useUnmergedTree = true)
                .textLayout()
        assertThat(title.lineCount).isEqualTo(1)
    }

    @Test
    fun `an info row shows its value at the end of the row, with no chevron after it`() {
        composeRule.setContent {
            Specimen(Modifier.width(400.dp)) {
                SettingsInfoRow("Version", "9.8.7 (commit abc1234)")
            }
        }

        val value =
            composeRule
                .onNodeWithText("9.8.7 (commit abc1234)", useUnmergedTree = true)
                .getBoundsInRoot()

        // Only the 16dp of padding: nothing opens from the row, so there is no chevron.
        assertThat(value.right.value).isWithin(1f).of(400f - 16f)
    }

    @Test
    fun `an info row is read as text, with no button role and no click action`() {
        composeRule.setContent { Specimen { SettingsInfoRow("Version", "9.8.7 (commit abc1234)") } }

        composeRule
            .onNodeWithText("Version")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Role))
    }

    @Test
    fun `a screen reader reads the title and the value of an info row as one`() {
        composeRule.setContent { Specimen { SettingsInfoRow("Version", "9.8.7 (commit abc1234)") } }

        composeRule
            .onNodeWithText("Version")
            .assert(hasText("Version") and hasText("9.8.7 (commit abc1234)"))
    }

    @Test
    fun `an info row stays a focus stop that OK does not act on`() {
        composeRule.setContentInKeyboardMode {
            Specimen {
                SettingsValueRow("Frame rate", "60 fps", { clicks++ })
                SettingsInfoRow("Version", "9.8.7 (commit abc1234)")
                SettingsValueRow("Source code", null, { clicks++ })
            }
        }
        composeRule.onNodeWithText("Frame rate").requestFocus()

        composeRule.onNodeWithText("Frame rate").performKeyInput { pressKey(Key.DirectionDown) }
        composeRule.onNodeWithText("Version").assertIsFocused()
        composeRule.onNodeWithText("Version").performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.onNodeWithText("Version").performKeyInput { pressKey(Key.DirectionDown) }

        composeRule.onNodeWithText("Source code").assertIsFocused()
        assertThat(clicks).isEqualTo(0)
    }

    private fun toggleState(state: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    @Composable
    private fun Specimen(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
        BorealisTheme(FormFactor.Tv) { Column(modifier) { content() } }
    }
}
