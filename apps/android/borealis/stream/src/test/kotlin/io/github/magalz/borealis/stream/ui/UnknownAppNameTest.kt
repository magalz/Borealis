package io.github.magalz.borealis.stream.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StartStage
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.setChrome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * A resume of a running app that the Library has no name for reaches the stream without one: no
 * screen shows an internal placeholder, they say "the running app".
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class UnknownAppNameTest {
    @get:Rule val composeRule = createComposeRule()

    private fun menu() {
        LayerFixture(composeRule, FormFactor.Phone, aStreaming(Panel.MENU), appName = "").show()
    }

    private fun noPlaceholder() {
        composeRule.onAllNodesWithText("app").assertCountEquals(0)
        composeRule.onAllNodesWithText("app · Gaming PC").assertCountEquals(0)
        composeRule.onAllNodesWithText("Quit app").assertCountEquals(0)
    }

    @Test
    fun `the menu header names the host alone`() {
        menu()

        composeRule.onNodeWithText("Gaming PC").assertIsDisplayed()
        noPlaceholder()
    }

    @Test
    fun `the Quit row and its consequence name the running app`() {
        menu()

        composeRule.onNodeWithText("Quit the running app").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithText("The app keeps running on the PC")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Closes the running app on the PC.\nUnsaved progress in it is lost.")
            .performScrollTo()
            .assertIsDisplayed()
        noPlaceholder()
    }

    @Test
    fun `the Quit confirmation names the running app`() {
        menu()
        composeRule.onNodeWithText("Quit the running app").performScrollTo().performClick()

        composeRule.onNodeWithText("Quit the running app?").assertIsDisplayed()
        composeRule
            .onNodeWithText("Closes the running app on the PC.\nUnsaved progress in it is lost.")
            .assertIsDisplayed()
        noPlaceholder()
    }

    @Test
    fun `the starting screen names the running app`() {
        composeRule.setChrome(FormFactor.Phone) {
            StartingScreen(
                "",
                "Gaming PC",
                StreamPhase.Starting(StartStage.STARTING_APP),
                RecordingIntents(),
            )
        }

        composeRule.onAllNodesWithText("Starting the running app").assertCountEquals(2)
        noPlaceholder()
    }

    @Test
    fun `the generic error names the running app`() {
        composeRule.setChrome(FormFactor.Phone) {
            ConnectionErrorScreen(
                "",
                "Gaming PC",
                StreamFailure(FailureKind.GENERIC, "Error -1", null),
                RecordingIntents(),
            )
        }

        composeRule.onNodeWithText("Couldn't start the running app").assertIsDisplayed()
        noPlaceholder()
    }
}
