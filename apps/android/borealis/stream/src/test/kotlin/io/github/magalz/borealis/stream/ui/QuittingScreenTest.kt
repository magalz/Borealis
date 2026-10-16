package io.github.magalz.borealis.stream.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.setChrome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** A Quit under way: the stream ended with the app, and the screen waits for the host's answer. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class QuittingScreenTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `it says the app is being quit`() {
        composeRule.setChrome(FormFactor.Phone) { QuittingScreen("Desktop") }

        composeRule.onNodeWithText("Quitting Desktop…").assertIsDisplayed()
    }

    @Test
    fun `it names no app it does not know`() {
        composeRule.setChrome(FormFactor.Phone) { QuittingScreen("") }

        composeRule.onNodeWithText("Quitting the running app…").assertIsDisplayed()
    }
}
