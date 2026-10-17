package io.github.magalz.borealis.settings.transfer

import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import io.github.magalz.borealis.settings.transfer.FakeTransferController.Companion.FILE_NAME
import io.github.magalz.borealis.settings.transfer.FakeTransferController.Companion.LINK
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val TV_QUALIFIERS = "w960dp-h540dp-land-television-xhdpi"

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV_QUALIFIERS)
class SendToPhoneDialogTest {
    @get:Rule val composeRule = createComposeRule()

    private val bundle = File("/sdcard/Download/$FILE_NAME")
    private var dismissed = 0

    private fun open(transfer: FakeTransferController) {
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                SendToPhoneDialog(bundle, ADB_COMMAND, { dismissed++ }, transfer)
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `opening the dialog starts a link for the bundle, once`() {
        val transfer = FakeTransferController()

        open(transfer)
        transfer.show(FakeTransferController.waiting(remainingMillis = 100_000))
        composeRule.waitForIdle()

        assertThat(transfer.started).containsExactly(bundle)
    }

    @Test
    fun `waiting shows the link, the file name and size and the countdown`() {
        open(FakeTransferController(FakeTransferController.waiting(remainingMillis = 271_200)))

        composeRule.onNodeWithText(LINK).assertIsDisplayed()
        composeRule.onNodeWithText("$FILE_NAME · 58 KB", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("4:32", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the countdown follows the remaining time`() {
        val transfer = FakeTransferController(FakeTransferController.waiting(60_000))
        open(transfer)
        composeRule.onNodeWithText("1:00", substring = true).assertIsDisplayed()

        transfer.show(FakeTransferController.waiting(remainingMillis = 9_000))
        composeRule.waitForIdle()

        composeRule.onNodeWithText("0:09", substring = true).assertIsDisplayed()
    }

    @Test
    fun `the QR code is about 60 percent of the short side of the screen`() {
        open(FakeTransferController())

        val bounds = composeRule.onNodeWithTag(QR_TAG).getUnclippedBoundsInRoot()

        // The window is 960dp by 540dp.
        assertThat((bounds.right - bounds.left).value).isWithin(2f).of(324f)
        assertThat((bounds.bottom - bounds.top).value).isWithin(2f).of(324f)
    }

    @Test
    fun `the QR code draws its dark modules in black on white`() {
        open(FakeTransferController())

        val image = composeRule.onNodeWithTag(QR_TAG).captureToImage().asAndroidBitmap()
        val module = image.width / 4
        val offset = (image.width - module * 4) / 2

        for (y in 0 until 4) {
            for (x in 0 until 4) {
                val pixel =
                    image.getPixel(
                        offset + x * module + module / 2,
                        offset + y * module + module / 2,
                    )
                assertThat(pixel).isEqualTo(if (x == y) Color.BLACK else Color.WHITE)
            }
        }
    }

    @Test
    fun `downloaded says the link is closed and shows no code`() {
        open(FakeTransferController(TransferState.Downloaded))

        composeRule.onNodeWithText("Downloaded. The link is closed.").assertIsDisplayed()
        composeRule.onNodeWithTag(QR_TAG).assertDoesNotExist()
    }

    @Test
    fun `expired says so and Try again starts a new link for the same bundle`() {
        val transfer = FakeTransferController(TransferState.Expired)
        open(transfer)

        composeRule.onNodeWithText("The link expired", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Try again").performClick()
        composeRule.waitForIdle()

        assertThat(transfer.started).containsExactly(bundle, bundle)
        assertThat(dismissed).isEqualTo(0)
    }

    @Test
    fun `without a local network the dialog explains it and shows the adb command`() {
        open(FakeTransferController(TransferState.NoNetwork))

        composeRule.onNodeWithText("not on a local network", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(ADB_COMMAND).assertIsDisplayed()
        composeRule.onNodeWithTag(QR_TAG).assertDoesNotExist()
    }

    @Test
    fun `a failure shows an error line`() {
        open(FakeTransferController(TransferState.Failed))

        composeRule.onNodeWithText("could not be started", substring = true).assertIsDisplayed()
    }

    @Test
    fun `Close has the focus in every state`() {
        val states =
            listOf(
                TransferState.Idle,
                FakeTransferController.waiting(),
                TransferState.Downloaded,
                TransferState.Expired,
                TransferState.NoNetwork,
                TransferState.Failed,
            )
        for (state in states) {
            val transfer = FakeTransferController(state)
            composeRule.setContent {
                BorealisTheme(FormFactor.Tv) {
                    SendToPhoneDialog(bundle, ADB_COMMAND, {}, transfer)
                }
            }
            composeRule.waitForIdle()

            composeRule.onNodeWithText("Close").assertIsFocused()
        }
    }

    @Test
    fun `Close stops the link and dismisses the dialog`() {
        val transfer = FakeTransferController()
        open(transfer)

        composeRule.onNodeWithText("Close").performClick()
        composeRule.waitForIdle()

        assertThat(transfer.stops).isAtLeast(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `Back stops the link and dismisses the dialog`() {
        val transfer = FakeTransferController()
        open(transfer)

        Espresso.pressBack()
        composeRule.waitForIdle()

        assertThat(transfer.stops).isAtLeast(1)
        assertThat(dismissed).isEqualTo(1)
    }

    @Test
    fun `leaving the composition stops the link`() {
        val transfer = FakeTransferController()
        var shown by mutableStateOf(true)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                if (shown) SendToPhoneDialog(bundle, ADB_COMMAND, {}, transfer)
            }
        }
        composeRule.waitForIdle()
        assertThat(transfer.stops).isEqualTo(0)

        shown = false
        composeRule.waitForIdle()

        assertThat(transfer.stops).isAtLeast(1)
        assertThat(dismissed).isEqualTo(0)
    }

    private companion object {
        const val ADB_COMMAND = "adb pull /sdcard/Download/$FILE_NAME"
    }
}
