package io.github.magalz.borealis.stream.ui

import android.graphics.Insets
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.core.view.WindowCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.StreamPhase
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.aPerf
import io.github.magalz.borealis.stream.testing.setChrome
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The activity is edge-to-edge and a stream dialog keeps its panel out of the system bars, but its
 * scrim covers the whole window: a tap in the strip of a bar is a tap on the scrim, which answers
 * like Cancel, and it does not fall through to what is under the dialog.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE)
class StreamDialogInsetsTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    /** A status bar of 40dp, which is 80 pixels at xhdpi. */
    private fun applyStatusBar() {
        composeRule.runOnUiThread {
            val window = composeRule.activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insets =
                WindowInsets.Builder()
                    .setInsets(WindowInsets.Type.systemBars(), Insets.of(0, STATUS_BAR_PX, 0, 0))
                    .build()
            window.decorView.dispatchApplyWindowInsets(insets)
        }
        composeRule.waitForIdle()
    }

    private fun tapInStatusBar() {
        composeRule.onRoot().performTouchInput { click(Offset(20f, STATUS_BAR_PX / 2f)) }
        composeRule.waitForIdle()
    }

    @Test
    fun `a tap in the status bar strip answers cancel to the conflict dialog`() {
        val intents = RecordingIntents()
        composeRule.setChrome(FormFactor.Phone) {
            ConflictDialog(StreamPhase.Conflict("Desktop", "Hades II", false), intents)
        }
        applyStatusBar()

        tapInStatusBar()

        assertThat(intents.calls).containsExactly("answerConflict CANCEL")
    }

    @Test
    fun `a tap in the status bar strip closes the Quit popup and not the menu`() {
        val fixture = LayerFixture(composeRule, FormFactor.Phone, aStreaming(Panel.MENU), aPerf())
        fixture.show()
        applyStatusBar()
        composeRule.onNodeWithText("Quit Desktop").performScrollTo().performClick()
        composeRule.onNodeWithText("Quit Desktop?").assertIsDisplayed()

        tapInStatusBar()

        composeRule.onNodeWithText("Quit Desktop?").assertDoesNotExist()
        assertThat(fixture.intents.calls).isEmpty()
    }

    private companion object {
        const val STATUS_BAR_PX = 80
    }
}
