package io.github.magalz.borealis.logging

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashHandlerTest {
    @get:Rule val temp = TemporaryFolder()

    private val logDir
        get() = File(temp.root, "logs")

    private val originalDefault = Thread.getDefaultUncaughtExceptionHandler()

    private class FakeHandler : Thread.UncaughtExceptionHandler {
        val calls = mutableListOf<Pair<Thread, Throwable>>()
        var filesAtCall: List<String>? = null
        var dir: File? = null

        override fun uncaughtException(t: Thread, e: Throwable) {
            calls += t to e
            filesAtCall = dir?.list()?.toList()
        }
    }

    @Before
    fun setUp() {
        Thread.setDefaultUncaughtExceptionHandler(null)
    }

    @After
    fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(originalDefault)
    }

    private fun crashFiles() =
        logDir.listFiles { f -> f.name.startsWith("crash-") }.orEmpty().toList()

    @Test
    fun `writes a timestamped crash report into the log directory`() {
        val handler = CrashReportHandler(logDir, null, clock = { 1_700_000_000_123L })

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertThat(crashFiles().map { it.name }).containsExactly("crash-20231114-221320-123.txt")
    }

    @Test
    fun `report has time, thread, header lines and the stack trace`() {
        val handler =
            CrashReportHandler(
                logDir,
                null,
                clock = { 1_700_000_000_123L },
                header = listOf("app=Borealis 1.2.3", "base=Moonlight 12.2"),
            )
        val thread = Thread({}, "render-thread")

        handler.uncaughtException(thread, IllegalStateException("boom"))

        val text = crashFiles().single().readText(Charsets.UTF_8)
        assertThat(text).contains("time=2023-11-14T22:13:20.123Z")
        assertThat(text).contains("thread=render-thread")
        assertThat(text).contains("app=Borealis 1.2.3")
        assertThat(text).contains("base=Moonlight 12.2")
        assertThat(text).contains("java.lang.IllegalStateException: boom")
        assertThat(text).contains("CrashHandlerTest")
    }

    @Test
    fun `report is redacted`() {
        val handler = CrashReportHandler(logDir, null)

        handler.uncaughtException(
            Thread.currentThread(),
            RuntimeException("pairing failed pin=7391 token=abc123"),
        )

        val text = crashFiles().single().readText()
        assertThat(text).contains("pin=[REDACTED]")
        assertThat(text).contains("token=[REDACTED]")
        assertThat(text).doesNotContain("7391")
        assertThat(text).doesNotContain("abc123")
    }

    @Test
    fun `delegates to the previous handler after the report is on disk`() {
        val previous = FakeHandler().also { it.dir = logDir }
        val handler = CrashReportHandler(logDir, previous)
        val error = IllegalStateException("boom")
        val thread = Thread.currentThread()

        handler.uncaughtException(thread, error)

        assertThat(previous.calls).containsExactly(thread to error)
        assertThat(previous.filesAtCall).hasSize(1)
    }

    @Test
    fun `still delegates when the report cannot be written`() {
        val notADirectory = File(temp.root, "file").apply { writeText("x") }
        val previous = FakeHandler()
        val handler = CrashReportHandler(notADirectory, previous)

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))

        assertThat(previous.calls).hasSize(1)
    }

    @Test
    fun `works without a previous handler`() {
        CrashReportHandler(logDir, null).uncaughtException(Thread.currentThread(), Error("x"))

        assertThat(crashFiles()).hasSize(1)
    }

    @Test
    fun `two crashes in the same millisecond keep both reports`() {
        val handler = CrashReportHandler(logDir, null, clock = { 1_700_000_000_123L })

        handler.uncaughtException(Thread.currentThread(), Error("first"))
        handler.uncaughtException(Thread.currentThread(), Error("second"))

        assertThat(crashFiles()).hasSize(2)
    }

    @Test
    fun `install wraps the previous default handler`() {
        val previous = FakeHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)

        val installed = CrashHandler.install(logDir, header = listOf("app=Borealis"))

        assertThat(Thread.getDefaultUncaughtExceptionHandler()).isSameInstanceAs(installed)
        assertThat(installed.previous).isSameInstanceAs(previous)
        Thread.getDefaultUncaughtExceptionHandler()
            .uncaughtException(Thread.currentThread(), Error("boom"))
        assertThat(crashFiles()).hasSize(1)
        assertThat(previous.calls).hasSize(1)
    }

    @Test
    fun `installing twice does not chain two report writers`() {
        val previous = FakeHandler()
        Thread.setDefaultUncaughtExceptionHandler(previous)

        CrashHandler.install(logDir)
        val second = CrashHandler.install(logDir)
        second.uncaughtException(Thread.currentThread(), Error("boom"))

        assertThat(second.previous).isSameInstanceAs(previous)
        assertThat(crashFiles()).hasSize(1)
        assertThat(previous.calls).hasSize(1)
    }

    @Test
    fun `only the newest ten crash reports are kept and other files are left alone`() {
        logDir.mkdirs()
        val log = File(logDir, "borealis.log").apply { writeText("log") }
        var now = 1_700_000_000_000L
        val handler = CrashReportHandler(logDir, null, clock = { now.also { now += 1_000L } })

        repeat(12) { handler.uncaughtException(Thread.currentThread(), Error("crash $it")) }

        val names = crashFiles().map { it.name }.sorted()
        assertThat(names).hasSize(10)
        assertThat(names.first()).isEqualTo("crash-20231114-221322-000.txt")
        assertThat(names.last()).isEqualTo("crash-20231114-221331-000.txt")
        assertThat(log.readText()).isEqualTo("log")
    }

    @Test
    fun `a long exception message is not truncated in the report`() {
        val handler = CrashReportHandler(logDir, null)
        val message = "m".repeat(40_000)

        handler.uncaughtException(Thread.currentThread(), RuntimeException(message))

        assertThat(crashFiles().single().readText()).contains(message)
    }
}
