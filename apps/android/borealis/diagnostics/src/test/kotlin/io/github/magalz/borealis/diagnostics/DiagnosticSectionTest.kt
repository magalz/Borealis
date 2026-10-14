package io.github.magalz.borealis.diagnostics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagnosticSectionTest {
    @Test
    fun acceptsPlainFileNames() {
        for (name in listOf("audit.jsonl", "decoders.json", "a-b_c.txt", "x".repeat(64))) {
            assertThat(DiagnosticSection.isSafeFileName(name)).isTrue()
        }
    }

    @Test
    fun rejectsPathsHiddenAndOversizedNames() {
        val unsafe =
            listOf(
                "",
                ".hidden",
                "../audit.jsonl",
                "logs/audit.jsonl",
                "/audit.jsonl",
                "logs\\audit.jsonl",
                "audit..jsonl",
                "audit.jsonl\u0000",
                "x".repeat(65),
            )
        for (name in unsafe) {
            assertThat(DiagnosticSection.isSafeFileName(name)).isFalse()
        }
    }
}
