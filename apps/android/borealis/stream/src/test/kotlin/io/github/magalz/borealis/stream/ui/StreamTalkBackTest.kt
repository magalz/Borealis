package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.github.magalz.borealis.designsystem.testing.paneTitles
import io.github.magalz.borealis.designsystem.testing.readingOrder
import io.github.magalz.borealis.designsystem.testing.setContentInKeyboardMode
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What a screen reader is told on the stream chrome (WCAG 1.3.1, 4.1.2): a panel, a keyboard or a
 * dialog says what it is when it opens, and each segment of a row of options has a name that tells
 * it from the segments of the other rows.
 */
@RunWith(AndroidJUnit4::class)
class StreamTalkBackTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `the menu says what it is when it opens`() {
        show(FormFactor.Phone) { Menu() }

        assertThat(composeRule.paneTitles()).contains("Desktop · Gaming PC")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun `no two segments of the menu have the same name on a phone`() {
        show(FormFactor.Phone) { Menu() }

        assertThat(segmentNames()).containsNoDuplicates()
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `no two segments of the menu have the same name on a handheld`() {
        show(FormFactor.Handheld) { Menu() }

        assertThat(segmentNames()).containsNoDuplicates()
    }

    @Test
    @Config(qualifiers = TV)
    fun `a segment is named with the row that it belongs to`() {
        show(FormFactor.Tv) { Menu() }

        assertThat(segmentNames()).contains("Off, Overlay")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the keyboard says what it is when it opens`() {
        show(FormFactor.Tv) { StreamKeyboard(false, RecordingIntents()) }

        assertThat(composeRule.paneTitles()).contains("Keyboard")
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `the conflict dialog says what it is when it opens`() {
        show(FormFactor.Phone) {
            ConflictDialog(StreamPhase.Conflict("Desktop", "Hades II", false), RecordingIntents())
        }

        assertThat(composeRule.paneTitles()).contains("An app is already running")
    }

    @Test
    @Config(qualifiers = TV)
    fun `the menu is read from its header down, row by row, whichever row has the focus`() {
        composeRule.setContentInKeyboardMode {
            BorealisTheme(FormFactor.Tv) { Box(Modifier.fillMaxSize()) { Menu() } }
        }

        val order = composeRule.readingOrder()
        val rows =
            listOf(
                "Desktop · Gaming PC",
                "Back to stream",
                "Keyboard",
                "Send keys",
                "Overlay",
                "Refresh the picture",
                "Session info",
                "Display for next launch",
                "Disconnect",
            )
        val places = rows.map { row -> order.indexOfFirst { it.startsWith(row) } }
        assertWithMessage(order.joinToString(" // "))
            .that(places.all { it >= 0 } && places.zipWithNext().all { (a, b) -> a < b })
            .isTrue()
    }

    /** What a screen reader says of each radio button of the screen: its description, else text. */
    private fun segmentNames(): List<String> =
        composeRule
            .onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .fetchSemanticsNodes()
            .map { node ->
                node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
                    ?: node.config[SemanticsProperties.Text].joinToString(", ") { it.text }
            }

    private fun show(formFactor: FormFactor, screen: @Composable () -> Unit) {
        composeRule.setContent {
            BorealisTheme(formFactor) { Box(Modifier.fillMaxSize()) { screen() } }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun Menu() {
        StreamingLayer(
            "Desktop",
            "Gaming PC",
            aStreaming(Panel.MENU, OverlayMode.DETAILED),
            aPerf(),
            RecordingIntents(),
        )
    }
}
