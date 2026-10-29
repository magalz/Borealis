package io.github.magalz.borealis.stream.ui

import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.setChrome
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Back of the system (the gesture, or the key when nothing in the chrome has the focus, as on a
 * phone in touch mode) while a stream dialog is open: the dialog takes it, and the host's callback
 * (`StreamChromeHost` registers one that closes the panel or answers Cancel) is for when no dialog
 * is open.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class StreamDialogBackTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()
    private val hostBacks = mutableListOf<String>()

    @Before
    fun `the host listens for Back`() {
        composeRule.activity.onBackPressedDispatcher.addCallback(
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    hostBacks += "host"
                }
            }
        )
    }

    private fun pressBack() {
        Espresso.pressBack()
        composeRule.waitForIdle()
    }

    @Test
    fun `Back closes the Quit popup and not the menu, then goes to the host again`() {
        val fixture = LayerFixture(composeRule, FormFactor.Phone, aStreaming(Panel.MENU), aPerf())
        fixture.show()
        composeRule.onNodeWithText("Quit Desktop").performScrollTo().performClick()
        composeRule.onNodeWithText("Quit Desktop?").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithText("Quit Desktop?").assertDoesNotExist()
        composeRule.onNodeWithText("Quit Desktop").assertIsDisplayed()
        assertThat(hostBacks).isEmpty()
        assertThat(fixture.intents.calls).isEmpty()

        pressBack()

        assertThat(hostBacks).containsExactly("host")
    }

    @Test
    fun `Back answers Cancel to the conflict dialog and not to the host`() {
        val intents = RecordingIntents()
        composeRule.setChrome(FormFactor.Phone) {
            ConflictDialog(StreamPhase.Conflict("Desktop", "Hades II", false), intents)
        }
        composeRule.onNodeWithText("An app is already running").assertIsDisplayed()

        pressBack()

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
        assertThat(hostBacks).isEmpty()
    }
}
