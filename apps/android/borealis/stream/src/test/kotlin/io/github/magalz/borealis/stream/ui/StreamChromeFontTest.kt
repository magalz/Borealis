package io.github.magalz.borealis.stream.ui

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.OverlayMode
import io.github.magalz.borealis.stream.Panel
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
 * The stream chrome draws with the font that the stream started with (ADR 0058, decision 2): its
 * text in the font, and its codes (the performance overlay, the figures of Session info and the raw
 * code of an error) in the monospaced companion of the font.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class StreamChromeFontTest {
    @get:Rule val composeRule = createComposeRule()

    private val b612 = BorealisLook(font = FontChoice.B612)
    private val b612Sans = listOf("b612_regular", "b612_bold")
    private val b612Mono = listOf("b612_mono_regular", "b612_mono_bold")

    private fun files(text: String): List<String> =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontFamily
            .fontFileNames(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun `the performance overlay draws in the monospaced companion of the font`() {
        composeRule.setContent {
            StreamChromeTheme(b612) {
                StreamingLayer(
                    "Desktop",
                    "Gaming PC",
                    aStreaming(overlay = OverlayMode.COMPACT),
                    aPerf(),
                    RecordingIntents(),
                )
            }
        }
        composeRule.waitForIdle()

        assertThat(files("60 fps · host 2.0 ms · net 12 ms · HEVC"))
            .containsExactlyElementsIn(b612Mono)
            .inOrder()
    }

    @Test
    fun `the figures of Session info draw in the monospaced companion and the labels in the font`() {
        composeRule.setContent {
            StreamChromeTheme(b612) {
                StreamingLayer(
                    "Desktop",
                    "Gaming PC",
                    aStreaming(Panel.MENU),
                    aPerf(),
                    RecordingIntents(),
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Session info").performClick()
        composeRule.waitForIdle()

        assertThat(files("c2.qti.hevc.decoder")).containsExactlyElementsIn(b612Mono).inOrder()
        assertThat(files("Decoder")).containsExactlyElementsIn(b612Sans).inOrder()
    }

    @Test
    fun `the raw code of an error draws in the monospaced companion and its text in the font`() {
        composeRule.setContent {
            StreamChromeTheme(b612) {
                ConnectionErrorScreen(
                    "Desktop",
                    "Desk PC",
                    StreamFailure(FailureKind.HOST_BUSY, "Error -1", null),
                    aSession(RecordingActions()),
                )
            }
        }
        composeRule.waitForIdle()

        assertThat(files("Error -1")).containsExactlyElementsIn(b612Mono).inOrder()
        assertThat(files("Desk PC is busy")).containsExactlyElementsIn(b612Sans).inOrder()
    }

    @Test
    fun `with the default look the overlay draws in IBM Plex Mono`() {
        composeRule.setContent {
            StreamChromeTheme(BorealisLook()) {
                StreamingLayer(
                    "Desktop",
                    "Gaming PC",
                    aStreaming(overlay = OverlayMode.COMPACT),
                    aPerf(),
                    RecordingIntents(),
                )
            }
        }
        composeRule.waitForIdle()

        assertThat(files("60 fps · host 2.0 ms · net 12 ms · HEVC"))
            .containsExactly(
                "ibm_plex_mono_regular",
                "ibm_plex_mono_medium",
                "ibm_plex_mono_semibold",
            )
            .inOrder()
    }
}
