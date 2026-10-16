package io.github.magalz.borealis

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * The upstream manifest keeps predictive back off for the application and for the stream activity
 * (ADR 0052, D3): Back opens the in-game menu during a stream, and the stream activity must receive
 * it as a key event instead of an `OnBackInvokedCallback`. Reads the source manifest, so it needs
 * no Android runtime.
 */
class BackOptOutTest {
    private val manifest: Element =
        DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
            .documentElement

    private fun Element.backCallback(): String? =
        getAttributeNS(ANDROID_NS, "enableOnBackInvokedCallback").ifEmpty { null }

    @Test
    fun `the application opts out of predictive back`() {
        val application = manifest.getElementsByTagName("application").item(0) as Element

        assertThat(application.backCallback()).isEqualTo("false")
    }

    @Test
    fun `the stream activity opts out of predictive back`() {
        val activities = manifest.getElementsByTagName("activity")
        val game =
            (0 until activities.length)
                .map { activities.item(it) as Element }
                .single { it.getAttributeNS(ANDROID_NS, "name") == ".Game" }

        assertThat(game.backCallback()).isEqualTo("false")
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
