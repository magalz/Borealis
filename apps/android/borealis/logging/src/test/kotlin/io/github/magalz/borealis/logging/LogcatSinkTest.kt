package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogcatSinkTest {
    private data class Call(val priority: Int, val tag: String, val message: String)

    private val calls = mutableListOf<Call>()
    private val sink = LogcatSink { priority, tag, message ->
        calls += Call(priority, tag, message)
    }

    private fun record(level: LogLevel, tag: LogTag, stackTrace: String? = null) =
        LogRecord(
            timestampMillis = 0L,
            level = level,
            tag = tag,
            message = "paired",
            fields = listOf("host" to "pc"),
            stackTrace = stackTrace,
        )

    @Test
    fun `prints body under a Borealis tag with the android priority`() {
        sink.write(record(LogLevel.INFO, LogTag.HOST))

        assertThat(calls).containsExactly(Call(4, "Borealis.HOST", "paired host=pc"))
    }

    @Test
    fun `every level uses its android priority`() {
        LogLevel.entries.forEach { sink.write(record(it, LogTag.APP)) }

        assertThat(calls.map { it.priority }).containsExactly(2, 3, 4, 5, 6).inOrder()
    }

    @Test
    fun `stack trace is printed as a second entry with the same tag and priority`() {
        sink.write(record(LogLevel.ERROR, LogTag.PYROWAVE, stackTrace = "java.lang.Boom\n\tat A.b"))

        assertThat(calls)
            .containsExactly(
                Call(6, "Borealis.PYROWAVE", "paired host=pc"),
                Call(6, "Borealis.PYROWAVE", "java.lang.Boom\n\tat A.b"),
            )
            .inOrder()
    }

    @Test
    fun `tags stay within the 23 character logcat limit`() {
        LogTag.entries.forEach { sink.write(record(LogLevel.INFO, it)) }

        assertThat(calls.all { it.tag.length <= 23 }).isTrue()
    }

    @Test
    fun `records below the minimum level are not printed`() {
        val filtered = mutableListOf<Call>()
        val releaseSink =
            LogcatSink(LogLevel.INFO) { priority, tag, message ->
                filtered += Call(priority, tag, message)
            }

        LogLevel.entries.forEach { releaseSink.write(record(it, LogTag.APP)) }

        assertThat(filtered.map { it.priority }).containsExactly(4, 5, 6).inOrder()
    }
}
