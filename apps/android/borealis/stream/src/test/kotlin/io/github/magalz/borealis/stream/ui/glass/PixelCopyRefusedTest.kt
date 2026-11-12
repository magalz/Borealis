package io.github.magalz.borealis.stream.ui.glass

import android.app.Activity
import android.graphics.Bitmap
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * A copy whose request the platform refuses, because the surface went away between the check of the
 * source and the request: it fails like any other copy that cannot be made, later and on the main
 * thread, and nothing reaches the caller.
 */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [ShadowRefusingPixelCopy::class])
class PixelCopyRefusedTest {
    private val activity: Activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val view = ValidSurfaceView(activity).apply { layout(0, 0, 1280, 720) }
    private val bitmap = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888)

    @Test
    fun `a request that the platform refuses ends the copy with false, later, and throws nothing`() {
        val results = mutableListOf<Boolean>()

        PixelCopySource(view).copy(bitmap) { results += it }
        assertThat(results).isEmpty()
        shadowOf(Looper.getMainLooper()).idle()

        assertThat(results).containsExactly(false)
    }
}
