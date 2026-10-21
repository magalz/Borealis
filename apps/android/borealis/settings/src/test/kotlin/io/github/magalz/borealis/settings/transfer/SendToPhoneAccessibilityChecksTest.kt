package io.github.magalz.borealis.settings.transfer

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.magalz.borealis.designsystem.testing.NoTouchOnTv
import io.github.magalz.borealis.designsystem.testing.assertAccessible
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.transfer.FakeTransferController.Companion.FILE_NAME
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Google's Accessibility Test Framework over the Send to phone dialog, on a TV. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class SendToPhoneAccessibilityChecksTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `the dialog while it waits for the phone`() {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                SendToPhoneDialog(
                    File("/sdcard/Download/$FILE_NAME"),
                    "adb pull /sdcard/Download/$FILE_NAME",
                    {},
                    FakeTransferController(FakeTransferController.waiting()),
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.assertAccessible(NoTouchOnTv)
    }
}
