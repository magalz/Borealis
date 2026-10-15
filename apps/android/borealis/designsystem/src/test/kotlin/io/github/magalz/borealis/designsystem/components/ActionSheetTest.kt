package io.github.magalz.borealis.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class ActionSheetTest {
    @get:Rule val composeRule = createComposeRule()
    private val chosen = mutableListOf<ActionSheetAction<String>>()
    private var dismissed = 0

    private val resume = ActionSheetAction("resume", "Resume", "Back to the running desktop")
    private val quit =
        ActionSheetAction("quit", "Quit", "Closes the app on the host", ActionStyle.Danger)

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the title, labels and consequences show in a dialog`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule.onNodeWithText("Desktop").assertIsDisplayed()
        composeRule.onNodeWithText("Resume").assertIsDisplayed()
        composeRule.onNodeWithText("Back to the running desktop").assertIsDisplayed()
        composeRule.onNodeWithText("Closes the app on the host").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the first action has focus`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Resume").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the sheet is a dialog centred in the window`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        val bounds = composeRule.onNodeWithText("Desktop").getUnclippedBoundsInRoot()

        // A dialog is narrower than the 960dp window and does not touch its sides.
        assertThat(bounds.left).isGreaterThan(100.dp)
        assertThat(bounds.right).isLessThan(860.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone the sheet is a bottom sheet`() {
        composeRule.setContent { Specimen(FormFactor.Phone) }
        composeRule.waitForIdle()

        val title = composeRule.onNodeWithText("Desktop")
        title.assertIsDisplayed()
        // The sheet fills the width and rests on the bottom edge: the last action is near it.
        val last =
            composeRule.onNodeWithText("Closes the app on the host").getUnclippedBoundsInRoot()
        assertThat(last.bottom).isGreaterThan(520.dp)
        assertThat(title.getUnclippedBoundsInRoot().left).isLessThan(40.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `choosing an action reports it`() {
        composeRule.setContent { Specimen(FormFactor.Phone) }

        composeRule.onNodeWithText("Quit").performClick()

        assertThat(chosen).containsExactly(quit)
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back dismisses the dialog`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
        assertThat(chosen).isEmpty()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `Back dismisses the bottom sheet`() {
        composeRule.setContent { Specimen(FormFactor.Phone) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissed).isEqualTo(1)
    }

    @Composable
    private fun Specimen(formFactor: FormFactor) {
        BorealisTheme(formFactor) {
            ActionSheet(
                title = "Desktop",
                actions = listOf(resume, quit),
                onAction = { chosen += it },
                onDismiss = { dismissed++ },
            )
        }
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}
