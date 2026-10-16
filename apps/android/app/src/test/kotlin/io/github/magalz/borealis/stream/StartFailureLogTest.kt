package io.github.magalz.borealis.stream

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.library.RecordingLog
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import java.net.ConnectException
import org.junit.Rule
import org.junit.Test

class StartFailureLogTest {
    @get:Rule val log = RecordingLog()

    private val refused =
        ConnectException(
            "failed to connect to /192.168.0.7 (port 47984) from /192.168.0.9 after 5000ms"
        )

    private fun everything(): String =
        log.records.joinToString("\n") { "${it.message} ${it.fields} ${it.stackTrace}" }

    @Test
    fun `the failure of a start step is logged with the step and the exception`() {
        StartFailureLog.failed("serverinfo", refused)

        val record = log.records.single()
        assertThat(record.level).isAtLeast(LogLevel.WARN)
        assertThat(record.tag).isEqualTo(LogTag.LAUNCH)
        assertThat(everything()).contains("serverinfo")
        assertThat(everything()).contains("ConnectException")
        assertThat(everything()).contains("port 47984")
    }

    @Test
    fun `the addresses of the exception text are masked when the privacy setting asks for it`() {
        BorealisLog.setMaskAddresses(true)

        StartFailureLog.failed("serverinfo", refused)

        assertThat(everything()).doesNotContain("192.168.0.7")
        assertThat(everything()).doesNotContain("192.168.0.9")
    }

    @Test
    fun `an exception without a message is logged too`() {
        StartFailureLog.failed("launch", java.io.IOException())

        assertThat(everything()).contains("launch")
        assertThat(everything()).contains("IOException")
    }
}
