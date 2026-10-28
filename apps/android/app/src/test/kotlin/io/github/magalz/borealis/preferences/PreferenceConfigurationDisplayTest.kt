package io.github.magalz.borealis.preferences

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.preferences.PreferenceConfiguration
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowDisplayManager

/**
 * Characterizes `PreferenceConfiguration.isSquarishScreen(Display)`, which takes the physical size
 * of the current display mode and applies the same ratio test as the two-number overload.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenceConfigurationDisplayTest {
    /**
     * A display whose mode is [width] by [height] pixels (the qualifiers use mdpi, one dp per
     * pixel).
     */
    private fun displayOf(width: Int, height: Int): Display {
        val id = ShadowDisplayManager.addDisplay("w${width}dp-h${height}dp-mdpi")
        val context: Context = ApplicationProvider.getApplicationContext()
        return context.getSystemService(DisplayManager::class.java).getDisplay(id)
    }

    @Test
    fun `a display with a physical ratio below 1,3 is squarish in either orientation`() {
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1200, 1000))).isTrue()
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1000, 1200))).isTrue()
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1299, 1000))).isTrue()
    }

    @Test
    fun `a display at the ratio 1,3 or beyond is not squarish`() {
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1300, 1000))).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1000, 1300))).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1920, 1080))).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(displayOf(1080, 1920))).isFalse()
    }

    @Test
    fun `the display overload takes the physical size of the display mode`() {
        val display = displayOf(1200, 1000)

        assertThat(display.mode.physicalWidth).isEqualTo(1200)
        assertThat(display.mode.physicalHeight).isEqualTo(1000)
        assertThat(PreferenceConfiguration.isSquarishScreen(display))
            .isEqualTo(PreferenceConfiguration.isSquarishScreen(1200, 1000))
    }
}
