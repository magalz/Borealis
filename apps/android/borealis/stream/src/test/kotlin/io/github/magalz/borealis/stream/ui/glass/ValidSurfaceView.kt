package io.github.magalz.borealis.stream.ui.glass

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView

/**
 * A `SurfaceView` that has a valid surface, which is what the stream view has once its surface is
 * created: Robolectric creates none. [locationQueries] counts how often the glass asked where the
 * view is on the screen, which it does on each tick of the copies.
 */
internal class ValidSurfaceView(context: Context) : SurfaceView(context) {
    private val fakeHolder = HolderOf(ValidSurface())

    var locationQueries = 0
        private set

    override fun getHolder(): SurfaceHolder = fakeHolder

    override fun getLocationOnScreen(outLocation: IntArray) {
        locationQueries++
        super.getLocationOnScreen(outLocation)
    }

    private class ValidSurface : Surface(SurfaceTexture(0)) {
        override fun isValid() = true
    }

    private class HolderOf(private val surface: Surface) : SurfaceHolder {
        override fun getSurface() = surface

        override fun addCallback(callback: SurfaceHolder.Callback?) = Unit

        override fun removeCallback(callback: SurfaceHolder.Callback?) = Unit

        override fun isCreating() = false

        override fun setType(type: Int) = Unit

        override fun setFixedSize(width: Int, height: Int) = Unit

        override fun setSizeFromLayout() = Unit

        override fun setFormat(format: Int) = Unit

        override fun setKeepScreenOn(screenOn: Boolean) = Unit

        override fun lockCanvas(): Canvas? = null

        override fun lockCanvas(dirty: Rect?): Canvas? = null

        override fun unlockCanvasAndPost(canvas: Canvas?) = Unit

        override fun getSurfaceFrame(): Rect = Rect()
    }
}
