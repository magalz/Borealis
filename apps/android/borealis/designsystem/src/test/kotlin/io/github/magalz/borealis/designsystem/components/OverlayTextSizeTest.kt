package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dialogs and the sheets follow the Text size setting (ADR 0057, decision 11): they are windows
 * of their own, and a window that is not given the font scale of the theme would show its text at
 * the size of the system, below what the screen behind it shows.
 */
@RunWith(AndroidJUnit4::class)
@Config(fontScale = SYSTEM_SCALE)
class OverlayTextSizeTest {
    @get:Rule val composeRule = createComposeRule()

    private val largest = BorealisLook(textSize = TextSize.Largest)

    @Test
    @Config(qualifiers = TV)
    fun `a dialog has the text size times the font scale of the system`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, largest) {
                BorealisDialog(
                    title = "Forget this host?",
                    onDismissRequest = {},
                    confirm = DialogAction("Forget") {},
                ) {
                    Text("Its pairing is removed.")
                }
            }
        }
        composeRule.waitForIdle()

        assertThat(fontScaleOf("Its pairing is removed.")).isWithin(EPSILON).of(EXPECTED)
        assertThat(fontScaleOf("Forget this host?")).isWithin(EPSILON).of(EXPECTED)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a sheet that is a dialog has the text size times the font scale of the system`() {
        composeRule.setContent { Sheet(FormFactor.Tv) }
        composeRule.waitForIdle()

        assertThat(fontScaleOf("Resume")).isWithin(EPSILON).of(EXPECTED)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `a bottom sheet has the text size times the font scale of the system`() {
        composeRule.setContent { Sheet(FormFactor.Phone) }
        composeRule.waitForIdle()

        assertThat(fontScaleOf("Resume")).isWithin(EPSILON).of(EXPECTED)
    }

    @Composable
    private fun Sheet(formFactor: FormFactor) {
        BorealisTheme(formFactor, largest) {
            ActionSheet(
                title = "Desktop",
                actions = listOf(ActionSheetAction("a", "Resume", "Back to the app")),
                onAction = {},
                onDismiss = {},
            )
        }
    }

    /** The font scale that the text with [text] was laid out with. */
    private fun fontScaleOf(text: String): Float =
        composeRule.onNodeWithText(text).textLayout().layoutInput.density.fontScale

    private companion object {
        const val EPSILON = 0.001f
        const val EXPECTED = SYSTEM_SCALE * 1.3f
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}

private const val SYSTEM_SCALE = 1.3f
