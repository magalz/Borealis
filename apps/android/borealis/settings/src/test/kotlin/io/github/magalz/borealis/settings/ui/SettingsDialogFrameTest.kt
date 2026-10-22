package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.options.ResolutionChoice
import io.github.magalz.borealis.settings.options.ResolutionKind
import io.github.magalz.borealis.settings.testing.assertDialogWidth
import io.github.magalz.borealis.settings.testing.dialogFrame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The dialogs of the settings screen are the design system's: one frame, one width, one focus. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class SettingsDialogFrameTest : SettingsScreenTestBase() {
    @Before fun theDeviceIsATelevision() = asTelevision()

    private fun openLicences() {
        show(keyboard = true)
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("Open-source licences").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the licences dialog is a standard dialog, 560dp wide`() {
        openLicences()

        composeRule.assertDialogWidth("Open-source licences", 560f)
    }

    @Test
    fun `the licences dialog keeps its text, Close and the focus on Close`() {
        openLicences()

        composeRule.onNodeWithText("Close").assertIsFocused()
        composeRule
            .onNodeWithText("The licence texts of the test", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `Close and Back dismiss the licences dialog`() {
        openLicences()

        composeRule.onNodeWithText("Close").performClick()
        composeRule.dialogFrame("Open-source licences").assertDoesNotExist()
        composeRule.onNodeWithText("Open-source licences").performClick()
        composeRule.waitForIdle()
        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.dialogFrame("Open-source licences").assertDoesNotExist()
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun `Reset layout asks in a compact dialog with Cancel focused`() {
        show(keyboard = true)
        composeRule.onNodeWithText("On-screen controls").performClick()
        composeRule.onNodeWithText("Reset layout").performClick()
        composeRule.waitForIdle()

        composeRule.assertDialogWidth("Reset the layout?", 420f)
        composeRule.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun `the native resolution warning is a compact dialog`() {
        options.resolutions =
            STANDARD_RESOLUTIONS.take(1) +
                ResolutionChoice("2400x1080", 2400, 1080, ResolutionKind.NATIVE)
        show(keyboard = true)
        composeRule.onNodeWithText("Resolution").performClick()
        composeRule.waitForIdle()
        sheetOption("Native (2400x1080)").performClick()
        composeRule.waitForIdle()

        composeRule.assertDialogWidth("Native resolution", 420f)
    }

    @Test
    fun `the link dialog of About is a wide dialog, 720dp, the one of Send to phone`() {
        show(keyboard = true)
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithText("Source code").performClick()
        composeRule.waitForIdle()

        composeRule.assertDialogWidth("Source code", 720f)
    }
}
