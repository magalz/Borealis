package io.github.magalz.borealis.stream.ui.glass

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

/**
 * The real source of the copies: the stream view, which is a `SurfaceView`. Robolectric has no
 * compositor, so what is pinned here is what the source decides, and that a copy that cannot be
 * made says so; the copy itself (`PixelCopy`) is exercised on the devices.
 */
@RunWith(AndroidJUnit4::class)
class PixelCopySourceTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    /** A `SurfaceView` that has [surface], which is what a view has once its surface is created. */
    private inner class SurfaceViewWith(private val surface: Surface) : SurfaceView(activity) {
        private val fakeHolder = HolderOf(surface)

        override fun getHolder(): SurfaceHolder = fakeHolder
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

    /** A surface that is valid, as the one of a created `SurfaceView` is. */
    private class ValidSurface : Surface(SurfaceTexture(0)) {
        override fun isValid() = true
    }

    private fun validSurface() = ValidSurface()

    private fun sized(view: SurfaceView, width: Int = 1280, height: Int = 720) = view.apply {
        layout(0, 0, width, height)
    }

    private fun copyInto(source: VideoSource, bitmap: Bitmap): List<Boolean> {
        val results = mutableListOf<Boolean>()
        source.copy(bitmap) { results += it }
        shadowOf(Looper.getMainLooper()).idle()
        return results
    }

    private val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)

    @Test
    fun `a view that is not a SurfaceView has no video to copy`() {
        val source = PixelCopySource(View(activity))

        assertThat(source.bounds()).isNull()
        assertThat(copyInto(source, bitmap)).containsExactly(false)
    }

    @Test
    fun `a SurfaceView with no surface yet has no video to copy`() {
        val view = sized(SurfaceView(activity))

        assertThat(PixelCopySource(view).bounds()).isNull()
        assertThat(copyInto(PixelCopySource(view), bitmap)).containsExactly(false)
    }

    @Test
    fun `a surface with no size has no video to copy`() {
        val view = sized(SurfaceViewWith(validSurface()), width = 0, height = 0)

        assertThat(PixelCopySource(view).bounds()).isNull()
    }

    @Test
    fun `a SurfaceView with a surface is a video of its size`() {
        val view = sized(SurfaceViewWith(validSurface()))

        assertThat(PixelCopySource(view).bounds())
            .isEqualTo(VideoBounds(left = 0, top = 0, width = 1280, height = 720))
    }

    @Test
    fun `a copy of a surface that works says so, once`() {
        val view = sized(SurfaceViewWith(validSurface()))

        assertThat(copyInto(PixelCopySource(view), bitmap)).containsExactly(true)
    }
}
