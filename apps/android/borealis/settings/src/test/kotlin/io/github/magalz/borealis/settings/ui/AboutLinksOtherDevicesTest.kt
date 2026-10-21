package io.github.magalz.borealis.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val SOURCE_CODE_URL = "https://github.com/magalz/Borealis"
private const val PRIVACY_POLICY_URL = "https://github.com/magalz/Borealis/blob/main/PRIVACY.md"

/**
 * The About links on a phone and on a handheld still open in an app: only a TV, where the one app
 * that takes a link keeps Back, shows the address and a QR code instead (issue #53).
 */
@RunWith(AndroidJUnit4::class)
class AboutLinksOtherDevicesTest : PhoneSettingsTestBase() {
    private fun assertNoQrCode(title: String) {
        composeRule
            .onNodeWithContentDescription("QR code for the link to $title")
            .assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = PHONE)
    fun `on a phone Source code opens its link in an app and shows no QR code`() {
        showOpen()

        row("Source code").performClick()
        composeRule.waitForIdle()

        assertThat(openedLinks).containsExactly(SOURCE_CODE_URL)
        assertNoQrCode("Source code")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Source code opens its link in an app and shows no QR code`() {
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)

        composeRule.onNodeWithText("Source code").performClick()
        composeRule.waitForIdle()

        assertThat(openedLinks).containsExactly(SOURCE_CODE_URL)
        assertNoQrCode("Source code")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld Privacy policy opens its link in an app and shows no QR code`() {
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)

        composeRule.onNodeWithText("Privacy policy").performClick()
        composeRule.waitForIdle()

        assertThat(openedLinks).containsExactly(PRIVACY_POLICY_URL)
        assertNoQrCode("Privacy policy")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun `on a handheld with no browser a link still shows its address in a dialog`() {
        browserAvailable = false
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)

        composeRule.onNodeWithText("Source code").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No app can open this link").assertIsDisplayed()
        composeRule
            .onNode(hasText(SOURCE_CODE_URL) and hasAnyAncestor(isDialog()))
            .assertIsDisplayed()
        assertNoQrCode("Source code")
    }
}
