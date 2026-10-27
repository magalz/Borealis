package io.github.magalz.borealis.stream.game

import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.SurfaceHolder

/** One call of `Surface.setFrameRate`. */
internal data class FrameRateRequest(
    val frameRate: Float,
    val compatibility: Int,
    val changeFrameRateStrategy: Int,
)

/** A surface that remembers the frame rate that it was asked for. */
internal class RecordingSurface : Surface(SurfaceTexture(0)) {
    val frameRates = mutableListOf<FrameRateRequest>()

    override fun setFrameRate(frameRate: Float, compatibility: Int, changeFrameRateStrategy: Int) {
        frameRates += FrameRateRequest(frameRate, compatibility, changeFrameRateStrategy)
    }
}

/**
 * The holder that the surface callbacks of the activity receive. The one of a unit test has no
 * surface, so the callbacks get this one, which has a [RecordingSurface].
 */
internal class RecordingSurfaceHolder : SurfaceHolder {
    val recordingSurface = RecordingSurface()

    override fun getSurface(): Surface = recordingSurface

    override fun addCallback(callback: SurfaceHolder.Callback?) = Unit

    override fun removeCallback(callback: SurfaceHolder.Callback?) = Unit

    override fun isCreating(): Boolean = false

    @Deprecated("The holder has no type to set.") override fun setType(type: Int) = Unit

    override fun setFixedSize(width: Int, height: Int) = Unit

    override fun setSizeFromLayout() = Unit

    override fun setFormat(format: Int) = Unit

    override fun setKeepScreenOn(screenOn: Boolean) = Unit

    override fun lockCanvas(): Canvas? = null

    override fun lockCanvas(dirty: Rect?): Canvas? = null

    override fun unlockCanvasAndPost(canvas: Canvas?) = Unit

    override fun getSurfaceFrame(): Rect = Rect()
}
