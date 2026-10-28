package io.github.magalz.borealis

import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.limelight.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.xmlpull.v1.XmlPullParser

/** The host and pinned-app shortcut icon is Borealis art, not Moonlight's white PC icon. */
@RunWith(RobolectricTestRunner::class)
class ShortcutIconTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun shortcutIcon(): AdaptiveIconDrawable =
        context.getDrawable(R.mipmap.ic_pc_scut) as AdaptiveIconDrawable

    /** The resource id each layer of an adaptive icon XML points at, by layer tag. */
    private fun layerResources(iconRes: Int): Map<String, Int> {
        val layers = mutableMapOf<String, Int>()
        context.resources.getXml(iconRes).use { xml ->
            while (xml.next() != XmlPullParser.END_DOCUMENT) {
                if (xml.eventType == XmlPullParser.START_TAG && xml.name != "adaptive-icon") {
                    layers[xml.name] = xml.getAttributeResourceValue(ANDROID_NS, "drawable", 0)
                }
            }
        }
        return layers
    }

    @Test
    fun `the shortcut icon is an adaptive icon`() {
        assertThat(context.getDrawable(R.mipmap.ic_pc_scut))
            .isInstanceOf(AdaptiveIconDrawable::class.java)
    }

    @Test
    fun `the shortcut icon uses the launcher sky as its background, not Moonlight's white`() {
        val shortcut = layerResources(R.mipmap.ic_pc_scut)
        val launcher = layerResources(R.mipmap.ic_launcher)

        assertThat(launcher["background"]).isNotEqualTo(0)
        assertThat(shortcut["background"]).isEqualTo(launcher["background"])
    }

    @Test
    fun `the shortcut icon has a monochrome layer for themed icons`() {
        assertThat(layerResources(R.mipmap.ic_launcher)).containsKey("monochrome")

        assertThat(shortcutIcon().monochrome).isNotNull()
    }

    @Test
    fun `the shortcut icon layers are vectors, not Moonlight's PNGs`() {
        val icon = shortcutIcon()

        assertThat(icon.foreground).isNotInstanceOf(BitmapDrawable::class.java)
        assertThat(icon.monochrome).isNotInstanceOf(BitmapDrawable::class.java)
    }

    @Test
    fun `the shortcut icon has its own glyph, distinct from the launcher icon`() {
        val shortcut = layerResources(R.mipmap.ic_pc_scut)
        val launcher = layerResources(R.mipmap.ic_launcher)

        assertThat(shortcut["foreground"]).isNotEqualTo(launcher["foreground"])
        assertThat(shortcut["monochrome"]).isNotEqualTo(launcher["monochrome"])
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
