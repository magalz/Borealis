package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
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
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class KeyboardKeyTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0
    private var longClicks = 0

    @Test
    fun `a tap clicks and the key is announced as a button`() {
        composeRule.setContent { Specimen { KeyboardKey("A", { clicks++ }) } }

        composeRule
            .onNodeWithText("A")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a long press runs the long click and not the click`() {
        composeRule.setContent {
            Specimen { KeyboardKey("A", { clicks++ }, onLongClick = { longClicks++ }) }
        }

        composeRule.onNodeWithText("A").performTouchInput { longClick() }

        assertThat(longClicks).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `X on a gamepad runs the long click`() {
        composeRule.setContentInKeyboardMode {
            Specimen { KeyboardKey("A", { clicks++ }, onLongClick = { longClicks++ }) }
        }
        composeRule.onNodeWithText("A").requestFocus()

        composeRule.onNodeWithText("A").performKeyInput { pressKey(Key.ButtonX) }

        assertThat(longClicks).isEqualTo(1)
    }

    @Test
    fun `a latched key says it is locked`() {
        composeRule.setContent { Specimen { KeyboardKey("Ctrl", {}, latched = true) } }

        composeRule
            .onNodeWithText("Ctrl")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Locked"))
    }

    @Test
    fun `a key that is not latched has no state`() {
        composeRule.setContent { Specimen { KeyboardKey("Ctrl", {}) } }

        composeRule
            .onNodeWithText("Ctrl")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun `a key is 40dp high on a TV`() {
        composeRule.setContent { Specimen { KeyboardKey("A", {}) } }

        assertThat(composeRule.onNodeWithText("A").getUnclippedBoundsInRoot().height)
            .isEqualTo(40.dp)
    }

    @Test
    fun `a key is 40dp high on a handheld`() {
        composeRule.setContent { Specimen(FormFactor.Handheld) { KeyboardKey("A", {}) } }

        assertThat(composeRule.onNodeWithText("A").getUnclippedBoundsInRoot().height)
            .isEqualTo(40.dp)
    }

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `a key is 32dp high on a phone`() {
        composeRule.setContent { Specimen(FormFactor.Phone) { KeyboardKey("A", {}) } }

        assertThat(composeRule.onNodeWithText("A").getUnclippedBoundsInRoot().height)
            .isEqualTo(32.dp)
    }

    @Test
    fun `keys are 44dp, 72dp and 240dp wide at most by style`() {
        composeRule.setContent {
            Specimen {
                KeyboardKey("A", {})
                KeyboardKey("Backspace", {}, style = KeyStyle.Wide)
                KeyboardKey("Space", {}, style = KeyStyle.Space)
            }
        }

        assertThat(composeRule.onNodeWithText("A").getUnclippedBoundsInRoot().width)
            .isEqualTo(44.dp)
        assertThat(composeRule.onNodeWithText("Backspace").getUnclippedBoundsInRoot().width)
            .isEqualTo(72.dp)
        assertThat(composeRule.onNodeWithText("Space").getUnclippedBoundsInRoot().width)
            .isEqualTo(240.dp)
    }

    @Test
    fun `a row that must shrink keeps the 44 to 72 to 240 proportion of the key styles`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                Row(Modifier.size(200.dp, 60.dp)) {
                    for ((label, style) in
                        listOf(
                            "A" to KeyStyle.Normal,
                            "Backspace" to KeyStyle.Wide,
                            "Space" to KeyStyle.Space,
                        )) {
                        KeyboardKey(
                            label,
                            {},
                            Modifier.weight(style.weight, fill = false),
                            style = style,
                        )
                    }
                }
            }
        }

        val normal = composeRule.onNodeWithText("A").getUnclippedBoundsInRoot().width.value
        val wide = composeRule.onNodeWithText("Backspace").getUnclippedBoundsInRoot().width.value
        val space = composeRule.onNodeWithText("Space").getUnclippedBoundsInRoot().width.value
        assertThat(wide / normal).isWithin(0.1f).of(72f / 44f)
        assertThat(space / normal).isWithin(0.1f).of(240f / 44f)
        assertThat(normal + wide + space).isWithin(1f).of(200f)
    }

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Tv,
        content: @Composable () -> Unit,
    ) {
        BorealisTheme(formFactor) { Row(Modifier) { content() } }
    }
}
