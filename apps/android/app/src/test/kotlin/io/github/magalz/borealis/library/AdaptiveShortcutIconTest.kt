package io.github.magalz.borealis.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.google.common.truth.Truth.assertThat
import kotlin.math.abs
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/** The box art of an app, as the foreground of an adaptive shortcut icon. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdaptiveShortcutIconTest {
    /**
     * A 300x400 portrait: blue, with yellow bands on the top and bottom 50 rows that a centre crop
     * to a square drops, and a red block in the centre.
     */
    private fun portrait(): Bitmap {
        val art = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(art)
        val paint = Paint()
        canvas.drawColor(BLUE)
        paint.color = YELLOW
        canvas.drawRect(0f, 0f, 300f, 50f, paint)
        canvas.drawRect(0f, 350f, 300f, 400f, paint)
        paint.color = RED
        canvas.drawRect(100f, 150f, 200f, 250f, paint)
        return art
    }

    private val icon = AdaptiveShortcutIcon.build(portrait())

    private fun channelsNear(actual: Int, expected: Int, tolerance: Int = 3) {
        for (shift in intArrayOf(0, 8, 16, 24)) {
            val a = (actual shr shift) and 0xFF
            val e = (expected shr shift) and 0xFF
            assertThat(abs(a - e)).isAtMost(tolerance)
        }
    }

    @Test
    fun `the icon layer is a square of 108 dp at 4x`() {
        assertThat(icon.width).isEqualTo(AdaptiveShortcutIcon.SIZE_PX)
        assertThat(icon.height).isEqualTo(AdaptiveShortcutIcon.SIZE_PX)
        assertThat(AdaptiveShortcutIcon.SIZE_PX).isEqualTo(432)
    }

    @Test
    fun `the centre pixel comes from the middle of the art`() {
        assertThat(icon.getPixel(216, 216)).isEqualTo(RED)
    }

    @Test
    fun `the corners are the Borealis launcher background, a sky that is darker at the top`() {
        val last = AdaptiveShortcutIcon.SIZE_PX - 1
        assertThat(icon.getPixel(0, 0)).isEqualTo(SKY_TOP)
        assertThat(icon.getPixel(last, 0)).isEqualTo(SKY_TOP)
        channelsNear(icon.getPixel(0, last), SKY_BOTTOM)
        channelsNear(icon.getPixel(last, last), SKY_BOTTOM)
    }

    @Test
    fun `the art stays inside the safe zone, the inner 72 of 108 dp`() {
        // The zone is 288 px wide, from 72 to 360. Outside it a row is as plain as its left end.
        for (y in intArrayOf(80, 216, 350)) {
            val background = icon.getPixel(0, y)
            assertThat(icon.getPixel(71, y)).isEqualTo(background)
            assertThat(icon.getPixel(360, y)).isEqualTo(background)
            assertThat(icon.getPixel(75, y)).isEqualTo(BLUE)
            assertThat(icon.getPixel(356, y)).isEqualTo(BLUE)
        }
        for (x in intArrayOf(80, 216, 350)) {
            assertThat(icon.getPixel(x, 71)).isEqualTo(icon.getPixel(0, 71))
            assertThat(icon.getPixel(x, 360)).isEqualTo(icon.getPixel(0, 360))
        }
    }

    @Test
    fun `the art is cropped to a centred square, not squeezed`() {
        // The yellow bands are in the top and bottom 50 rows, which the crop drops.
        val pixels = IntArray(icon.width * icon.height)
        icon.getPixels(pixels, 0, icon.width, 0, 0, icon.width, icon.height)
        assertThat(pixels.count { it == YELLOW }).isEqualTo(0)
        // The red block is 100 of the 300 columns: a third of the 288 px square, centred.
        val redColumns = (0 until icon.width).count { icon.getPixel(it, 216) == RED }
        assertThat(redColumns).isAtLeast(94)
        assertThat(redColumns).isAtMost(98)
    }

    @Test
    fun `hardware art is drawn too`() {
        val hardware = portrait().copy(Bitmap.Config.HARDWARE, false)

        val built = AdaptiveShortcutIcon.build(hardware)

        assertThat(built.getPixel(216, 216)).isEqualTo(RED)
    }

    private companion object {
        val BLUE = Color.parseColor("#FF2040C0")
        val YELLOW = Color.parseColor("#FFFFE000")
        val RED = Color.parseColor("#FFE02020")

        // The first and last colour of the launcher background gradient: the
        // borealis_launcher_background vector.
        val SKY_TOP = Color.parseColor("#FF1B0E3A")
        val SKY_BOTTOM = Color.parseColor("#FF3D1E7E")
    }
}
