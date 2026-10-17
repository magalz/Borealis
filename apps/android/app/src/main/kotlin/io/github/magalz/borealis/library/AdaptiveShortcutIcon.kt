package io.github.magalz.borealis.library

import android.graphics.Bitmap

/** Builds the foreground of an adaptive icon from the box art of an app. */
internal object AdaptiveShortcutIcon {
    /** 108 dp at 4x: the side of an adaptive icon layer. */
    const val SIZE_PX = 432

    fun build(art: Bitmap): Bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
}
