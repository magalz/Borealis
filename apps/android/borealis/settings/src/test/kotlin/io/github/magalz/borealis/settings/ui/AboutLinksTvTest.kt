package io.github.magalz.borealis.settings.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import io.github.magalz.borealis.settings.testing.assertDialogWidth
import io.github.magalz.borealis.settings.testing.dialogFrame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

private const val SOURCE_CODE = "Source code"
private const val PRIVACY_POLICY = "Privacy policy"
private const val SOURCE_CODE_URL = "https://github.com/magalz/Borealis"
private const val PRIVACY_POLICY_URL = "https://github.com/magalz/Borealis/blob/main/PRIVACY.md"
private const val HINT = "Scan with your phone to open this link."
private const val MIN_QR_DP = 200f
private const val MIN_QUIET_MODULES = 2f
private const val FINDER_MODULES = 7

/**
 * The About links on a TV (issue #53). The one app there that opens a link is a third-party one
 * that keeps Back, so Source code and Privacy policy show the address and a QR code in a dialog,
 * for a phone to open, and ask no app to open anything.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = TV)
class AboutLinksTvTest : SettingsScreenTestBase() {
    @Before
    fun theAboutGroupIsOpenOnATelevision() {
        asTelevision()
        show(keyboard = true, initialGroup = SettingsGroupId.ABOUT)
    }

    private val inDialog = hasAnyAncestor(isDialog())

    /** The row of About, and not the dialog that it opens, which has the same title. */
    private fun aboutRow(title: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(title) and !inDialog)

    private fun inDialog(text: String): SemanticsNodeInteraction =
        composeRule.onNode(hasText(text) and inDialog)

    private fun qrOf(title: String): SemanticsNodeInteraction =
        composeRule.onNodeWithContentDescription("QR code for the link to $title")

    private fun close(): SemanticsNodeInteraction = inDialog("Close")

    /** Focuses the row and presses OK on it, as a remote does. */
    private fun open(title: String) {
        aboutRow(title).requestFocus()
        aboutRow(title).performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()
    }

    private fun decode(qr: SemanticsNodeInteraction): String {
        val image = qr.captureToImage().asAndroidBitmap()
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        val source = RGBLuminanceSource(image.width, image.height, pixels)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }

    @Test
    fun `Source code opens a dialog with its title, its address and a hint`() {
        open(SOURCE_CODE)

        composeRule.dialogFrame(SOURCE_CODE).assertIsDisplayed()
        inDialog(SOURCE_CODE).assertIsDisplayed()
        inDialog(SOURCE_CODE_URL).assertIsDisplayed()
        inDialog(HINT).assertIsDisplayed()
    }

    @Test
    fun `Privacy policy opens a dialog with its title, its address and a hint`() {
        open(PRIVACY_POLICY)

        composeRule.dialogFrame(PRIVACY_POLICY).assertIsDisplayed()
        inDialog(PRIVACY_POLICY).assertIsDisplayed()
        inDialog(PRIVACY_POLICY_URL).assertIsDisplayed()
        inDialog(HINT).assertIsDisplayed()
    }

    @Test
    fun `a link asks no app to open it and shows no dialog about that`() {
        open(SOURCE_CODE)
        close().performKeyInput { pressKey(Key.DirectionCenter) }
        open(PRIVACY_POLICY)

        assertThat(openedLinks).isEmpty()
        composeRule.onNodeWithText("No app can open this link").assertDoesNotExist()
    }

    @Test
    fun `the QR code of Source code decodes to its address`() {
        open(SOURCE_CODE)

        assertThat(decode(qrOf(SOURCE_CODE))).isEqualTo(SOURCE_CODE_URL)
    }

    @Test
    fun `the QR code of Privacy policy decodes to its address`() {
        open(PRIVACY_POLICY)

        assertThat(decode(qrOf(PRIVACY_POLICY))).isEqualTo(PRIVACY_POLICY_URL)
    }

    @Test
    fun `the QR code is large enough to scan across a room`() {
        open(PRIVACY_POLICY)

        val bounds = qrOf(PRIVACY_POLICY).getUnclippedBoundsInRoot()

        assertThat((bounds.right - bounds.left).value).isAtLeast(MIN_QR_DP)
        assertThat((bounds.bottom - bounds.top).value).isAtLeast(MIN_QR_DP)
    }

    @Test
    fun `the QR code is dark on light and keeps a quiet zone around its modules`() {
        open(PRIVACY_POLICY)

        val image = qrOf(PRIVACY_POLICY).captureToImage().asAndroidBitmap()
        val dark = image.darkBounds()
        // The first dark run along the top edge of the code is a finder pattern, 7 modules wide.
        var run = 0
        while (image.getPixel(dark.left + run, dark.top) == Color.BLACK) run++
        val module = run.toFloat() / FINDER_MODULES

        assertThat(image.getPixel(0, 0)).isEqualTo(Color.WHITE)
        assertThat(image.getPixel(image.width - 1, image.height - 1)).isEqualTo(Color.WHITE)
        assertThat(dark.left / module).isAtLeast(MIN_QUIET_MODULES)
        assertThat(dark.top / module).isAtLeast(MIN_QUIET_MODULES)
        assertThat((image.width - 1 - dark.right) / module).isAtLeast(MIN_QUIET_MODULES)
        assertThat((image.height - 1 - dark.bottom) / module).isAtLeast(MIN_QUIET_MODULES)
    }

    @Test
    fun `the dialog is a wide one, 720dp`() {
        open(SOURCE_CODE)

        composeRule.assertDialogWidth(SOURCE_CODE, 720f)
    }

    @Test
    fun `Close has the focus when the dialog opens`() {
        open(SOURCE_CODE)

        close().assertIsFocused()
    }

    @Test
    fun `Close dismisses the dialog and the focus returns to the row that opened it`() {
        open(SOURCE_CODE)

        close().performKeyInput { pressKey(Key.DirectionCenter) }
        composeRule.waitForIdle()

        composeRule.dialogFrame(SOURCE_CODE).assertDoesNotExist()
        aboutRow(SOURCE_CODE).assertIsFocused()
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun `Back dismisses the dialog and the focus returns to the row that opened it`() {
        open(PRIVACY_POLICY)

        Espresso.pressBack()
        composeRule.waitForIdle()

        composeRule.dialogFrame(PRIVACY_POLICY).assertDoesNotExist()
        aboutRow(PRIVACY_POLICY).assertIsFocused()
        assertThat(backs).isEqualTo(0)
    }

    @Test
    fun `a screen reader hears that the image is a QR code for the link`() {
        open(SOURCE_CODE)

        qrOf(SOURCE_CODE).assertIsDisplayed()
    }

    @Test
    fun `a screen reader meets the address once, in the text and not in the image`() {
        open(SOURCE_CODE)

        val sayingTheAddress =
            composeRule.onAllNodes(
                (hasText(SOURCE_CODE_URL, substring = true) or
                    hasContentDescription(SOURCE_CODE_URL, substring = true)) and inDialog,
                useUnmergedTree = true,
            )

        assertThat(sayingTheAddress.fetchSemanticsNodes()).hasSize(1)
    }

    /** The smallest box that holds every black pixel: left, top, right and bottom, inclusive. */
    private fun Bitmap.darkBounds(): Rect {
        val bounds = Rect(width, height, -1, -1)
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (getPixel(x, y) == Color.BLACK) {
                    bounds.left = minOf(bounds.left, x)
                    bounds.top = minOf(bounds.top, y)
                    bounds.right = maxOf(bounds.right, x)
                    bounds.bottom = maxOf(bounds.bottom, y)
                }
            }
        }
        return bounds
    }
}
