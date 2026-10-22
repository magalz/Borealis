package io.github.magalz.borealis.designsystem.components.settings

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The picker of a font shows the name of each font in that font, with its purpose line under it in
 * the font of the app, and a screen reader reads both (ADR 0058, decision 2).
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class ChoiceSheetFontTest {
    @get:Rule val composeRule = createComposeRule()

    private val options =
        listOf(
            ChoiceOption("plex", "IBM Plex Sans", "Default.", FontChoice.Plex),
            ChoiceOption("atkinson", "Atkinson Next", "For low vision.", FontChoice.AtkinsonNext),
            ChoiceOption("luciole", "Luciole", "Tested in a study.", FontChoice.Luciole),
            ChoiceOption("b612", "B612", "For cockpit screens.", FontChoice.B612),
            ChoiceOption(
                "system",
                "System font",
                "Uses the font of the device.",
                FontChoice.System,
            ),
            ChoiceOption("5 %", "5 %"),
        )

    private fun files(style: TextStyle): List<String> =
        style.fontFamily.fontFileNames(
            ApplicationProvider.getApplicationContext<Context>().resources
        )

    /** The style of the text [text], read from the unmerged tree: a row merges its two texts. */
    private fun style(text: String) =
        composeRule.onNodeWithText(text, useUnmergedTree = true).textLayout().layoutInput.style

    @Composable
    private fun Specimen() {
        BorealisTheme(FormFactor.Tv) {
            ChoiceSheet(
                title = "Font",
                options = options,
                selected = "plex",
                onSelect = {},
                onDismiss = {},
            )
        }
    }

    @Test
    fun `each name is drawn in its own font`() {
        composeRule.setContent { Specimen() }

        assertThat(files(style("IBM Plex Sans")))
            .containsExactly(
                "ibm_plex_sans_regular",
                "ibm_plex_sans_medium",
                "ibm_plex_sans_semibold",
            )
        assertThat(files(style("Atkinson Next")))
            .containsExactly(
                "atkinson_next_regular",
                "atkinson_next_medium",
                "atkinson_next_semibold",
            )
        assertThat(files(style("Luciole"))).containsExactly("luciole_regular", "luciole_bold")
        assertThat(files(style("B612"))).containsExactly("b612_regular", "b612_bold")
        assertThat(style("System font").fontFamily).isEqualTo(FontFamily.Default)
    }

    @Test
    fun `a name is drawn at the scale of its font, as the font would draw it in the app`() {
        composeRule.setContent { Specimen() }

        val plex = style("IBM Plex Sans").fontSize.value
        assertThat(style("Atkinson Next").fontSize.value).isWithin(0.001f).of(plex * 1.040f)
        assertThat(style("Luciole").fontSize.value).isWithin(0.001f).of(plex * 0.947f)
        assertThat(style("B612").fontSize.value).isWithin(0.001f).of(plex * 0.923f)
    }

    @Test
    fun `the purpose line is drawn in the font of the app, not in the font it describes`() {
        composeRule.setContent { Specimen() }

        assertThat(files(style("For low vision.")))
            .containsExactly(
                "ibm_plex_sans_regular",
                "ibm_plex_sans_medium",
                "ibm_plex_sans_semibold",
            )
    }

    @Test
    fun `an option with no font is drawn in the font of the app`() {
        composeRule.setContent { Specimen() }

        assertThat(files(style("5 %")))
            .containsExactly(
                "ibm_plex_sans_regular",
                "ibm_plex_sans_medium",
                "ibm_plex_sans_semibold",
            )
    }

    @Test
    fun `a screen reader reads the name and then the purpose line of a font`() {
        composeRule.setContent { Specimen() }

        composeRule
            .onNodeWithText("Atkinson Next")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(
                SemanticsMatcher("reads the name and the line") { node ->
                    node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ==
                        listOf("Atkinson Next", "For low vision.")
                }
            )
    }
}
