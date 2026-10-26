package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
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
class ContinueCardTest {
    @get:Rule val composeRule = createComposeRule()
    private var resumed = 0
    private var optionsRequested = 0

    @Test
    fun `the card shows the app, where it runs and the resume label`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("Desktop").assertIsDisplayed()
        composeRule.onNodeWithText("Running on Desk PC · started 14 min ago").assertIsDisplayed()
        composeRule.onNodeWithText("Resume", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun `a tap resumes the session`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithTag(CARD).performClick()

        assertThat(resumed).isEqualTo(1)
    }

    @Test
    fun `OK on the focused card resumes the session`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        composeRule.onNodeWithTag(CARD).requestFocus()
        composeRule.onNodeWithTag(CARD).performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(resumed).isEqualTo(1)
    }

    @Test
    fun `X on a gamepad requests the options`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        composeRule.onNodeWithTag(CARD).requestFocus()
        composeRule.onNodeWithTag(CARD).performKeyInput { pressKey(Key.ButtonX) }

        assertThat(optionsRequested).isEqualTo(1)
        assertThat(resumed).isEqualTo(0)
    }

    @Composable
    private fun Specimen() {
        BorealisTheme(FormFactor.Tv) {
            ContinueCard(
                appName = "Desktop",
                detail = "Running on Desk PC · started 14 min ago",
                resumeLabel = "Resume",
                onClick = { resumed++ },
                onOptions = { optionsRequested++ },
                modifier = Modifier.testTag(CARD),
            )
        }
    }

    private companion object {
        const val CARD = "card"
    }
}
