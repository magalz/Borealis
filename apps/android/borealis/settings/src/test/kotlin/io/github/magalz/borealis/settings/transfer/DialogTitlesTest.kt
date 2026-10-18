package io.github.magalz.borealis.settings.transfer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.settings.privacy.PrivacyDialog
import io.github.magalz.borealis.settings.privacy.PrivacyDialogActions
import io.github.magalz.borealis.settings.privacy.PrivacyDialogs
import io.github.magalz.borealis.settings.testing.textStyleOf
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Saved and Send to phone are one family of dialogs: they title themselves the same way. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
class DialogTitlesTest {
    @get:Rule val composeRule = createComposeRule()

    private val bundle = File("/sdcard/Download/${FakeTransferController.FILE_NAME}")
    private var sendToPhone by mutableStateOf(false)

    @Test
    fun `Saved and Send to phone have the same title style`() {
        val actions = PrivacyDialogActions({}, {}, {}, { _, _ -> })
        val saved =
            PrivacyDialog.Saved(
                location = "/sdcard/Download/${bundle.name}",
                adbPullCommand = "adb pull /sdcard/Download/${bundle.name}",
                bundle = bundle,
                fileName = bundle.name,
                sizeBytes = 59_392,
            )
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                if (sendToPhone) {
                    SendToPhoneDialog(bundle, "adb pull", {}, FakeTransferController())
                } else {
                    PrivacyDialogs(saved, actions)
                }
            }
        }
        composeRule.waitForIdle()
        val savedTitle = composeRule.textStyleOf("Diagnostics saved")

        sendToPhone = true
        composeRule.waitForIdle()
        val sendTitle = composeRule.textStyleOf("Send to phone")

        assertThat(sendTitle.fontSize).isEqualTo(savedTitle.fontSize)
        assertThat(sendTitle.fontWeight).isEqualTo(savedTitle.fontWeight)
        assertThat(sendTitle.fontFamily).isEqualTo(savedTitle.fontFamily)
        assertThat(sendTitle.lineHeight).isEqualTo(savedTitle.lineHeight)
        assertThat(sendTitle.color).isEqualTo(savedTitle.color)
    }
}
