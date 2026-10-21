package io.github.magalz.borealis.settings.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.testing.OnPage
import io.github.magalz.borealis.designsystem.testing.headings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The sections of the settings list on a phone: each group opens and closes its rows (UC-009). */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class SettingsPhoneSectionsTest : PhoneSettingsTestBase() {
    @Test
    fun `the phone list has exactly these sections, in this order`() {
        show()

        val sections =
            listOf(
                "STREAM",
                "VIDEO",
                "INPUT",
                "GAMEPAD",
                "ON-SCREEN CONTROLS",
                "AUDIO",
                "ADVANCED",
                "APPEARANCE",
                "ACCESSIBILITY",
                "ABOUT",
            )
        // PHONE_SECTIONS is derived from the groups, so this is what pins the list itself.
        assertThat(PHONE_SECTIONS).containsExactlyElementsIn(sections).inOrder()
        assertThat(composeRule.headings())
            .containsExactly("Settings", *sections.toTypedArray())
            .inOrder()
    }

    @Test
    fun `every section starts collapsed`() {
        show()

        PHONE_SECTIONS.forEach { header(it).assert(state("Collapsed")) }
        listOf(
                "Resolution",
                "Codec",
                "Touch",
                "Vibration strength",
                "Show on-screen controls",
                "Channels",
                "Optimise game settings",
                "Background",
                "Version",
            )
            .forEach { composeRule.onNodeWithText(it).assertDoesNotExist() }
        row("Privacy and diagnostics").assertIsDisplayed()
    }

    @Test
    fun `a section header is a heading and a button`() {
        show()

        PHONE_SECTIONS.forEach {
            header(it)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
        }
    }

    @Test
    fun `a tap opens a section and the next tap closes it`() {
        show()

        header("STREAM").performClick()
        composeRule.waitForIdle()

        header("STREAM").assert(state("Expanded"))
        row("Resolution").assertIsDisplayed()
        row("Frame pacing").assertIsDisplayed()

        header("STREAM").performClick()
        composeRule.waitForIdle()

        header("STREAM").assert(state("Collapsed"))
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
        composeRule.onNodeWithText("Frame pacing").assertDoesNotExist()
    }

    @Test
    fun `two sections can be open at once`() {
        show()

        header("STREAM").performClick()
        composeRule.waitForIdle()
        header("AUDIO").performClick()
        composeRule.waitForIdle()

        header("STREAM").assert(state("Expanded"))
        header("AUDIO").assert(state("Expanded"))
        row("Resolution").assertIsDisplayed()
        row("Channels").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `opening a section does not scroll the list`() {
        show()
        listOf("STREAM", "VIDEO", "INPUT", "GAMEPAD").forEach {
            header(it).performClick()
            composeRule.waitForIdle()
        }
        header("AUDIO")
        val before = top("AUDIO")
        composeRule.onNodeWithText("Channels").assertDoesNotExist()

        header("AUDIO").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Channels").assertExists()
        assertThat(top("AUDIO")).isEqualTo(before)
    }

    @Test
    fun `the open sections survive a recreation`() {
        val restoration = StateRestorationTester(composeRule)
        val viewModel = viewModel()
        restoration.setContent { OnPage { Route(viewModel) } }
        composeRule.waitForIdle()
        header("STREAM").performClick()
        composeRule.waitForIdle()
        header("ABOUT").performClick()
        composeRule.waitForIdle()

        restoration.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        header("STREAM").assert(state("Expanded"))
        header("ABOUT").assert(state("Expanded"))
        header("VIDEO").assert(state("Collapsed"))
        row("Resolution").assertIsDisplayed()
        row("Source code").assertIsDisplayed()
        composeRule.onNodeWithText("Codec").assertDoesNotExist()
    }

    @Test
    fun `the open sections survive leaving the screen and coming back`() {
        val viewModel = viewModel()
        var onScreen by mutableStateOf(true)
        composeRule.setContent {
            val holder = rememberSaveableStateHolder()
            if (onScreen) holder.SaveableStateProvider("settings") { OnPage { Route(viewModel) } }
        }
        composeRule.waitForIdle()
        header("AUDIO").performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle { onScreen = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AUDIO").assertDoesNotExist()
        composeRule.runOnIdle { onScreen = true }
        composeRule.waitForIdle()

        header("AUDIO").assert(state("Expanded"))
        row("Channels").assertIsDisplayed()
        composeRule.onNodeWithText("Resolution").assertDoesNotExist()
    }

    @Test
    fun `About is a section that holds its rows`() {
        show()

        composeRule.onNodeWithText("Version").assertDoesNotExist()
        composeRule.onNodeWithText("Source code").assertDoesNotExist()
        header("ABOUT").assert(state("Collapsed")).performClick()
        composeRule.waitForIdle()

        header("ABOUT").assert(state("Expanded"))
        listOf("Version", "Source code", "Open-source licences", "Privacy policy").forEach {
            row(it).assertIsDisplayed()
        }
    }

    @Test
    fun `Privacy and diagnostics is a link row between the groups and About, not a section`() {
        show()

        val privacy = top("Privacy and diagnostics")
        assertThat(top("APPEARANCE")).isLessThan(privacy)
        assertThat(privacy).isLessThan(top("ABOUT"))
        row("Privacy and diagnostics")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .performClick()
        assertThat(privacyOpens).isEqualTo(1)
    }

    @Composable
    private fun Route(viewModel: SettingsViewModel) {
        SettingsRoute(
            onBack = {},
            onOpenPrivacy = { privacyOpens++ },
            privacyPane = {},
            about = testAbout(),
            viewModel = viewModel,
        )
    }
}
