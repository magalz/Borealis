package io.github.magalz.borealis.settings

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/** The module's own manifest declares the settings activity and keeps it private. */
class ManifestTest {
    private val activities: List<Element> by lazy {
        val manifest = File("src/main/AndroidManifest.xml")
        check(manifest.isFile) { "missing ${manifest.absolutePath}" }
        val document =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(manifest)
        val nodes = document.getElementsByTagName("activity")
        (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    @Test
    fun `the settings activity is declared and not exported`() {
        val activity = activities.single()

        assertThat(activity.androidAttribute("name")).isEqualTo(".BorealisSettingsActivity")
        assertThat(activity.androidAttribute("exported")).isEqualTo("false")
    }

    @Test
    fun `the settings activity has no intent filter`() {
        assertThat(activities.single().getElementsByTagName("intent-filter").length).isEqualTo(0)
    }
}
