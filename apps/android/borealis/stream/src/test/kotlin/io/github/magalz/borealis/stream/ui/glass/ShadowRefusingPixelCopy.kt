package io.github.magalz.borealis.stream.ui.glass

import android.graphics.Bitmap
import android.os.Handler
import android.view.PixelCopy
import android.view.SurfaceView
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * A `PixelCopy` that refuses every request of a `SurfaceView`, as the platform does when the
 * surface went away after the caller checked it: Robolectric's own never refuses one.
 */
@Implements(PixelCopy::class)
object ShadowRefusingPixelCopy {
    @Implementation
    @JvmStatic
    @Suppress("UNUSED_PARAMETER")
    fun request(
        source: SurfaceView,
        dest: Bitmap,
        listener: PixelCopy.OnPixelCopyFinishedListener,
        listenerThread: Handler,
    ) {
        throw IllegalArgumentException("Source Surface is not valid")
    }
}
