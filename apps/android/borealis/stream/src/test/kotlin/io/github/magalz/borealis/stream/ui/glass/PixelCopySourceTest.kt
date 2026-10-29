package io.github.magalz.borealis.stream.ui.glass

import android.app.Activity
import android.graphics.Bitmap
import android.os.Looper
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

    private fun <V : SurfaceView> sized(view: V, width: Int = 1280, height: Int = 720): V =
        view.apply {
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
        val view = sized(ValidSurfaceView(activity), width = 0, height = 0)

        assertThat(PixelCopySource(view).bounds()).isNull()
    }

    @Test
    fun `a SurfaceView with a surface is a video of its size`() {
        val view = sized(ValidSurfaceView(activity))

        assertThat(PixelCopySource(view).bounds())
            .isEqualTo(VideoBounds(left = 0, top = 0, width = 1280, height = 720))
    }

    @Test
    fun `a copy of a surface that works says so, once`() {
        val view = sized(ValidSurfaceView(activity))

        assertThat(copyInto(PixelCopySource(view), bitmap)).containsExactly(true)
    }
}
