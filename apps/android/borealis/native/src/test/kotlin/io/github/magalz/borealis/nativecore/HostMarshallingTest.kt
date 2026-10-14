package io.github.magalz.borealis.nativecore

import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.logging.BorealisLog
import io.github.magalz.borealis.logging.LogLevel
import io.github.magalz.borealis.logging.LogTag
import org.junit.After
import org.junit.Before
import org.junit.Test

class HostMarshallingTest {
    private val sink = RecordingSink()

    @Before
    fun setUp() {
        BorealisLog.initialize(listOf(sink))
    }

    @After
    fun tearDown() {
        BorealisLog.shutdown()
    }

    // Wire layout, mirroring borealis_jni.cpp: [status, family, v0, v1, v2, v3, codec, pyrowave].
    private fun capsWire(
        family: Int = 2,
        version: List<Int> = listOf(7, 1, 431, -1),
        codec: Int = 0,
        pyroWave: Int = 0,
        status: Int = 0,
    ) = intArrayOf(status, family) + version.toIntArray() + intArrayOf(codec, pyroWave)

    // [status, action, delay_ms, category, max_attempts].
    private fun decisionWire(
        action: Int = 1,
        delayMs: Int = 1000,
        category: Int = 1,
        maxAttempts: Int = 4,
        status: Int = 0,
    ) = intArrayOf(status, action, delayMs, category, maxAttempts)

    @Test
    fun `an absent message is a null array`() {
        assertThat(HostMarshalling.encodeMessage(null)).isNull()
    }

    @Test
    fun `an empty message is an empty non-null array`() {
        assertThat(HostMarshalling.encodeMessage("")).isEmpty()
    }

    @Test
    fun `a short message is its UTF-8 bytes`() {
        assertThat(HostMarshalling.encodeMessage("Pending é"))
            .isEqualTo("Pending é".encodeToByteArray())
    }

    @Test
    fun `messages of 255, 256 and 257 ASCII bytes keep at most 256 bytes`() {
        assertThat(HostMarshalling.encodeMessage("a".repeat(255))!!.size).isEqualTo(255)
        assertThat(HostMarshalling.encodeMessage("a".repeat(256))!!.size).isEqualTo(256)
        assertThat(HostMarshalling.encodeMessage("a".repeat(257))!!.size).isEqualTo(256)
        assertThat(HostMarshalling.encodeMessage("a".repeat(1000))!!.size).isEqualTo(256)
    }

    @Test
    fun `a two byte character that ends exactly at 256 bytes is kept`() {
        val bytes = HostMarshalling.encodeMessage("a".repeat(254) + "é")!!

        assertThat(bytes.size).isEqualTo(256)
        assertThat(bytes.decodeToString()).endsWith("é")
    }

    @Test
    fun `a two byte character that straddles the limit is dropped whole`() {
        val bytes = HostMarshalling.encodeMessage("a".repeat(255) + "é")!!

        assertThat(bytes.size).isEqualTo(255)
        assertThat(bytes.decodeToString()).isEqualTo("a".repeat(255))
    }

    @Test
    fun `a four byte character that straddles the limit is dropped whole`() {
        val bytes = HostMarshalling.encodeMessage("a".repeat(254) + "😀")!!

        assertThat(bytes.size).isEqualTo(254)
        assertThat(bytes.decodeToString()).isEqualTo("a".repeat(254))
    }

    @Test
    fun `a message of only multi byte characters is cut on a character boundary`() {
        val bytes = HostMarshalling.encodeMessage("é".repeat(200))!!

        assertThat(bytes.size).isEqualTo(256)
        assertThat(bytes.decodeToString()).isEqualTo("é".repeat(128))
    }

    @Test
    fun `non negative attempts pass through`() {
        assertThat(HostMarshalling.clampAttempts(0)).isEqualTo(0)
        assertThat(HostMarshalling.clampAttempts(3)).isEqualTo(3)
        assertThat(HostMarshalling.clampAttempts(Int.MAX_VALUE)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `negative attempts are clamped to Int MAX_VALUE`() {
        assertThat(HostMarshalling.clampAttempts(-1)).isEqualTo(Int.MAX_VALUE)
        assertThat(HostMarshalling.clampAttempts(Int.MIN_VALUE)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `caps decode the family, the version and the empty flag sets`() {
        val caps = HostMarshalling.decodeCaps(capsWire())

        assertThat(caps)
            .isEqualTo(
                HostCaps(
                    family = HostFamily.SUNSHINE,
                    version = listOf(7, 1, 431, -1),
                    hevc = false,
                    hevcMain10 = false,
                    av1 = false,
                    av1Main10 = false,
                    yuv444 = false,
                    pyroWaveProfiles = emptySet(),
                )
            )
        assertThat(sink.records).isEmpty()
    }

    @Test
    fun `every host family value is decoded`() {
        assertThat(HostMarshalling.decodeCaps(capsWire(family = 0))!!.family)
            .isEqualTo(HostFamily.UNKNOWN)
        assertThat(HostMarshalling.decodeCaps(capsWire(family = 1))!!.family)
            .isEqualTo(HostFamily.GFE)
        assertThat(HostMarshalling.decodeCaps(capsWire(family = 2))!!.family)
            .isEqualTo(HostFamily.SUNSHINE)
    }

    @Test
    fun `an unknown family keeps the zero version`() {
        val caps = HostMarshalling.decodeCaps(capsWire(family = 0, version = listOf(0, 0, 0, 0)))!!

        assertThat(caps.version).containsExactly(0, 0, 0, 0).inOrder()
    }

    @Test
    fun `each codec flag bit sets exactly its own boolean`() {
        fun decode(bits: Int) = HostMarshalling.decodeCaps(capsWire(codec = bits))!!

        assertThat(decode(0x01)).isEqualTo(decode(0).copy(hevc = true))
        assertThat(decode(0x02)).isEqualTo(decode(0).copy(hevcMain10 = true))
        assertThat(decode(0x04)).isEqualTo(decode(0).copy(av1 = true))
        assertThat(decode(0x08)).isEqualTo(decode(0).copy(av1Main10 = true))
        assertThat(decode(0x10)).isEqualTo(decode(0).copy(yuv444 = true))
        assertThat(decode(0x1F))
            .isEqualTo(
                decode(0)
                    .copy(
                        hevc = true,
                        hevcMain10 = true,
                        av1 = true,
                        av1Main10 = true,
                        yuv444 = true,
                    )
            )
    }

    @Test
    fun `each PyroWave bit maps to its profile`() {
        fun decode(bits: Int) = HostMarshalling.decodeCaps(capsWire(pyroWave = bits))!!

        assertThat(decode(0x1).pyroWaveProfiles).containsExactly(PyroWaveProfile.SDR_420)
        assertThat(decode(0x2).pyroWaveProfiles).containsExactly(PyroWaveProfile.SDR_444)
        assertThat(decode(0x4).pyroWaveProfiles).containsExactly(PyroWaveProfile.HDR_420)
        assertThat(decode(0x8).pyroWaveProfiles).containsExactly(PyroWaveProfile.HDR_444)
        assertThat(decode(0xF).pyroWaveProfiles).containsExactlyElementsIn(PyroWaveProfile.entries)
    }

    @Test
    fun `flag bits the binding does not know are ignored`() {
        val caps = HostMarshalling.decodeCaps(capsWire(codec = 0x21, pyroWave = 0x11))!!

        assertThat(caps.hevc).isTrue()
        assertThat(caps.pyroWaveProfiles).containsExactly(PyroWaveProfile.SDR_420)
    }

    @Test
    fun `a non OK caps status decodes to null and warns with the status only`() {
        assertThat(HostMarshalling.decodeCaps(capsWire(status = -2))).isNull()

        val record = sink.records.single()
        assertThat(record.level).isEqualTo(LogLevel.WARN)
        assertThat(record.tag).isEqualTo(LogTag.CORE)
        assertThat(record.fields).contains("status" to "-2")
    }

    @Test
    fun `an unknown family decodes to null and warns`() {
        assertThat(HostMarshalling.decodeCaps(capsWire(family = 3))).isNull()
        assertThat(HostMarshalling.decodeCaps(capsWire(family = -1))).isNull()

        assertThat(sink.records.map { it.level }).containsExactly(LogLevel.WARN, LogLevel.WARN)
    }

    @Test
    fun `a missing or wrongly sized caps array decodes to null and warns`() {
        assertThat(HostMarshalling.decodeCaps(null)).isNull()
        assertThat(HostMarshalling.decodeCaps(intArrayOf(0, 2, 7))).isNull()
        assertThat(HostMarshalling.decodeCaps(capsWire() + 0)).isNull()

        assertThat(sink.records).hasSize(3)
        assertThat(sink.records.map { it.level }.toSet()).containsExactly(LogLevel.WARN)
    }

    @Test
    fun `every action and category value is decoded`() {
        fun decode(action: Int, category: Int) =
            HostMarshalling.decodeDecision(decisionWire(action = action, category = category))!!

        assertThat(decode(0, 0).action).isEqualTo(LaunchAction.FAIL)
        assertThat(decode(1, 0).action).isEqualTo(LaunchAction.RETRY)
        assertThat(decode(2, 0).action).isEqualTo(LaunchAction.FALLBACK_TO_LAUNCH)
        assertThat(decode(0, 0).category).isEqualTo(LaunchCategory.PERMANENT)
        assertThat(decode(0, 1).category).isEqualTo(LaunchCategory.PENDING)
        assertThat(decode(0, 2).category).isEqualTo(LaunchCategory.RESTORING_DISPLAY)
        assertThat(decode(0, 3).category).isEqualTo(LaunchCategory.GPU_NOT_READY)
        assertThat(decode(0, 4).category).isEqualTo(LaunchCategory.CAPTURE_OR_DISPLAY)
        assertThat(decode(0, 5).category).isEqualTo(LaunchCategory.NO_RUNNING_APP)
    }

    @Test
    fun `a decision carries the delay and the attempt limit`() {
        val decision = HostMarshalling.decodeDecision(decisionWire(delayMs = 5000, maxAttempts = 2))

        assertThat(decision)
            .isEqualTo(
                LaunchDecision(
                    action = LaunchAction.RETRY,
                    delayMs = 5000,
                    category = LaunchCategory.PENDING,
                    maxAttempts = 2,
                )
            )
        assertThat(sink.records).isEmpty()
    }

    @Test
    fun `a non OK decision status decodes to null and warns`() {
        assertThat(HostMarshalling.decodeDecision(decisionWire(status = -1))).isNull()

        val record = sink.records.single()
        assertThat(record.level).isEqualTo(LogLevel.WARN)
        assertThat(record.fields).contains("status" to "-1")
    }

    @Test
    fun `an unknown action or category decodes to null and warns`() {
        assertThat(HostMarshalling.decodeDecision(decisionWire(action = 3))).isNull()
        assertThat(HostMarshalling.decodeDecision(decisionWire(category = 6))).isNull()
        assertThat(HostMarshalling.decodeDecision(decisionWire(action = -1))).isNull()

        assertThat(sink.records).hasSize(3)
    }

    @Test
    fun `a missing or wrongly sized decision array decodes to null and warns`() {
        assertThat(HostMarshalling.decodeDecision(null)).isNull()
        assertThat(HostMarshalling.decodeDecision(intArrayOf(0))).isNull()
        assertThat(HostMarshalling.decodeDecision(decisionWire() + 0)).isNull()

        assertThat(sink.records).hasSize(3)
    }
}
