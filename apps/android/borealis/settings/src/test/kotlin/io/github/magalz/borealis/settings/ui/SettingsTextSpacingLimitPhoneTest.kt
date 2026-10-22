package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.catalog.Settings
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The dialog and the summary of the Text spacing limit on a phone (ADR 0058, decision 4), where the
 * rows are in the sections of one list. The cases of the limit itself are in
 * [SettingsTextSpacingLimitTest].
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = PHONE, fontScale = 1.6f)
class SettingsTextSpacingLimitPhoneTest : PhoneSettingsTestBase() {
    private fun chooseWider() {
        row("Text spacing").performClick()
        composeRule.waitForIdle()
        sheetOption("Wider").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `choosing Wider where Wide is drawn opens the dialog, and OK closes it`() {
        showOpen()

        chooseWider()

        composeRule.onNodeWithText("Wider spacing is limited").assertExists()
        composeRule
            .onNodeWithText("At this text size, Borealis uses Wide spacing instead of Wider", true)
            .assertExists()
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wider")

        composeRule.onNodeWithText("OK").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Wider spacing is limited").assertDoesNotExist()
        assertThat(snapshot[Settings.TEXT_SPACING]).isEqualTo("wider")
    }

    @Test
    fun `the row says that Wide is drawn while Wider is chosen`() {
        stored(Settings.TEXT_SPACING to "wider")
        showOpen()

        row("Text spacing")
        composeRule
            .onNodeWithText("Drawn as Wide at this text size, so that nothing is cut off.")
            .assertExists()
    }
}
