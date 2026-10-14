package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogFormatterTest {
    private fun record(
        message: String,
        fields: List<Pair<String, String>> = emptyList(),
        stackTrace: String? = null,
    ) =
        LogRecord(
            timestampMillis = 1_700_000_000_123L,
            level = LogLevel.INFO,
            tag = LogTag.APP,
            message = message,
            fields = fields,
            stackTrace = stackTrace,
        )

    @Test
    fun `line has iso timestamp in utc, level, tag and message`() {
        assertThat(LogFormatter.format(record("started")))
            .isEqualTo("2023-11-14T22:13:20.123Z INFO APP started")
    }

    @Test
    fun `timestamp keeps milliseconds even when zero`() {
        val line = LogFormatter.format(record("x").copy(timestampMillis = 1_700_000_000_000L))

        assertThat(line).startsWith("2023-11-14T22:13:20.000Z ")
    }

    @Test
    fun `fields are appended as key=value in order`() {
        val line =
            LogFormatter.format(record("paired", listOf("host" to "gaming-pc", "port" to "47989")))

        assertThat(line)
            .isEqualTo("2023-11-14T22:13:20.123Z INFO APP paired host=gaming-pc port=47989")
    }

    @Test
    fun `values with spaces quotes equals signs or nothing are quoted and escaped`() {
        val line =
            LogFormatter.format(
                record(
                    "m",
                    listOf(
                        "a" to "two words",
                        "b" to "say \"hi\"",
                        "c" to "x=y",
                        "d" to "",
                        "e" to "back\\slash tab\t",
                    ),
                )
            )

        assertThat(line.substringAfter(" m "))
            .isEqualTo("""a="two words" b="say \"hi\"" c="x=y" d="" e="back\\slash tab\t"""")
    }

    @Test
    fun `newlines in message and values never break the line`() {
        val line = LogFormatter.format(record("first\nsecond\r\nthird", listOf("v" to "a\nb")))

        assertThat(line).doesNotContain("\n")
        assertThat(line).doesNotContain("\r")
        assertThat(line).endsWith("""first\nsecond\r\nthird v="a\nb"""")
    }

    @Test
    fun `stack trace is appended as an escaped quoted field`() {
        val line =
            LogFormatter.format(record("boom", stackTrace = "java.lang.Boom\n\tat A.b(A.java:1)"))

        assertThat(line).endsWith("""boom stack="java.lang.Boom\n\tat A.b(A.java:1)"""")
        assertThat(line).doesNotContain("\n")
    }

    @Test
    fun `body has message and fields only`() {
        assertThat(LogFormatter.formatBody(record("paired", listOf("host" to "pc"))))
            .isEqualTo("paired host=pc")
    }

    @Test
    fun `levels map to android log priorities`() {
        assertThat(LogLevel.entries.map { it.androidPriority })
            .containsExactly(2, 3, 4, 5, 6)
            .inOrder()
    }
}
