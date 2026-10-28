package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.assertPixel
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.tokens.BorealisTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class HostCardTest {
    @get:Rule val composeRule = createComposeRule()
    private var opened = 0
    private var optionsRequested = 0

    @Test
    fun `the card shows the name, status, detail and running app`() {
        composeRule.setContent { Specimen(runningApp = "Desktop running") }

        composeRule.onNodeWithText("Desk PC").assertIsDisplayed()
        composeRule.onNodeWithText("Online").assertIsDisplayed()
        composeRule.onNodeWithText("LuminalShine 26.09.1 · 192.168.1.20").assertIsDisplayed()
        composeRule.onNodeWithText("Desktop running").assertIsDisplayed()
    }

    @Test
    fun `the tags are shown under the detail line as text`() {
        composeRule.setContent { Specimen(tags = listOf("HEVC 10-bit", "HDR", "4:4:4")) }

        composeRule.onNodeWithText("HEVC 10-bit", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("HDR", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("4:4:4", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(CARD).assert(hasText("HDR"))
    }

    @Test
    fun `a card without tags shows none`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithText("HDR", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `the tags sit below the detail line`() {
        composeRule.setContent { Specimen(tags = listOf("HDR")) }

        val detail =
            composeRule.onNodeWithText(
                "LuminalShine 26.09.1 · 192.168.1.20",
                useUnmergedTree = true,
            )
        val tag = composeRule.onNodeWithText("HDR", useUnmergedTree = true)
        assertThat(tag.getUnclippedBoundsInRoot().top)
            .isAtLeast(detail.getUnclippedBoundsInRoot().bottom)
    }

    @Test
    fun `a long detail line wraps instead of cutting the address`() {
        composeRule.setContent { Specimen(FormFactor.Tv, cardWidth = 220.dp) }

        val layout =
            composeRule
                .onNodeWithText("LuminalShine 26.09.1 · 192.168.1.20", useUnmergedTree = true)
                .textLayout()

        assertThat(layout.hasVisualOverflow).isFalse()
        assertThat(layout.lineCount).isEqualTo(2)
    }

    @Test
    fun `the running app line is optional`() {
        composeRule.setContent { Specimen(runningApp = null) }

        composeRule.onNodeWithText("Desktop running").assertDoesNotExist()
    }

    @Test
    fun `a tap on the card opens the host`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithTag(CARD).performClick()

        assertThat(opened).isEqualTo(1)
    }

    @Test
    fun `the card has no button of its own`() {
        composeRule.setContent { Specimen(FormFactor.Tv) }

        composeRule.onAllNodes(hasClickAction(), useUnmergedTree = true).assertCountEquals(1)
    }

    @Test
    fun `OK on a focused card runs its click`() {
        composeRule.setContentInKeyboardMode { Specimen(FormFactor.Tv) }
        composeRule.onNodeWithTag(CARD).requestFocus()

        composeRule.onNodeWithTag(CARD).performKeyInput { pressKey(Key.DirectionCenter) }

        assertThat(opened).isEqualTo(1)
        assertThat(optionsRequested).isEqualTo(0)
    }

    @Test
    fun `a long press requests the options and does not open the host`() {
        composeRule.setContent { Specimen() }

        composeRule.onNodeWithTag(CARD).performTouchInput { longClick() }

        assertThat(optionsRequested).isEqualTo(1)
        assertThat(opened).isEqualTo(0)
    }

    @Test
    fun `X on a gamepad requests the options`() {
        composeRule.setContentInKeyboardMode { Specimen() }

        composeRule.onNodeWithTag(CARD).requestFocus()
        composeRule.onNodeWithTag(CARD).performKeyInput { pressKey(Key.ButtonX) }

        assertThat(optionsRequested).isEqualTo(1)
    }

    @Test
    fun `a focused card shows the outline around the card`() {
        composeRule.setContentInKeyboardMode { Specimen(FormFactor.Tv) }

        composeRule.onNodeWithTag(CARD).requestFocus()

        val width = composeRule.onNodeWithTag(CARD).getUnclippedBoundsInRoot().width.value
        composeRule
            .onNodeWithTag(FRAME)
            .assertPixel(x = 24f + width / 2, y = 24f - 3.5f, BorealisTokens.Color.AccentSoft)
    }

    @Composable
    private fun Specimen(
        formFactor: FormFactor = FormFactor.Phone,
        runningApp: String? = null,
        tags: List<String> = emptyList(),
        cardWidth: Dp? = null,
    ) {
        BorealisTheme(formFactor) {
            Box(Modifier.testTag(FRAME).background(BorealisTokens.Color.Page).padding(24.dp)) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Online,
                    statusText = "Online",
                    // A layout specimen (a long line), not data a host reports.
                    detail = "LuminalShine 26.09.1 · 192.168.1.20",
                    tags = tags,
                    runningApp = runningApp,
                    onClick = { opened++ },
                    onOptions = { optionsRequested++ },
                    modifier =
                        Modifier.testTag(CARD)
                            .then(cardWidth?.let { Modifier.width(it) } ?: Modifier),
                )
            }
        }
    }

    private companion object {
        const val FRAME = "frame"
        const val CARD = "card"
    }
}
