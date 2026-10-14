package io.github.magalz.borealis.host.launch

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.host.R
import io.github.magalz.borealis.nativecore.LaunchCategory
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Test
import org.w3c.dom.Element

class LaunchProgressTextTest {
    /** Renders a string as `id:arg|arg` so a test sees which resource got which arguments. */
    private val fake: (Int, List<Any>) -> String = { id, args -> "$id:" + args.joinToString("|") }

    private fun text(category: LaunchCategory, attempt: Int, max: Int, delayMs: Int) =
        LaunchProgressText.format(fake, category.ordinal, attempt, max, delayMs)

    @Test
    fun `each retried category has its own text with seconds, attempt and attempt limit`() {
        assertThat(text(LaunchCategory.PENDING, 2, 4, 2000))
            .isEqualTo("${R.string.launch_retry_pending}:2|2|4")
        assertThat(text(LaunchCategory.RESTORING_DISPLAY, 1, 3, 2000))
            .isEqualTo("${R.string.launch_retry_restoring_display}:2|1|3")
        assertThat(text(LaunchCategory.GPU_NOT_READY, 1, 2, 5000))
            .isEqualTo("${R.string.launch_retry_gpu_not_ready}:5|1|2")
        assertThat(text(LaunchCategory.CAPTURE_OR_DISPLAY, 1, 1, 3000))
            .isEqualTo("${R.string.launch_retry_capture_or_display}:3|1|1")
    }

    @Test
    fun `a delay that is not a whole second is rounded up`() {
        assertThat(text(LaunchCategory.PENDING, 1, 4, 1))
            .isEqualTo("${R.string.launch_retry_pending}:1|1|4")
        assertThat(text(LaunchCategory.PENDING, 1, 4, 1001))
            .isEqualTo("${R.string.launch_retry_pending}:2|1|4")
        assertThat(text(LaunchCategory.PENDING, 1, 4, 0))
            .isEqualTo("${R.string.launch_retry_pending}:0|1|4")
    }

    @Test
    fun `a category that is never retried or unknown gets the generic text`() {
        assertThat(text(LaunchCategory.PERMANENT, 1, 4, 1000))
            .isEqualTo("${R.string.launch_retry_generic}:1|1|4")
        assertThat(LaunchProgressText.format(fake, 99, 1, 4, 1000))
            .isEqualTo("${R.string.launch_retry_generic}:1|1|4")
        assertThat(LaunchProgressText.format(fake, -1, 1, 4, 1000))
            .isEqualTo("${R.string.launch_retry_generic}:1|1|4")
    }

    @Test
    fun `English and Brazilian Portuguese define the same strings with the same placeholders`() {
        val english = strings("values")
        val portuguese = strings("values-pt-rBR")

        assertThat(english.keys)
            .containsAtLeast(
                "launch_retry_pending",
                "launch_retry_restoring_display",
                "launch_retry_gpu_not_ready",
                "launch_retry_capture_or_display",
                "launch_retry_generic",
            )
        assertThat(portuguese.keys).isEqualTo(english.keys)
        for ((name, text) in english) {
            assertThat(placeholders(portuguese.getValue(name))).isEqualTo(placeholders(text))
        }
    }

    @Test
    fun `every retry text carries seconds, attempt and attempt limit`() {
        for (folder in listOf("values", "values-pt-rBR")) {
            for ((_, text) in strings(folder).filterKeys { it.startsWith("launch_retry_") }) {
                assertThat(placeholders(text)).containsExactly("%1\$s", "%2\$s", "%3\$s")
            }
        }
    }

    private fun strings(folder: String): Map<String, String> {
        val file = File("src/main/res/$folder/strings_launch.xml")
        val nodes =
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
                .getElementsByTagName("string")
        return (0 until nodes.length).associate {
            val element = nodes.item(it) as Element
            element.getAttribute("name") to element.textContent
        }
    }

    private fun placeholders(text: String): List<String> =
        Regex("""%\d+\$[a-z]""").findAll(text).map { it.value }.sorted().toList()
}
