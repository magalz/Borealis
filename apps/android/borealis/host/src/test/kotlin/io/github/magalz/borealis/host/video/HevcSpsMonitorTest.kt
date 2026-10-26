package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogRecord
import io.github.magalz.borealis.logging.LogSink
import io.github.magalz.borealis.logging.LogTag
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.After
import org.junit.Before
import org.junit.Test

class HevcSpsMonitorTest {
    private val records = CopyOnWriteArrayList<LogRecord>()
    private var declaresMain10 = true
    private var declaresMain10Asked = 0

    private val main = HevcSpsBuilder.sps(SpsFields(profileIdc = 1))
    private val main10 =
        HevcSpsBuilder.sps(SpsFields(profileIdc = 2, bitDepthLuma = 10, bitDepthChroma = 10))
    private val rext444 = HevcSpsBuilder.sps(SpsFields(profileIdc = 4, chromaFormatIdc = 3))

    @Before
    fun recordLogs() {
        BorealisLog.initialize(listOf(RecordingSink(records)))
    }

    @After
    fun stopRecording() {
        BorealisLog.shutdown()
    }

    private class RecordingSink(private val into: MutableList<LogRecord>) : LogSink {
        override fun write(record: LogRecord) {
            into += record
        }
    }

    private fun monitor(requestedHdr: Boolean = true, debugBuild: Boolean = false) =
        HevcSpsMonitor(
            requestedHdr = requestedHdr,
            decoderDeclaresMain10 = {
                declaresMain10Asked++
                declaresMain10
            },
            debugBuild = debugBuild,
        )

    private fun at(level: LogLevel) = records.filter { it.level == level }

    @Test
    fun `the first SPS is logged at INFO with profile chroma and depth`() {
        monitor().onSps(main10)

        assertThat(records).hasSize(1)
        assertThat(records[0].level).isEqualTo(LogLevel.INFO)
        assertThat(records[0].tag).isEqualTo(LogTag.VIDEO)
        assertThat(records[0].message).contains("profile=Main10 chroma=4:2:0 depth=10/10")
    }

    @Test
    fun `an identical SPS is not logged again`() {
        val monitor = monitor()
        monitor.onSps(main)
        monitor.onSps(main)
        monitor.onSps(HevcSpsBuilder.sps(SpsFields(profileIdc = 1, threeByteStartCode = true)))

        assertThat(records).hasSize(1)
        assertThat(records[0].message).contains("profile=Main chroma=4:2:0 depth=8/8")
    }

    @Test
    fun `a changed SPS is logged again`() {
        val monitor = monitor()
        monitor.onSps(main)
        monitor.onSps(rext444)

        assertThat(at(LogLevel.INFO)).hasSize(2)
        assertThat(records[1].message).contains("profile=RExt chroma=4:4:4 depth=8/8")
    }

    @Test
    fun `a 10-bit stream on a decoder without Main10 warns`() {
        declaresMain10 = false

        monitor().onSps(main10)

        assertThat(at(LogLevel.WARN)).hasSize(1)
        assertThat(at(LogLevel.WARN)[0].message).contains("Main10")
    }

    @Test
    fun `a 10-bit stream on a decoder with Main10 does not warn`() {
        monitor().onSps(main10)

        assertThat(at(LogLevel.WARN)).isEmpty()
    }

    @Test
    fun `an 8-bit stream does not ask the decoder or warn`() {
        declaresMain10 = false

        monitor().onSps(main)

        assertThat(at(LogLevel.WARN)).isEmpty()
        assertThat(declaresMain10Asked).isEqualTo(0)
    }

    @Test
    fun `chroma 4 4 4 at 8 bit does not need Main10`() {
        declaresMain10 = false

        monitor().onSps(rext444)

        assertThat(at(LogLevel.WARN)).isEmpty()
    }

    @Test
    fun `a 10-bit SPS in a session that did not request HDR is noted at INFO`() {
        monitor(requestedHdr = false).onSps(main10)

        val notes = at(LogLevel.INFO).filter { it.message.contains("did not request HDR") }
        assertThat(notes).hasSize(1)
        assertThat(at(LogLevel.WARN)).isEmpty()
    }

    @Test
    fun `a 10-bit SPS in an HDR session is not noted`() {
        monitor(requestedHdr = true).onSps(main10)

        assertThat(records.filter { it.message.contains("did not request HDR") }).isEmpty()
    }

    @Test
    fun `an 8-bit SPS in an SDR session is not noted`() {
        monitor(requestedHdr = false).onSps(main)

        assertThat(records.filter { it.message.contains("did not request HDR") }).isEmpty()
    }

    @Test
    fun `a downgrade from 4 4 4 to 4 2 0 warns`() {
        val monitor = monitor()
        monitor.onSps(rext444)
        monitor.onSps(main)

        val warnings = at(LogLevel.WARN)
        assertThat(warnings).hasSize(1)
        assertThat(warnings[0].message).contains("4:4:4")
        assertThat(warnings[0].message).contains("4:2:0")
    }

    @Test
    fun `an upgrade to 4 4 4 or a first 4 2 0 SPS does not warn`() {
        val monitor = monitor()
        monitor.onSps(main)
        monitor.onSps(rext444)

        assertThat(at(LogLevel.WARN)).isEmpty()
    }

    @Test
    fun `an unreadable SPS is logged once at DEBUG and never throws`() {
        val monitor = monitor()

        monitor.onSps(byteArrayOf(1, 2, 3))
        monitor.onSps(ByteArray(0))
        monitor.onSps(byteArrayOf(0x42, 1), 0, 99)

        assertThat(records).hasSize(1)
        assertThat(records[0].level).isEqualTo(LogLevel.DEBUG)
        assertThat(records[0].tag).isEqualTo(LogTag.VIDEO)
    }

    @Test
    fun `an unreadable SPS does not hide the next change`() {
        val monitor = monitor()
        monitor.onSps(rext444)
        monitor.onSps(byteArrayOf(1, 2, 3))
        monitor.onSps(main)

        assertThat(at(LogLevel.WARN)).hasSize(1)
    }

    @Test
    fun `a failing decoder lookup never escapes`() {
        val monitor =
            HevcSpsMonitor(
                requestedHdr = true,
                decoderDeclaresMain10 = { error("no capabilities") },
                debugBuild = false,
            )

        monitor.onSps(main10)

        assertThat(at(LogLevel.INFO)).isNotEmpty()
    }

    @Test
    fun `debug builds dump at most 128 bytes of the first SPS`() {
        val nal = HevcSpsBuilder.sps() + ByteArray(200) { 0x7F }
        val monitor = monitor(debugBuild = true)

        monitor.onSps(nal)
        monitor.onSps(nal.copyOf().also { it[nal.size - 1] = 1 })

        val dumps = at(LogLevel.DEBUG)
        assertThat(dumps).hasSize(1)
        val hex = dumps[0].message.substringAfter("hex=").trim().split(" ")
        assertThat(hex).hasSize(128)
        assertThat(hex.take(5)).containsExactly("00", "00", "00", "01", "42").inOrder()
    }

    @Test
    fun `release builds do not dump bytes`() {
        monitor(debugBuild = false).onSps(HevcSpsBuilder.sps())

        assertThat(at(LogLevel.DEBUG)).isEmpty()
    }

    @Test
    fun `a debug dump of a short SPS honours offset and length`() {
        val nal = HevcSpsBuilder.sps()
        val padded = byteArrayOf(0x55) + nal + byteArrayOf(0x66)

        monitor(debugBuild = true).onSps(padded, 1, nal.size)

        val hex = at(LogLevel.DEBUG).single().message.substringAfter("hex=").trim().split(" ")
        assertThat(hex).hasSize(nal.size)
        assertThat(hex).doesNotContain("55")
    }

    @Test
    fun `a session without an HEVC decoder declares no Main10`() {
        HevcSpsMonitor.forDecoder(null, requestedHdr = true, debugBuild = false).onSps(main10)

        assertThat(at(LogLevel.WARN)).hasSize(1)
    }

    @Test
    fun `Main10 is declared by any of the Main10 profile constants`() {
        assertThat(HevcSpsMonitor.declaresMain10(intArrayOf(2))).isTrue()
        assertThat(HevcSpsMonitor.declaresMain10(intArrayOf(1, 4096))).isTrue()
        assertThat(HevcSpsMonitor.declaresMain10(intArrayOf(8192))).isTrue()
        assertThat(HevcSpsMonitor.declaresMain10(intArrayOf(1, 4))).isFalse()
        assertThat(HevcSpsMonitor.declaresMain10(intArrayOf())).isFalse()
    }
}
