package io.github.magalz.borealis.nativecore

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import org.junit.After
import org.junit.Before
import org.junit.Test

class BorealisCoreTest {
    private val sink = RecordingSink()

    @Before
    fun setUp() {
        BorealisLog.initialize(listOf(sink))
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    @Test
    fun `version is null instead of a crash when the native library is missing on the JVM`() {
        assertThat(BorealisCore.version()).isNull()
    }

    @Test
    fun `matching versions log one info line with the core version`() {
        BorealisCore.logStartup(bridgeVersion = "0.1.0", coreVersion = "0.1.0")

        val record = sink.records.single()
        assertThat(record.level).isEqualTo(LogLevel.INFO)
        assertThat(record.tag).isEqualTo(LogTag.CORE)
        assertThat(record.fields).contains("core" to "0.1.0")
    }

    @Test
    fun `a bridge version mismatch warns with both versions`() {
        BorealisCore.logStartup(bridgeVersion = "0.2.0", coreVersion = "0.1.0")

        assertThat(sink.records.map { it.level }).containsExactly(LogLevel.INFO, LogLevel.WARN)
        val warning = sink.records.last()
        assertThat(warning.fields).containsAtLeast("core" to "0.1.0", "bridge" to "0.2.0")
    }

    @Test
    fun `an unavailable bridge warns`() {
        BorealisCore.logStartup(bridgeVersion = null, coreVersion = "0.1.0")

        val warning = sink.records.single { it.level == LogLevel.WARN }
        assertThat(warning.message).contains("bridge")
        assertThat(warning.fields).contains("core" to "0.1.0")
    }

    @Test
    fun `an unavailable core warns and does not throw`() {
        BorealisCore.logStartup(bridgeVersion = "0.1.0", coreVersion = null)

        val warning = sink.records.single()
        assertThat(warning.level).isEqualTo(LogLevel.WARN)
        assertThat(warning.message).contains("core")
    }
}
