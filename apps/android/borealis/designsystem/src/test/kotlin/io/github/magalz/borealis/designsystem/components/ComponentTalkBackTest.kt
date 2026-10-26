package io.github.magalz.borealis.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.headings
import io.github.magalz.borealis.designsystem.testing.paneTitles
import io.github.magalz.borealis.designsystem.testing.readingOrder
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told about the components (WCAG 1.3.1, 2.4.6 and 4.1.2): a sheet says
 * what it is when it opens, a title and a section label are headings, and a card is one sentence.
 */
@RunWith(AndroidJUnit4::class)
class ComponentTalkBackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE)
    fun `a bottom sheet says what it is when it opens, and its title is a heading`() {
        composeRule.setContent { Sheet(FormFactor.Phone) }
        composeRule.waitForIdle()

        assertThat(composeRule.paneTitles()).contains("Desktop")
        assertThat(composeRule.headings()).contains("Desktop")
    }

    @Test
    @Config(qualifiers = TV)
    fun `a sheet that is a dialog says what it is when it opens, and its title is a heading`() {
        composeRule.setContent { Sheet(FormFactor.Tv) }
        composeRule.waitForIdle()

        assertThat(composeRule.paneTitles()).contains("Desktop")
        assertThat(composeRule.headings()).contains("Desktop")
    }

    @Test
    @Config(qualifiers = TV)
    fun `a dialog says what it is when it opens, and its title is a heading`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                BorealisDialog(
                    title = "Forget this host?",
                    onDismissRequest = {},
                    confirm = DialogAction("Forget") {},
                ) {}
            }
        }
        composeRule.waitForIdle()

        assertThat(composeRule.paneTitles()).contains("Forget this host?")
        assertThat(composeRule.headings()).contains("Forget this host?")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the title of an empty state is a heading`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) { EmptyState("Looking for hosts", "The PC must be on.") }
        }

        assertThat(composeRule.headings()).containsExactly("Looking for hosts")
    }

    @Test
    @Config(qualifiers = TV)
    fun `a section label over the background is a heading`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                Column {
                    BackdropKicker("Continue")
                    BackdropText("Not a label")
                }
            }
        }

        assertThat(composeRule.headings()).containsExactly("CONTINUE")
    }

    @Test
    @Config(qualifiers = TV)
    fun `a host card says its name, status, address, capabilities and what it runs`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                HostCard(
                    name = "Desk PC",
                    status = DotStatus.Online,
                    statusText = "Online",
                    onClick = {},
                    detail = "192.168.1.20",
                    tags = listOf("HEVC", "HDR"),
                    runningApp = "Desktop",
                    runningLabel = "Running Desktop",
                )
            }
        }

        val said =
            composeRule
                .onNodeWithText("Desk PC")
                .fetchSemanticsNode()
                .config
                .getOrNull(SemanticsProperties.ContentDescription)
        assertThat(said)
            .containsExactly("Desk PC, Online, 192.168.1.20, HEVC, HDR, Running Desktop")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the rows of a menu are read from the top, whichever row has the focus`() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) {
                OnPage {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        MenuRow("Back to stream", {})
                        MenuRow("Keyboard", {})
                        MenuRow("Disconnect", {})
                    }
                }
            }
        }
        composeRule.onNodeWithText("Back to stream").requestFocus()

        assertThat(composeRule.readingOrder())
            .containsAtLeast("Back to stream", "Keyboard", "Disconnect")
            .inOrder()
    }

    @Composable
    private fun Sheet(formFactor: FormFactor) {
        BorealisTheme(formFactor) {
            ActionSheet(
                title = "Desktop",
                actions = listOf(ActionSheetAction("a", "Resume", "Back to the app")),
                onAction = {},
                onDismiss = {},
            )
        }
    }

    private companion object {
        const val PHONE = "w360dp-h640dp-xhdpi"
        const val TV = "w960dp-h540dp-land-television-xhdpi"
    }
}
