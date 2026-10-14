package io.github.magalz.borealis.diagnostics.bundle

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.Redactor
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** No canary secret survives into the bundle, whatever the files and sections held. */
class BundleCanaryTest {
    @get:Rule val temp = TemporaryFolder()

    private fun bundleWithEveryCanary(maskAddresses: Boolean): Map<String, String> {
        val logDir = File(temp.root, "logs-$maskAddresses").apply { mkdirs() }
        val lines = CanaryCorpus.canaries().map { it.input }.toTypedArray()
        // Raw, as a file written before redaction existed or by something that bypassed it.
        File(logDir, "borealis.log").writeLines(*lines)
        File(logDir, "borealis.log.1").writeLines(*lines.reversedArray())
        File(logDir, "crash-20261014-120000-000.txt").writeLines(*lines)
        val section = TextSection("audit.jsonl", lines.joinToString("") { "$it\n" })
        val out = ByteArrayOutputStream()
        DiagnosticBundleWriter(logDir, listOf(section), Redactor(maskAddresses)::redact)
            .write(SAMPLE_MANIFEST, SAMPLE_DECODERS, out)
        return unzip(out.toByteArray())
    }

    @Test
    fun `the corpus is loaded`() {
        assertThat(CanaryCorpus.canaries().size).isAtLeast(30)
    }

    @Test
    fun `no secret reaches the bundle with address masking off`() = assertNoSecrets(false)

    @Test
    fun `no secret reaches the bundle with address masking on`() = assertNoSecrets(true)

    private fun assertNoSecrets(maskAddresses: Boolean) {
        val entries = bundleWithEveryCanary(maskAddresses)

        assertThat(entries.keys)
            .containsAtLeast("logs/borealis.log", "logs/borealis.log.1", "audit.jsonl")
        for (canary in CanaryCorpus.canaries()) {
            for (content in entries.values) {
                assertThat(content).doesNotContain(canary.secret)
            }
        }
    }
}
