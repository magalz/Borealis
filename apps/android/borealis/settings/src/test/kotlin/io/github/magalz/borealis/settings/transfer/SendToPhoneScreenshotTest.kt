package io.github.magalz.borealis.settings.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.diagnostics.transfer.QrMatrix
import io.github.magalz.borealis.diagnostics.transfer.TransferState
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the Send to phone dialog on a TV, one per state; the references live in
 * `src/test/screenshots`, named `sendtophone_tv_<state>.png`. The code is a stand-in with the
 * finder squares of a real one: it does not decode, and the link is a made-up private address.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w960dp-h540dp-land-television-xhdpi")
@OptIn(ExperimentalRoborazziApi::class)
class SendToPhoneScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun sendToPhoneTvWaiting() = shoot("waiting", FakeTransferController.waiting(qr = STAND_IN))

    @Test fun sendToPhoneTvDownloaded() = shoot("downloaded", TransferState.Downloaded)

    @Test fun sendToPhoneTvExpired() = shoot("expired", TransferState.Expired)

    @Test fun sendToPhoneTvNoNetwork() = shoot("no-network", TransferState.NoNetwork)

    private fun shoot(name: String, state: TransferState) {
        val transfer = FakeTransferController(state)
        composeRule.setContent {
            BorealisTheme(FormFactor.Tv) {
                Box(Modifier.fillMaxSize().background(LocalBorealisColors.current.page))
                SendToPhoneDialog(
                    File("/sdcard/Download/${FakeTransferController.FILE_NAME}"),
                    "adb pull /sdcard/Download/${FakeTransferController.FILE_NAME}",
                    {},
                    transfer,
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/sendtophone_tv_$name.png")
    }

    private companion object {
        const val SIZE = 29
        const val MARGIN = 2
        const val FINDER = 7

        /** The finder squares in three corners and pseudo-random modules elsewhere. */
        val STAND_IN: QrMatrix = standIn()

        fun standIn(): QrMatrix {
            var seed = 17
            val noise =
                BooleanArray(SIZE * SIZE) {
                    seed = (seed * 1_103_515_245 + 12_345) and 0x7fffffff
                    seed % 100 < 45
                }
            fun finder(x: Int, y: Int, ox: Int, oy: Int): Boolean? {
                val dx = x - ox
                val dy = y - oy
                if (dx !in 0 until FINDER || dy !in 0 until FINDER) return null
                val ring = maxOf(kotlin.math.abs(dx - 3), kotlin.math.abs(dy - 3))
                return ring != 2
            }
            return FakeTransferController.qr(SIZE + 2 * MARGIN) { x, y ->
                val cx = x - MARGIN
                val cy = y - MARGIN
                if (cx !in 0 until SIZE || cy !in 0 until SIZE) {
                    false
                } else {
                    finder(cx, cy, 0, 0)
                        ?: finder(cx, cy, SIZE - FINDER, 0)
                        ?: finder(cx, cy, 0, SIZE - FINDER)
                        ?: noise[cy * SIZE + cx]
                }
            }
        }
    }
}
