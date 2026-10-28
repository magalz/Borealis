package io.github.magalz.borealis

import com.google.common.truth.Truth.assertThat
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

/**
 * Every activity that a manifest names has a class: a screen that was removed (the 12.2 host and
 * app lists) leaves no entry behind, which would only fail when something started it. Reads the
 * source manifests of the app and of the Library, so it needs no Android runtime.
 */
class ManifestActivitiesTest {
    private fun activityClasses(manifest: String, namespace: String): List<String> {
        val activities =
            DocumentBuilderFactory.newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(File(manifest))
                .getElementsByTagName("activity")
        return (0 until activities.length).map {
            val name = (activities.item(it) as Element).getAttributeNS(ANDROID_NS, "name")
            if (name.startsWith(".")) namespace + name else name
        }
    }

    private fun assertClassesExist(classes: List<String>) {
        assertThat(classes).isNotEmpty()
        val missing = classes.filter {
            runCatching { Class.forName(it, false, javaClass.classLoader) }.isFailure
        }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `every activity of the app manifest has a class`() {
        assertClassesExist(activityClasses("src/main/AndroidManifest.xml", "com.limelight"))
    }

    @Test
    fun `every activity of the Library manifest has a class`() {
        assertClassesExist(
            activityClasses(
                "../borealis/library/src/main/AndroidManifest.xml",
                "io.github.magalz.borealis.library",
            )
        )
    }

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
