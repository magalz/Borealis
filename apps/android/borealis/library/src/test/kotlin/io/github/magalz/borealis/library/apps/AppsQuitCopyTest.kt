package io.github.magalz.borealis.library.apps

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * What Quit says on the Apps screen: for Desktop it ends the stream and loses nothing; for a game
 * it closes the game and its unsaved progress is lost.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class AppsQuitCopyTest {
    @get:Rule val composeRule = createComposeRule()
    private val chosen = mutableListOf<AppAction>()

    private val desktop = item(1, "Desktop", running = true)
    private val runningSteam = item(2, "Steam Big Picture", running = true)
    private val hades = item(3, "Hades II")

    private fun item(id: Int, name: String, running: Boolean = false) =
        AppItemUiState(id, name, running, hidden = false, hdrSupported = false)

    /** The sheet while a game runs: Quit closes the game. */
    private fun gameSheet(app: AppItemUiState, vararg choices: AppAction) =
        AppActionsUiState(app, "Steam Big Picture", choices.toList())

    /** The sheet while Desktop runs: Quit ends the stream and closes no program. */
    private fun desktopSheet(app: AppItemUiState, vararg choices: AppAction) =
        AppActionsUiState(app, "Desktop", choices.toList(), runningAppIsDesktop = true)

    private fun show(running: AppItemUiState, actions: AppActionsUiState) {
        val items = listOf(running, hades)
        composeRule.setContent {
            AppsScreen(
                state =
                    AppsUiState(
                        loading = false,
                        hostName = "Desk PC",
                        runningAppId = running.id,
                        runningAppName = running.name,
                        items = items,
                        actions = actions,
                    ),
                formFactor = FormFactor.Tv,
                snackbarHostState = SnackbarHostState(),
                onBack = {},
                onAppSelected = {},
                onActions = {},
                onActionChosen = { chosen += it },
                onActionsDismissed = {},
                onDetailsDismissed = {},
                onToggleShowHidden = {},
            )
        }
        composeRule.waitForIdle()
    }

    private fun assertNoUnsavedWarning() {
        composeRule.onAllNodesWithText("Unsaved", substring = true).assertCountEquals(0)
    }

    private fun assertNothingIsLost() {
        composeRule.onAllNodesWithText("lost", substring = true).assertCountEquals(0)
        assertNoUnsavedWarning()
    }

    @Test
    fun `the sheet of a running game says Quit closes it and its unsaved progress is lost`() {
        show(
            runningSteam,
            gameSheet(runningSteam, AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS),
        )

        composeRule.onNodeWithText("Quit Steam Big Picture").assertIsDisplayed()
        composeRule.onNodeWithText(GAME_QUIT).assertIsDisplayed()
    }

    @Test
    fun `the sheet of Desktop says nothing of lost work`() {
        show(desktop, desktopSheet(desktop, AppAction.RESUME, AppAction.QUIT, AppAction.DETAILS))

        assertNothingIsLost()
    }

    @Test
    fun `the sheet says a start would close the running game and lose its unsaved progress`() {
        show(
            runningSteam,
            gameSheet(hades, AppAction.QUIT_AND_START, AppAction.DETAILS, AppAction.HIDE),
        )

        composeRule.onNodeWithText("Quit Steam Big Picture and start Hades II").assertIsDisplayed()
        composeRule.onNodeWithText(GAME_QUIT_FIRST).assertIsDisplayed()
    }

    @Test
    fun `the sheet of another app does not warn of unsaved progress while Desktop runs`() {
        show(
            desktop,
            desktopSheet(hades, AppAction.QUIT_AND_START, AppAction.DETAILS, AppAction.HIDE),
        )

        composeRule.onNodeWithText(DESKTOP_QUIT_FIRST).assertIsDisplayed()
        assertNoUnsavedWarning()
    }

    @Test
    fun `the confirmation of Quit on a game says it closes and its unsaved progress is lost`() {
        show(runningSteam, gameSheet(runningSteam, AppAction.RESUME, AppAction.QUIT))

        composeRule.onNodeWithText("Quit Steam Big Picture").performClick()

        composeRule.onNodeWithText("Quit Steam Big Picture?").assertIsDisplayed()
        composeRule.onNodeWithText(GAME_QUIT).assertIsDisplayed()
        assertThat(chosen).isEmpty()

        composeRule.onNodeWithText("Quit").performClick()

        assertThat(chosen).containsExactly(AppAction.QUIT)
    }

    @Test
    fun `Quit and start on a game asks for confirmation saying its unsaved progress is lost`() {
        show(runningSteam, gameSheet(hades, AppAction.QUIT_AND_START, AppAction.DETAILS))

        composeRule.onNodeWithText("Quit Steam Big Picture and start Hades II").performClick()

        composeRule.onNodeWithText("Quit Steam Big Picture?").assertIsDisplayed()
        composeRule.onNodeWithText(GAME_QUIT_FIRST).assertIsDisplayed()
        assertThat(chosen).isEmpty()
    }

    @Test
    fun `the confirmation of Quit on Desktop says nothing of lost work`() {
        show(desktop, desktopSheet(desktop, AppAction.RESUME, AppAction.QUIT))

        composeRule.onNodeWithText("Quit Desktop").performClick()

        composeRule.onNodeWithText(DESKTOP_QUIT).assertIsDisplayed()
        assertNothingIsLost()
    }

    @Test
    fun `the title of the confirmation is only the question and the consequence is the body`() {
        show(runningSteam, gameSheet(runningSteam, AppAction.RESUME, AppAction.QUIT))

        composeRule.onNodeWithText("Quit Steam Big Picture").performClick()

        composeRule
            .onNode(
                hasText("Quit Steam Big Picture?") and
                    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)
            )
            .assertIsDisplayed()
        composeRule
            .onNode(
                hasText(GAME_QUIT) and SemanticsMatcher.keyNotDefined(SemanticsProperties.Heading)
            )
            .assertIsDisplayed()
    }

    private companion object {
        // One sentence a line.
        const val DESKTOP_QUIT = "Ends the stream.\nThe display and controls go back to the PC."
        const val DESKTOP_QUIT_FIRST = "Ends the stream first.\nNothing is lost."
        const val GAME_QUIT = "Closes Steam Big Picture on the PC.\nUnsaved progress in it is lost."
        const val GAME_QUIT_FIRST =
            "Closes Steam Big Picture on the PC first.\nUnsaved progress in it is lost."
    }
}
