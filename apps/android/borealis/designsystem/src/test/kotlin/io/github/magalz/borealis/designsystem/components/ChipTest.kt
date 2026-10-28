package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisFonts
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class ChipTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0

    @Test
    fun `a chip with an action clicks and is announced as a button`() {
        composeRule.setContent { Specimen { Chip("Win+D", onClick = { clicks++ }) } }

        composeRule
            .onNodeWithText("Win+D")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a chip without an action is a static tag`() {
        composeRule.setContent { Specimen { Chip("HEVC") } }

        composeRule.onNodeWithText("HEVC").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun `a selected chip with an action is announced as selected`() {
        composeRule.setContent {
            Specimen {
                Chip("Alt+Tab", selected = true, onClick = {})
                Chip("Win+D", onClick = {}, modifier = Modifier.padding(top = 40.dp))
            }
        }

        composeRule.onNodeWithText("Alt+Tab").assertIsSelected()
        composeRule.onNodeWithText("Win+D").assertIsNotSelected()
    }

    @Test
    fun `a selected static chip is announced as selected`() {
        composeRule.setContent { Specimen { Chip("HEVC", selected = true) } }

        composeRule.onNodeWithText("HEVC").assertIsSelected()
    }

    @Test
    fun `a selected chip has the accent tint and a plain chip has none`() {
        composeRule.setContent {
            Specimen {
                Chip("Alt+Tab", selected = true, modifier = Modifier.testTag("on"))
                Chip("Win+D", modifier = Modifier.padding(top = 40.dp).testTag("off"))
            }
        }

        // Next to the text, inside the chip: the tint is over the page, a plain chip shows the
        // page.
        val on = composeRule.onNodeWithTag(FRAME)
        on.assertPixel(x = 24f + 3f, y = 24f + 3f, tintOverPage())
        on.assertPixel(x = 24f + 3f, y = 24f + 43f, BorealisTokens.Color.Page)
    }

    @Test
    @Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
    fun `a focused chip shows the outline in keyboard mode`() {
        composeRule.setContentInKeyboardMode {
            Specimen(FormFactor.Tv) { Chip("Win+D", onClick = {}) }
        }

        composeRule.onNodeWithText("Win+D").requestFocus()

        val width = composeRule.onNodeWithText("Win+D").getUnclippedBoundsInRoot().width.value
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(x = 24f + width / 2, y = 24f - 3.5f, BorealisTokens.Color.AccentSoft)
    }

    @Test
    fun `a chip focused by touch shows no outline`() {
        composeRule.setContent { Specimen { Chip("Win+D", onClick = {}) } }

        composeRule.onNodeWithText("Win+D").requestFocus()

        val width = composeRule.onNodeWithText("Win+D").getUnclippedBoundsInRoot().width.value
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(x = 24f + width / 2, y = 24f - 3.5f, BorealisTokens.Color.Page)
    }

    @Test
    fun `a tag shows its text and is not clickable`() {
        composeRule.setContent { Specimen { Tag("HDR") } }

        composeRule.onNodeWithText("HDR").assertIsDisplayed().assertHasNoClickAction()
    }

    @Test
    fun `a chip is padded 9dp by 4dp around its text`() {
        composeRule.setContent {
            Specimen {
                Column {
                    Chip("Win+D", Modifier.testTag("chip"))
                    Text(
                        "Win+D",
                        Modifier.testTag("plain"),
                        style =
                            TextStyle(
                                fontFamily = BorealisFonts.of(FontChoice.Plex).codes,
                                fontSize = 12.sp,
                            ),
                    )
                }
            }
        }

        val chip = composeRule.onNodeWithTag("chip").getUnclippedBoundsInRoot()
        val plain = composeRule.onNodeWithTag("plain").getUnclippedBoundsInRoot()
        assertThat(chip.width - plain.width).isEqualTo(18.dp)
        assertThat(chip.height - plain.height).isEqualTo(8.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a chip is 13 sp on a TV`() {
        composeRule.setContent { Specimen(FormFactor.Tv) { Chip("Win+D") } }

        assertThat(textSize("Win+D")).isEqualTo(13.sp)
    }

    @Test
    fun `a chip stays 12 sp on a phone`() {
        composeRule.setContent { Specimen(FormFactor.Phone) { Chip("Win+D") } }

        assertThat(textSize("Win+D")).isEqualTo(12.sp)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a chip stays 12 sp on a handheld`() {
        composeRule.setContent { Specimen(FormFactor.Handheld) { Chip("Win+D") } }

        assertThat(textSize("Win+D")).isEqualTo(12.sp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a tag is 13 sp on a TV`() {
        composeRule.setContent { Specimen(FormFactor.Tv) { Tag("HDR") } }

        assertThat(textSize("HDR")).isEqualTo(13.sp)
    }

    @Test
    fun `a tag stays 11 sp on a phone`() {
        composeRule.setContent { Specimen(FormFactor.Phone) { Tag("HDR") } }

        assertThat(textSize("HDR")).isEqualTo(11.sp)
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `a tag stays 12 sp on a handheld`() {
        composeRule.setContent { Specimen(FormFactor.Handheld) { Tag("HDR") } }

        assertThat(textSize("HDR")).isEqualTo(12.sp)
    }

    private fun textSize(text: String) =
        composeRule.onNodeWithText(text).textLayout().layoutInput.style.fontSize

    private fun tintOverPage() =
        BorealisTokens.Color.AccentTint.compositeOver(BorealisTokens.Color.Page)

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Phone,
        content: @Composable () -> Unit,
    ) {
        BorealisTheme(formFactor) {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(24.dp)) {
                content()
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
        const val HANDHELD = "w800dp-h360dp-land-xhdpi"
    }
}
