package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import io.github.magalz.borealis.designsystem.theme.BorealisLook
import io.github.magalz.borealis.designsystem.theme.FormFactor
import io.github.magalz.borealis.designsystem.theme.Transparency
import io.github.magalz.borealis.stream.Panel
import io.github.magalz.borealis.stream.testing.HANDHELD
import io.github.magalz.borealis.stream.testing.PHONE_LANDSCAPE
import io.github.magalz.borealis.stream.testing.TV
import io.github.magalz.borealis.stream.ui.aStreaming
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The in-game menu and its Quit popup over a fixed fake video frame: a gradient with a few bright
 * blocks, which is the picture of the stream behind the chrome and, a fourth of its size and
 * blurred, the glass of the panels. Named `menu_glass_<device>_<state>`; the solid fallback is
 * `menu_solid_<device>_<why>`.
 */
@RunWith(AndroidJUnit4::class)
class MenuGlassScreenshotTest {
    @get:Rule val composeRule = createComposeRule()

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

    /** The menu on a screen of [px] pixels, over the fake video that fills it. */
    private fun fixture(
        formFactor: FormFactor,
        px: IntSize,
        look: BorealisLook = BorealisLook(),
        keyboard: Boolean = false,
        white: Boolean = false,
    ): GlassFixture {
        // The worst frame for the text, which the tint is chosen for: all white.
        val paint: (Bitmap) -> Unit =
            if (white) { bitmap -> bitmap.eraseColor(WHITE) } else ::paintFrame
        val source =
            FakeVideoSource(
                bounds = VideoBounds(0, 0, px.width, px.height),
                completing = true,
                paint = paint,
            )
        // The picture of the stream behind the chrome, at the size of the screen.
        val picture = Bitmap.createBitmap(px.width, px.height, Bitmap.Config.ARGB_8888)
        paint(picture)
        return GlassFixture(composeRule, source).apply {
            this.formFactor = formFactor
            this.look = look
            keyboardMode = keyboard
            if (formFactor != FormFactor.Phone) {
                phase = aStreaming(Panel.MENU, touchMode = null, gamepadMouse = false)
            }
            behind = {
                Image(
                    picture.asImageBitmap(),
                    null,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage("src/test/screenshots/$name.png")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuGlassPhone() {
        fixture(FormFactor.Phone, PHONE_PX).apply { show() }.run(SETTLE_MS)

        shoot("menu_glass_phone")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuGlassTvFocused() {
        fixture(FormFactor.Tv, TV_PX, keyboard = true).apply { show() }.run(SETTLE_MS)

        shoot("menu_glass_tv_focused")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun menuGlassHandheld() {
        fixture(FormFactor.Handheld, HANDHELD_PX, keyboard = true).apply { show() }.run(SETTLE_MS)

        shoot("menu_glass_handheld")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuGlassPhoneQuitConfirm() {
        fixture(FormFactor.Phone, PHONE_PX).apply {
            show()
            openQuitPopup()
            run(SETTLE_MS)
        }

        shoot("menu_glass_phone_quit-confirm")
    }

    @Test
    @Config(qualifiers = TV)
    fun menuGlassTvQuitConfirmFocused() {
        fixture(FormFactor.Tv, TV_PX, keyboard = true).apply {
            show()
            openQuitFromKeys()
            run(SETTLE_MS)
        }

        shoot("menu_glass_tv_quit-confirm_focused")
    }

    @Test
    @Config(qualifiers = HANDHELD)
    fun menuGlassHandheldQuitConfirmFocused() {
        fixture(FormFactor.Handheld, HANDHELD_PX, keyboard = true).apply {
            show()
            openQuitFromKeys()
            run(SETTLE_MS)
        }

        shoot("menu_glass_handheld_quit-confirm_focused")
    }

    /** The whitest frame: the glass is as light as it can get, and the text keeps 4.5:1. */
    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuGlassPhoneWhiteFrame() {
        fixture(FormFactor.Phone, PHONE_PX, white = true).apply { show() }.run(SETTLE_MS)

        shoot("menu_glass_phone_white-frame")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuGlassPhoneQuitConfirmWhiteFrame() {
        fixture(FormFactor.Phone, PHONE_PX, white = true).apply {
            show()
            openQuitPopup()
            run(SETTLE_MS)
        }

        shoot("menu_glass_phone_quit-confirm_white-frame")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuSolidPhoneTransparencyOff() {
        fixture(FormFactor.Phone, PHONE_PX, BorealisLook(transparency = Transparency.Off))
            .apply { show() }
            .run(SETTLE_MS)

        shoot("menu_solid_phone_transparency-off")
    }

    @Test
    @Config(qualifiers = PHONE_LANDSCAPE)
    fun menuSolidPhoneQuitConfirmTransparencyOff() {
        fixture(FormFactor.Phone, PHONE_PX, BorealisLook(transparency = Transparency.Off)).apply {
            show()
            openQuitPopup()
            run(SETTLE_MS)
        }

        shoot("menu_solid_phone_quit-confirm_transparency-off")
    }

    private companion object {
        val PHONE_PX = IntSize(1280, 720)
        val TV_PX = IntSize(1920, 1080)
        val HANDHELD_PX = IntSize(1200, 720)
        const val SETTLE_MS = 100L
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
