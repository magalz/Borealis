package io.github.magalz.borealis.diagnostics.bundle

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Document
import org.w3c.dom.Element

/** The FileProvider is private, covers only the bundle directory, and the app may query for ACTION_SEND. */
class ManifestXmlTest {
    private fun parse(path: String): Document {
        val file = File(path)
        check(file.isFile) { "missing ${file.absolutePath}" }
        return DocumentBuilderFactory.newInstance()
            .apply { isNamespaceAware = true }
            .newDocumentBuilder()
            .parse(file)
    }

    private fun Element.androidAttribute(name: String): String =
        getAttributeNS("http://schemas.android.com/apk/res/android", name)

    private fun Document.elements(tag: String): List<Element> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val manifest by lazy { parse("src/main/AndroidManifest.xml") }

    @Test
    fun `the file provider is private and grants only temporary access`() {
        val provider = manifest.elements("provider").single()

        assertThat(provider.androidAttribute("name")).isEqualTo("androidx.core.content.FileProvider")
        assertThat(provider.androidAttribute("authorities"))
            .isEqualTo("\${applicationId}.diagnostics.fileprovider")
        assertThat(provider.androidAttribute("exported")).isEqualTo("false")
        assertThat(provider.androidAttribute("grantUriPermissions")).isEqualTo("true")
        val meta = provider.getElementsByTagName("meta-data").item(0) as Element
        assertThat(meta.androidAttribute("name")).isEqualTo("android.support.FILE_PROVIDER_PATHS")
        assertThat(meta.androidAttribute("resource")).isEqualTo("@xml/borealis_diagnostics_paths")
    }

    @Test
    fun `the app queries for zip share targets`() {
        val queries = manifest.elements("queries").single()
        val intent = queries.getElementsByTagName("intent").item(0) as Element
        val action = intent.getElementsByTagName("action").item(0) as Element
        val data = intent.getElementsByTagName("data").item(0) as Element

        assertThat(action.androidAttribute("name")).isEqualTo("android.intent.action.SEND")
        assertThat(data.androidAttribute("mimeType")).isEqualTo("application/zip")
    }

    @Test
    fun `the provider path covers only the bundle directory of the cache`() {
        val paths = parse("src/main/res/xml/borealis_diagnostics_paths.xml")

        val cache = paths.elements("cache-path").single()
        assertThat(cache.getAttribute("path")).isEqualTo("diagnostics/")
        assertThat(paths.elements("files-path")).isEmpty()
        assertThat(paths.elements("root-path")).isEmpty()
        assertThat(paths.elements("external-path")).isEmpty()
    }
}
