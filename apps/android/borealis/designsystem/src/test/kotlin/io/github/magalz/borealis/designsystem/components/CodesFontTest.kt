package io.github.magalz.borealis.designsystem.components

import android.content.Context
import androidx.compose.runtime.Composable
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

/**
 * The codes of the design system (a chip, the number of a step, the initials of a cover and the
 * digits of the PIN) draw in the monospaced companion of the font of the look, or in IBM Plex Mono
 * when the font has none (ADR 0058, decision 2).
 */
@RunWith(AndroidJUnit4::class)
class CodesFontTest {
    @get:Rule val composeRule = createComposeRule()

    private fun names(style: TextStyle): List<String> =
        style.fontFamily.fontFileNames(
            ApplicationProvider.getApplicationContext<Context>().resources
        )

    private fun style(text: String): TextStyle =
        composeRule.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style

    private fun show(font: FontChoice, content: @Composable () -> Unit) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv, BorealisLook(font = font)) { content() }
        }
        composeRule.waitForIdle()
    }

    private val b612Mono = listOf("b612_mono_regular", "b612_mono_bold")
    private val plexMono =
        listOf("ibm_plex_mono_regular", "ibm_plex_mono_medium", "ibm_plex_mono_semibold")

    @Test
    fun `a chip draws in B612 Mono with B612`() {
        show(FontChoice.B612) { Chip("Alt+Tab") }

        assertThat(names(style("Alt+Tab"))).isEqualTo(b612Mono)
    }

    @Test
    fun `a chip draws in IBM Plex Mono with Luciole, which has no monospaced font`() {
        show(FontChoice.Luciole) { Chip("Alt+Tab") }

        assertThat(names(style("Alt+Tab"))).isEqualTo(plexMono)
    }

    @Test
    fun `a chip draws in IBM Plex Mono with the default font`() {
        show(FontChoice.Plex) { Chip("Alt+Tab") }

        assertThat(names(style("Alt+Tab"))).isEqualTo(plexMono)
    }

    @Test
    fun `the number of a step draws in B612 Mono with B612`() {
        show(FontChoice.B612) { StepList(listOf("Find the host", "Pair"), current = 0) }

        assertThat(names(style("1"))).isEqualTo(b612Mono)
        assertThat(names(style("2"))).isEqualTo(b612Mono)
    }

    @Test
    fun `the digits of the PIN draw in B612 Mono with B612`() {
        show(FontChoice.B612) { PinDisplay("4817") }

        assertThat(names(style("4"))).isEqualTo(b612Mono)
    }

    @Test
    fun `the codes of the system font draw in IBM Plex Mono`() {
        show(FontChoice.System) { Chip("Win+D") }

        assertThat(names(style("Win+D"))).isEqualTo(plexMono)
    }
}
