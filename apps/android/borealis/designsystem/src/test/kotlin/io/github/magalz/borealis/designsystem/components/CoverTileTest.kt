package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import io.github.magalz.borealis.designsystem.pixelAt
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class CoverTileTest {
    @get:Rule val composeRule = createComposeRule()
    private var clicks = 0
    private var optionsRequested = 0
    private val runningLabel = mutableStateOf<String?>(null)
    private val hidden = mutableStateOf(false)

    @Test
    fun `initials come from the first letters of the first two words`() {
        assertThat(initialsOf("Desktop")).isEqualTo("D")
        assertThat(initialsOf("Steam Big Picture")).isEqualTo("SB")
        assertThat(initialsOf("  elden   ring ")).isEqualTo("ER")
        assertThat(initialsOf("")).isEqualTo("")
    }

    @Test
    fun `the running badge shows its label`() {
        runningLabel.value = "Running"
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Running").assertIsDisplayed()
    }

    @Test
    fun `an app that does not run has no badge`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Running").assertDoesNotExist()
    }

    @Test
    fun `the tile is 3 by 4`() {
        composeRule.setContent { Specimen() }

        val bounds = tile().getUnclippedBoundsInRoot()

        assertThat(bounds.width.value / bounds.height.value).isWithin(0.01f).of(3f / 4f)
    }

    @Test
    fun `a hidden app is dimmed`() {
        composeRule.setContent { Specimen() }
        val normal = composeRule.onNodeWithTag(FRAME).pixelAt(x = 84f, y = 104f)

        hidden.value = true
        composeRule.waitForIdle()
        val dimmed = composeRule.onNodeWithTag(FRAME).pixelAt(x = 84f, y = 104f)

        val page = BorealisTokens.Color.Page
        assertThat(dimmed.red - page.red).isLessThan(normal.red - page.red)
        assertThat(dimmed.red).isGreaterThan(page.red - 0.001f)
    }

    @Test
    fun `a tap starts the app`() {
        composeRule.setContent { Specimen() }

        tile().performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `a long press requests the options and does not start the app`() {
        composeRule.setContent { Specimen() }

        tile().performTouchInput { longClick() }

        assertThat(optionsRequested).isEqualTo(1)
        assertThat(clicks).isEqualTo(0)
    }

    @Test
    fun `X on a gamepad requests the options`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        tile().requestFocus()
        tile().performKeyInput { pressKey(Key.ButtonX) }

        assertThat(optionsRequested).isEqualTo(1)
    }

    /** The cover is the focusable tile, and it is announced by the app name. */
    private fun tile(): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription("Desktop")

    @Composable
    private fun Specimen() {
        BorealisTheme(FormFactor.Tv) {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(24.dp)) {
                CoverTile(
                    name = "Desktop",
                    runningLabel = runningLabel.value,
                    hidden = hidden.value,
                    onClick = { clicks++ },
                    onOptions = { optionsRequested++ },
                    modifier = Modifier.width(120.dp),
                )
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
    }
}
