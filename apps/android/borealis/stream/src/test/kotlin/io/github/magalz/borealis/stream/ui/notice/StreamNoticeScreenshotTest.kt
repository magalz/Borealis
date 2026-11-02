package io.github.magalz.borealis.stream.ui.notice

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.LocalBorealisColors
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.stream.StreamNotice
import io.github.magalz.borealis.stream.SurroundLayout
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.RecordingIntents
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.StreamingLayer
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the notice card over the stream, named `notice_<device>_<notice>`. The stream is a
 * gradient, so the tint of the card can be seen over a picture.
 */
@RunWith(AndroidJUnit4::class)
class StreamNoticeScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun noticePhoneBluetooth() {
        composeRule.setContent {
            Stream(FormFactor.Phone, BorealisLook()) { Layer(StreamNotice.BluetoothOutput) }
        }

        shoot("notice_phone_bluetooth")
    }

    @Test
    @Config(qualifiers = TV)
    fun noticeTvStereoFallback() {
        composeRule.setContent {
            Stream(FormFactor.Tv, BorealisLook()) {
                Layer(StreamNotice.StereoFallback(SurroundLayout.SURROUND_7_1))
            }
        }

        shoot("notice_tv_stereo-fallback")
    }

    /** The solid card, where the glass rules say so. */
    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun noticePhoneBluetoothTransparencyOff() {
        composeRule.setContent {
            Stream(FormFactor.Phone, BorealisLook(transparency = Transparency.Off)) {
                Layer(StreamNotice.BluetoothOutput)
            }
        }

        shoot("notice_phone_bluetooth_transparency-off")
    }

    @Composable
    private fun Layer(notice: StreamNotice) =
        StreamingLayer(
            "Desktop",
            "Gaming PC",
            aStreaming(),
            null,
            RecordingIntents(),
            notice = notice,
            onNoticeShown = {},
        )

    /** The picture of the stream is a gradient; [content] is the chrome over it. */
    @Composable
    private fun Stream(
        formFactor: FormFactor,
        look: BorealisLook,
        content: @Composable () -> Unit,
    ) {
        BorealisTheme(formFactor, look) {
            val colors = LocalBorealisColors.current
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.verticalGradient(listOf(colors.accentStrong, colors.page)))
            ) {
                content()
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }
}
