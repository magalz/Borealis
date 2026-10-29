package io.github.magalz.borealis.stream.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.BorealisTheme
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.host.failure.FailureKind
import io.github.magalz.borealis.host.failure.StreamFailure
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.testing.aSession
import io.github.magalz.borealis.stream.ui.glass.FakeVideoSource
import io.github.magalz.borealis.stream.ui.glass.FrozenFrame
import io.github.magalz.borealis.stream.ui.glass.OverFrozenFrame
import io.github.magalz.borealis.stream.ui.glass.VideoBounds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Screenshots of the error popup on the frozen last frame of the stream, over a fixed fake frame: a
 * gradient with a few bright blocks, the picture of the stream that failed, blurred behind the
 * popup and in its glass panel. The popup on the background is `error_<device>_<state>`, in
 * `ConnectionErrorScreenshotTest`; the ones here are `error_frame_<device>_<state>`. The frame
 * shows sharp, with the solid panel, where Transparency is Off.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalRoborazziApi::class)
class ConnectionErrorFrameScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    @Config(qualifiers = TV)
    fun errorFrameTvStoppedFocused() =
        shoot(
            "error_frame_tv_stopped_focused",
            FormFactor.Tv,
            IntSize(1920, 1080),
            StreamFailure(FailureKind.STOPPED, "Error -1", null),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorFramePhoneStopped() =
        shoot(
            "error_frame_phone_stopped",
            FormFactor.Phone,
            IntSize(720, 1280),
            StreamFailure(FailureKind.STOPPED, "Error -1", null),
        )

    @Test
    @Config(qualifiers = HANDHELD)
    fun errorFrameHandheldStopped() =
        shoot(
            "error_frame_handheld_stopped",
            FormFactor.Handheld,
            IntSize(1200, 720),
            StreamFailure(FailureKind.STOPPED, "Error -1", null),
        )

    @Test
    @Config(qualifiers = PHONE)
    fun errorFramePhoneStoppedTransparencyOff() =
        shoot(
            "error_frame_phone_stopped_transparency-off",
            FormFactor.Phone,
            IntSize(720, 1280),
            StreamFailure(FailureKind.STOPPED, "Error -1", null),
            BorealisLook(transparency = Transparency.Off),
        )

    /** A frame of the fake video: the same picture at any size, so the copy matches the stream. */
    private fun paintFrame(bitmap: Bitmap) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val paint = Paint()
        paint.shader =
            LinearGradient(
                0f,
                0f,
                w,
                h,
                intArrayOf(0xFF1E88E5.toInt(), 0xFFFDD835.toInt(), 0xFFE53935.toInt()),
                null,
                Shader.TileMode.CLAMP,
            )
        val canvas = Canvas(bitmap)
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(w * 0.62f, h * 0.10f, w * 0.98f, h * 0.45f, paint)
        paint.color = 0xFF000000.toInt()
        canvas.drawRect(w * 0.62f, h * 0.55f, w * 0.98f, h * 0.90f, paint)
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawRect(w * 0.05f, h * 0.60f, w * 0.30f, h * 0.95f, paint)
    }

    private fun shoot(
        name: String,
        formFactor: FormFactor,
        px: IntSize,
        failure: StreamFailure,
        look: BorealisLook = BorealisLook(),
    ) {
        val source =
            FakeVideoSource(
                bounds = VideoBounds(0, 0, px.width, px.height),
                completing = true,
                paint = ::paintFrame,
            )
        val frozen = FrozenFrame(source, { IntOffset.Zero })
        frozen.request()
        composeRule.setContent {
            BorealisTheme(formFactor, look) {
                OverFrozenFrame(frozen, checkNotNull(frozen.frame), menuGlass = true) {
                    ConnectionErrorScreen("Desktop", "Desk PC", failure, aSession())
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/screenshots/$name.png")
    }
}
