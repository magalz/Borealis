package io.github.magalz.borealis.settings.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.fontFileNames
import io.github.magalz.borealis.designsystem.testing.textLayout
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FontChoice
import io.github.magalz.borealis.designsystem.theme.LocalBorealisLook
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private val PLEX_SANS =
    listOf("ibm_plex_sans_regular", "ibm_plex_sans_medium", "ibm_plex_sans_semibold")

/** The name of each font in the picker, and the files of the font it is drawn in. */
private val FONT_NAMES =
    listOf(
        "IBM Plex Sans" to PLEX_SANS,
        "Atkinson Hyperlegible Next" to
            listOf("atkinson_next_regular", "atkinson_next_medium", "atkinson_next_semibold"),
        "Luciole" to listOf("luciole_regular", "luciole_bold"),
        "Lexend" to listOf("lexend_regular", "lexend_medium", "lexend_semibold"),
        "Andika" to listOf("andika_regular", "andika_semibold"),
        "B612" to listOf("b612_regular", "b612_bold"),
    )

private const val SOURCE_CODE = "Source code"
private const val SOURCE_CODE_URL = "https://github.com/magalz/Borealis"

/**
 * What the font of the look does in Settings on a TV (ADR 0058, decision 2): the text of the screen
 * is drawn in it, the picker of the Font row shows each name in its own font, and the address of an
 * About link is a code.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsFontTest : SettingsScreenTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    private val resources = ApplicationProvider.getApplicationContext<Context>().resources

    /** The page with the look that the Library gives the screens. */
    private fun withLook(look: BorealisLook): @Composable (@Composable () -> Unit) -> Unit = {
        CompositionLocalProvider(LocalBorealisLook provides look) { OnPage(it) }
    }

    private fun style(text: String, inDialog: Boolean = false): TextStyle {
        val matcher = if (inDialog) hasText(text) and hasAnyAncestor(isDialog()) else hasText(text)
        return composeRule.onNode(matcher, useUnmergedTree = true).textLayout().layoutInput.style
    }

    private fun files(text: String, inDialog: Boolean = false): List<String> =
        style(text, inDialog).fontFamily.fontFileNames(resources)

    @Test
    fun `the text of the screen is drawn in the font of the look`() {
        show(
            keyboard = true,
            initialGroup = SettingsGroupId.APPEARANCE,
            backdrop = withLook(BorealisLook(font = FontChoice.B612)),
        )

        assertThat(files("Font")).containsExactly("b612_regular", "b612_bold").inOrder()
    }

    @Test
    fun `the text of the screen is IBM Plex Sans in the default look`() {
        show(keyboard = true, initialGroup = SettingsGroupId.APPEARANCE)

        assertThat(files("Font")).containsExactlyElementsIn(PLEX_SANS).inOrder()
    }

    @Test
    fun `the picker shows the name of each font in that font`() {
        show(keyboard = true, initialGroup = SettingsGroupId.APPEARANCE)
        composeRule.onNodeWithText("Font").performScrollTo().performClick()
        composeRule.waitForIdle()

        for ((name, expected) in FONT_NAMES) {
            assertThat(files(name, inDialog = true)).containsExactlyElementsIn(expected).inOrder()
        }
        assertThat(style("System font", inDialog = true).fontFamily).isEqualTo(FontFamily.Default)
    }

    @Test
    fun `the purpose lines of the picker are drawn in the font of the app`() {
        show(
            keyboard = true,
            initialGroup = SettingsGroupId.APPEARANCE,
            backdrop = withLook(BorealisLook(font = FontChoice.Lexend)),
        )
        composeRule.onNodeWithText("Font").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertThat(files("Uses your device's font.", inDialog = true))
            .containsExactly("lexend_regular", "lexend_medium", "lexend_semibold")
            .inOrder()
    }

    @Test
    fun `the licence text is a code in the monospaced companion of the font`() {
        licenses = "Licence text of the test"
        show(
            keyboard = true,
            initialGroup = SettingsGroupId.ABOUT,
            backdrop = withLook(BorealisLook(font = FontChoice.B612)),
        )
        composeRule.onNodeWithText("Open-source licences").performClick()
        composeRule.waitForIdle()

        assertThat(files("Licence text of the test", inDialog = true))
            .containsExactly("b612_mono_regular", "b612_mono_bold")
            .inOrder()
    }

    @Test
    fun `the licence text is IBM Plex Mono in the default look, not the monospace of the system`() {
        licenses = "Licence text of the test"
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)
        composeRule.onNodeWithText("Open-source licences").performClick()
        composeRule.waitForIdle()

        assertThat(files("Licence text of the test", inDialog = true))
            .containsExactly(
                "ibm_plex_mono_regular",
                "ibm_plex_mono_medium",
                "ibm_plex_mono_semibold",
            )
            .inOrder()
    }

    @Test
    fun `the address of an About link is a code in the monospaced companion of the font`() {
        show(
            keyboard = true,
            initialGroup = SettingsGroupId.ABOUT,
            backdrop = withLook(BorealisLook(font = FontChoice.B612)),
        )
        val row = composeRule.onNode(hasText(SOURCE_CODE) and !hasAnyAncestor(isDialog()))
        row.requestFocus()
        row.performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        assertThat(files(SOURCE_CODE_URL, inDialog = true))
            .containsExactly("b612_mono_regular", "b612_mono_bold")
            .inOrder()
    }
}
