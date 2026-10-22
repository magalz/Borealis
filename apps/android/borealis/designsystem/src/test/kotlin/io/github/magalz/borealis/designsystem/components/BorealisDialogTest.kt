package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.DialogWidth
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class BorealisDialogTest {
    @get:Rule val composeRule = createComposeRule()
    private var confirmed = 0
    private var dismissed = 0
    private var dismissRequested = 0
    private var extra = 0

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the compact width is 420`() {
        assertWidth(FormFactor.Tv, DialogWidth.Compact, 420.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the standard width is 560`() {
        assertWidth(FormFactor.Tv, DialogWidth.Standard, 560.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `on a TV the wide width is 720`() {
        assertWidth(FormFactor.Tv, DialogWidth.Wide, 720.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone a compact dialog is the window less 16dp on each side`() {
        assertWidth(FormFactor.Phone, DialogWidth.Compact, 328.dp)
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone a wide dialog is the window less 16dp on each side`() {
        assertWidth(FormFactor.Phone, DialogWidth.Wide, 328.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the title and the content show`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule.onNodeWithText("Forget this host?").assertIsDisplayed()
        composeRule.onNodeWithText("Its pairing is removed.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the padding is 24dp on every side`() {
        composeRule.setContent { Specimen(FormFactor.Tv, dismiss = DialogAction("Cancel") {}) }
        composeRule.waitForIdle()

        val frame = composeRule.onNodeWithTag(DIALOG).getUnclippedBoundsInRoot()
        val title = composeRule.onNodeWithText("Forget this host?").getUnclippedBoundsInRoot()
        val confirm = composeRule.onNodeWithText("Forget").getUnclippedBoundsInRoot()
        assertThat(title.left - frame.left).isEqualTo(24.dp)
        assertThat(title.top - frame.top).isEqualTo(24.dp)
        assertThat(frame.right - confirm.right).isEqualTo(24.dp)
        assertThat(frame.bottom - confirm.bottom).isEqualTo(24.dp)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the confirm action is on the right of the dismiss action`() {
        composeRule.setContent { Specimen(FormFactor.Tv, dismiss = DialogAction("Cancel") {}) }
        composeRule.waitForIdle()

        val cancel = composeRule.onNodeWithText("Cancel").getUnclippedBoundsInRoot()
        val confirm = composeRule.onNodeWithText("Forget").getUnclippedBoundsInRoot()
        assertThat(confirm.left).isGreaterThan(cancel.right)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the extra actions sit at the start, away from the dismiss action`() {
        composeRule.setContent {
            Specimen(
                FormFactor.Tv,
                dismiss = DialogAction("Cancel") {},
                extraActions = listOf(DialogAction("Copy") { extra++ }),
            )
        }
        composeRule.waitForIdle()

        val copy = composeRule.onNodeWithText("Copy").getUnclippedBoundsInRoot()
        val cancel = composeRule.onNodeWithText("Cancel").getUnclippedBoundsInRoot()
        assertThat(copy.right).isAtMost(cancel.left)
        composeRule.onNodeWithText("Copy").performClick()
        assertThat(extra).isEqualTo(1)
    }

    @Test
    @Config(qualifiers = TV)
    fun `the confirm action has focus when the dialog opens`() {
        composeRule.setContent { Specimen(FormFactor.Tv, dismiss = DialogAction("Cancel") {}) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Forget").assertIsFocused()
        composeRule.onNodeWithText("Cancel").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the dismiss action has focus when the caller says it is the safe one`() {
        composeRule.setContent {
            Specimen(FormFactor.Tv, dismiss = DialogAction("Cancel") {}, focusDismiss = true)
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertIsFocused()
        composeRule.onNodeWithText("Forget").assertIsNotFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the confirm action keeps focus when there is no dismiss action to focus`() {
        composeRule.setContent { Specimen(FormFactor.Tv, focusDismiss = true) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Forget").assertIsFocused()
    }

    @Test
    @Config(qualifiers = TV)
    fun `the actions run their callbacks and leave the dialog open for the screen to close`() {
        composeRule.setContent {
            Specimen(FormFactor.Tv, dismiss = DialogAction("Cancel") { dismissed++ })
        }

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Forget").performClick()

        assertThat(dismissed).isEqualTo(1)
        assertThat(confirmed).isEqualTo(1)
        assertThat(dismissRequested).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `Back asks to dismiss`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(dismissRequested).isEqualTo(1)
        assertThat(confirmed).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a tap on the scrim asks to dismiss`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        // The dialog window is the second root; its corner is the scrim, outside the dialog.
        composeRule.onAllNodes(isRoot())[1].performTouchInput { click(Offset(10f, 10f)) }
        composeRule.waitForIdle()

        assertThat(dismissRequested).isEqualTo(1)
        assertThat(confirmed).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = TV)
    fun `a tap on the dialog itself does not dismiss`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Its pairing is removed.").performClick()

        assertThat(dismissRequested).isEqualTo(0)
    }

    @Test
    @Config(qualifiers = SHORT_PHONE)
    fun `long content scrolls and the title and the actions stay in view`() {
        composeRule.setContent {
            Specimen(FormFactor.Phone, content = { Box(Modifier.height(900.dp)) })
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Forget").assertIsDisplayed()
        composeRule.onNodeWithText("Forget this host?").assertIsDisplayed()
    }

    private fun assertWidth(formFactor: FormFactor, width: DialogWidth, expected: Dp) {
        composeRule.setContent { Specimen(formFactor, width) }
        composeRule.waitForIdle()

        val frame = composeRule.onNodeWithTag(DIALOG).getUnclippedBoundsInRoot()
        assertThat(frame.right - frame.left).isEqualTo(expected)
    }

    @Composable
    private fun Specimen(
        formFactor: FormFactor,
        width: DialogWidth = DialogWidth.Compact,
        dismiss: DialogAction? = null,
        extraActions: List<DialogAction> = emptyList(),
        focusDismiss: Boolean = false,
        content: @Composable ColumnScope.() -> Unit = { Text("Its pairing is removed.") },
    ) {
        BorealisTheme(formFactor) {
            BorealisDialog(
                title = "Forget this host?",
                onDismissRequest = { dismissRequested++ },
                confirm = DialogAction("Forget") { confirmed++ },
                modifier = Modifier.testTag(DIALOG),
                width = width,
                dismiss = dismiss,
                extraActions = extraActions,
                focusDismiss = focusDismiss,
                content = content,
            )
        }
    }

    private companion object {
        const val DIALOG = "dialog"
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val SHORT_PHONE = "w360dp-h360dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}
