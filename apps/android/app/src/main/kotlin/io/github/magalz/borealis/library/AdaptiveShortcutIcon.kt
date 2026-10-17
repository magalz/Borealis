package io.github.magalz.borealis.library

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import androidx.core.graphics.createBitmap

/**
 * The foreground of an adaptive shortcut icon built from the box art of an app. The launcher masks
 * an adaptive icon: only the inner 72 of its 108 dp are sure to show, so the art is centre-cropped
 * to a square, scaled into that zone and set on the Borealis launcher sky.
 */
internal object AdaptiveShortcutIcon {
    /** The side of the icon layer: 108 dp at 4x. */
    const val SIZE_PX = 432

    private const val SAFE_ZONE_PX = SIZE_PX * 72 / 108

    // The gradient of the borealis_launcher_background vector, which the launcher icon uses.
    private const val SKY_TOP = 0xFF1B0E3A.toInt()
    private const val SKY_BOTTOM = 0xFF3D1E7E.toInt()

    /** A new square bitmap; [art] stays as it is. */
    fun build(art: Bitmap): Bitmap {
        // A hardware bitmap cannot be drawn on a software canvas.
        val source =
            if (art.config == Bitmap.Config.HARDWARE) art.copy(Bitmap.Config.ARGB_8888, false)
            else art
        val icon = createBitmap(SIZE_PX, SIZE_PX)
        val canvas = Canvas(icon)
        val sky =
            Paint().apply {
                shader =
                    LinearGradient(
                        0f,
                        0f,
                        0f,
                        SIZE_PX.toFloat(),
                        SKY_TOP,
                        SKY_BOTTOM,
                        Shader.TileMode.CLAMP,
                    )
            }
        canvas.drawRect(0f, 0f, SIZE_PX.toFloat(), SIZE_PX.toFloat(), sky)
        val side = minOf(source.width, source.height)
        val crop =
            Rect(
                (source.width - side) / 2,
                (source.height - side) / 2,
                (source.width + side) / 2,
                (source.height + side) / 2,
            )
        val inset = (SIZE_PX - SAFE_ZONE_PX) / 2
        val zone = Rect(inset, inset, inset + SAFE_ZONE_PX, inset + SAFE_ZONE_PX)
        canvas.drawBitmap(source, crop, zone, Paint(Paint.FILTER_BITMAP_FLAG))
        return icon
    }
}
