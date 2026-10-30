package io.github.magalz.borealis.stream.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingActions
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.testing.aSessionInfo
import io.github.magalz.borealis.stream.testing.setChrome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A Disconnect under way: the connection ends and the app keeps running on the PC. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class DisconnectingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `it says which app the stream is disconnecting from`() {
        composeRule.setChrome(FormFactor.Phone) { DisconnectingScreen("Desktop") }

        composeRule.onNodeWithText("Disconnecting from Desktop…").assertIsDisplayed()
    }

    @Test
    fun `it names no app it does not know`() {
        composeRule.setChrome(FormFactor.Phone) { DisconnectingScreen("") }

        composeRule.onNodeWithText("Disconnecting from the running app…").assertIsDisplayed()
    }

    @Test
    fun `the chrome shows it as soon as the session disconnects`() {
        val session = aSession(RecordingActions())
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        composeRule.setContent { StreamChromeRoot(session, {}, BorealisLook()) }

        session.disconnect()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Disconnecting from Desktop…").assertIsDisplayed()
    }

    @Test
    fun `the chrome shows Quitting before the stream ends`() {
        val session = aSession(RecordingActions())
        session.onConnectionStarted(aSessionInfo)
        session.onFirstFrame()
        composeRule.setContent { StreamChromeRoot(session, {}, BorealisLook()) }

        session.quitApp()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Quitting Desktop…").assertIsDisplayed()
    }
}
