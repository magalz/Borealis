package io.github.magalz.borealis.library.pairing

import android.content.Context
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The Pairing screen draws its text in the font of the look, and its codes (the digits of the PIN
 * and the address of the Web UI) in the monospaced companion of that font (ADR 0058, decision 2).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp-xhdpi")
class PairingFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun show(font: FontChoice) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Phone, BorealisLook(font = font)) {
                PairingScreen(
                    state = PairingUiState.Waiting("4817", WEB_UI, "Galaxy S22", false),
                    formFactor = FormFactor.Phone,
                    onGetNewPin = {},
                    onCancel = {},
                    hostName = "Studio",
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun files(text: String): List<String> {
        val style: TextStyle =
            composeRule.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style
        return style.fontFamily.fontFileNames(
            ApplicationProvider.getApplicationContext<Context>().resources
        )
    }

    @Test
    fun `with B612 the PIN and the Web UI address draw in B612 Mono and the text in B612`() {
        show(FontChoice.B612)

        assertThat(files("8")).containsExactly("b612_mono_regular", "b612_mono_bold").inOrder()
        assertThat(files(WEB_UI)).containsExactly("b612_mono_regular", "b612_mono_bold").inOrder()
        assertThat(files("Pair with Studio")).containsExactly("b612_regular", "b612_bold").inOrder()
    }

    @Test
    fun `with Lexend the codes fall back to IBM Plex Mono`() {
        show(FontChoice.Lexend)

        assertThat(files("8"))
            .containsExactly(
                "ibm_plex_mono_regular",
                "ibm_plex_mono_medium",
                "ibm_plex_mono_semibold",
            )
            .inOrder()
        assertThat(files(WEB_UI))
            .containsExactly(
                "ibm_plex_mono_regular",
                "ibm_plex_mono_medium",
                "ibm_plex_mono_semibold",
            )
            .inOrder()
    }

    private companion object {
        const val WEB_UI = "https://192.0.2.10:47990"
    }
}
