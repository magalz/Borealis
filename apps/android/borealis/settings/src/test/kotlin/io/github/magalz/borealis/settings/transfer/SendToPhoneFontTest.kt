package io.github.magalz.borealis.settings.transfer

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import io.github.magalz.borealis.settings.transfer.FakeTransferController.Companion.FILE_NAME
import io.github.magalz.borealis.settings.transfer.FakeTransferController.Companion.LINK
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Send to phone draws the link and the `adb pull` command in the monospaced companion of the font
 * of the look, and the rest of its text in the font (ADR 0058, decision 2).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SendToPhoneFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun open(font: FontChoice, state: TransferState) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(font = font)) {
                SendToPhoneDialog(
                    File("/sdcard/Download/$FILE_NAME"),
                    ADB_COMMAND,
                    {},
                    FakeTransferController(state),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun files(text: String): List<String> =
        composeRule
            .onNodeWithText(text, useUnmergedTree = true)
            .textLayout()
            .layoutInput
            .style
            .fontFamily
            .fontFileNames(ApplicationProvider.getApplicationContext<Context>().resources)

    @Test
    fun `with Atkinson the link draws in Atkinson Hyperlegible Mono`() {
        open(FontChoice.AtkinsonNext, FakeTransferController.waiting())

        assertThat(files(LINK))
            .containsExactly(
                "atkinson_mono_regular",
                "atkinson_mono_medium",
                "atkinson_mono_semibold",
            )
            .inOrder()
    }

    @Test
    fun `with B612 the adb command draws in B612 Mono`() {
        open(FontChoice.B612, TransferState.NoNetwork)

        assertThat(files(ADB_COMMAND))
            .containsExactly("b612_mono_regular", "b612_mono_bold")
            .inOrder()
    }

    @Test
    fun `with Lexend the link falls back to IBM Plex Mono`() {
        open(FontChoice.Lexend, FakeTransferController.waiting())

        assertThat(files(LINK))
            .containsExactly(
                "ibm_plex_mono_regular",
                "ibm_plex_mono_medium",
                "ibm_plex_mono_semibold",
            )
            .inOrder()
    }

    private companion object {
        const val ADB_COMMAND = "adb pull /sdcard/Download/$FILE_NAME"
    }
}
