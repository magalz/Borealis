package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textColor
import io.github.magalz.borealis.designsystem.theme.AccentChoice
import io.github.magalz.borealis.designsystem.theme.BorealisColors
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.borealisColors
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class BorealisButtonTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0
    private var look by mutableStateOf(BorealisLook())
    private var buttonStyle by mutableStateOf(BorealisButtonStyle.Primary)

    @Test
    fun `a tap clicks`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Open").performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a disabled button does not click`() {
        composeRule.setContent { Specimen(enabled = false) }

        composeRule.onNodeWithText("Open").assertIsNotEnabled().performClick()

        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `an enabled button is announced as a button`() {
        composeRule.setContent { Specimen() }

        composeRule
            .onNodeWithText("Open")
            .assertIsEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test
    fun `a button is at least 40dp high on a phone`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Open").assertHeightIsAtLeast(40.dp)
        assertThat(composeRule.onNodeWithText("Open").getUnclippedBoundsInRoot().height)
            .isEqualTo(40.dp)
    }

    @Test
    fun `a button is at least 48dp high on a TV`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        assertThat(composeRule.onNodeWithText("Open").getUnclippedBoundsInRoot().height)
            .isEqualTo(48.dp)
    }

    @Test
    fun `a primary button has an accent fill`() {
        composeRule.setContent { Specimen(style = BorealisButtonStyle.Primary) }

        composeRule.onNodeWithTag(FRAME).assertPixel(x = 27f, y = 44f, BorealisTokens.Color.Accent)
    }

    @Test
    fun `a secondary button has no fill`() {
        composeRule.setContent { Specimen(style = BorealisButtonStyle.Secondary) }

        composeRule.onNodeWithTag(FRAME).assertPixel(x = 27f, y = 44f, BorealisTokens.Color.Page)
    }

    @Test
    fun `the label of a text button is accent-soft for every accent, which is safe on the glass`() {
        buttonStyle = BorealisButtonStyle.Text
        composeRule.setContent { Specimen(style = buttonStyle, look = look) }

        for (accent in AccentChoice.entries) {
            look = BorealisLook(accent = accent)
            composeRule.waitForIdle()

            assertWithMessage("$accent")
                .that(composeRule.textColor("Open"))
                .isEqualTo(borealisColors(look).accentSoft)
        }
    }

    @Test
    fun `the labels of the other styles are as they were`() {
        composeRule.setContent { Specimen(style = buttonStyle, look = look) }
        val colors = BorealisColors()

        for ((style, expected) in
            listOf(
                BorealisButtonStyle.Primary to colors.onAccent,
                BorealisButtonStyle.Secondary to colors.ink,
                BorealisButtonStyle.Danger to colors.dangerInk,
            )) {
            buttonStyle = style
            composeRule.waitForIdle()

            assertWithMessage("$style").that(composeRule.textColor("Open")).isEqualTo(expected)
        }
    }

    @Test
    fun `a pressed button gets a faint wash that goes when it is released`() {
        composeRule.setContent { Specimen(style = BorealisButtonStyle.Secondary) }
        val unpressed = composeRule.onNodeWithTag(FRAME).pixelAt(x = 27f, y = 44f)

        composeRule.onNodeWithText("Open").performTouchInput { down(center) }
        composeRule.waitForIdle()
        val pressed = composeRule.onNodeWithTag(FRAME).pixelAt(x = 27f, y = 44f)
        composeRule.onNodeWithText("Open").performTouchInput { up() }
        composeRule.waitForIdle()
        val released = composeRule.onNodeWithTag(FRAME).pixelAt(x = 27f, y = 44f)

        assertThat(pressed.red).isGreaterThan(unpressed.red)
        assertThat(released).isEqualTo(unpressed)
    }

    @Test
    fun `a focused button shows the outline outside its shape`() {
        composeRule.setContentInKeyboardMode { Specimen(FormFactor.Tv) }

        composeRule.onNodeWithText("Open").requestFocus()

        // The button starts at 24dp in the frame; the outline is centred 3.5dp outside its top
        // edge.
        val width = composeRule.onNodeWithText("Open").getUnclippedBoundsInRoot().width.value
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(x = 24f + width / 2, y = 24f - 3.5f, BorealisTokens.Color.AccentSoft)
    }

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Phone,
        style: BorealisButtonStyle = BorealisButtonStyle.Primary,
        enabled: Boolean = true,
        look: BorealisLook = BorealisLook(),
    ) {
        BorealisTheme(formFactor, look) {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(24.dp)) {
                BorealisButton("Open", onClick = { clicks++ }, style = style, enabled = enabled)
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
    }
}
