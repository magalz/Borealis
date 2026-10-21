package io.github.magalz.borealis.stream.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.assertNoClippedText
import io.github.magalz.borealis.designsystem.testing.dumpFrame
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.TextSize
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.aSession
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The stream chrome at the largest text: the Text size setting at 130 percent over a system font
 * scale of 1.3, which is 1.69 of the text that the screens are laid out for (ADR 0057, decision
 * 11). Every text must fit where it is, or the panel scrolls: none runs past its width, is cut off
 * at the bottom or ends in an ellipsis. The chrome wears the look that the stream started with.
 */
@RunWith(AndroidJUnit4::class)
class TextSizeFitTest {
    @get:Rule val composeRule = createComposeRule()

    private val largest = BorealisLook(textSize = TextSize.Largest)

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE, fontScale = SYSTEM_SCALE)
    fun `the in-game menu on a phone fits`() = assertFits("menu_phone", FormFactor.Phone) { Menu() }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `the in-game menu on a TV fits`() = assertFits("menu_tv", FormFactor.Tv) { Menu() }

    @Test
    @Config(qualifiers = HANDHELD, fontScale = SYSTEM_SCALE)
    fun `the in-game menu on a handheld fits`() =
        assertFits("menu_handheld", FormFactor.Handheld) { Menu() }

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `the keyboard on a phone fits`() =
        assertFits("keyboard_phone", FormFactor.Phone) { StreamKeyboard(true, RecordingIntents()) }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `the keyboard on a TV fits`() =
        assertFits("keyboard_tv", FormFactor.Tv) { StreamKeyboard(false, RecordingIntents()) }

    @Test
    @Config(qualifiers = PHONE, fontScale = SYSTEM_SCALE)
    fun `the connection error screen on a phone fits`() =
        assertFits("error_phone", FormFactor.Phone) { ErrorScreen(FailureKind.CERTIFICATE_CHANGED) }

    @Test
    @Config(qualifiers = TV, fontScale = SYSTEM_SCALE)
    fun `the connection error screen on a TV fits`() =
        assertFits("error_tv", FormFactor.Tv) { ErrorScreen(FailureKind.HOST_OFFLINE) }

    private fun assertFits(name: String, formFactor: FormFactor, screen: @Composable () -> Unit) {
        composeRule.setContent {
            BorealisTheme(formFactor, largest) { Box(Modifier.fillMaxSize()) { screen() } }
        }
        composeRule.waitForIdle()

        composeRule.dumpFrame("fit_$name")
        composeRule.assertNoClippedText()
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

    @Composable
    private fun ErrorScreen(kind: FailureKind) {
        ConnectionErrorScreen(
            "Desktop",
            "Desk PC",
            StreamFailure(kind, "Error -1", portHint = null),
            aSession(RecordingActions()),
        )
    }

    private companion object {
        const val SYSTEM_SCALE = 1.3f
    }
}
