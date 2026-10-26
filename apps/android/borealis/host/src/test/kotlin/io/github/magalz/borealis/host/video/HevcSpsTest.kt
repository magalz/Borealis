package io.github.magalz.borealis.host.video

import com.google.common.truth.Truth.assertThat
import java.util.Random
import org.junit.Test

class HevcSpsTest {
    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/hevc/$name")) { "missing fixture $name" }
            .use { it.readBytes() }

    private fun flags(vararg indexes: Int) = indexes.fold(0) { acc, j -> acc or (1 shl j) }

    // See resources/hevc/README.md for how each file was generated.
    private val fixtures =
        mapOf(
            "main-420-8bit.bin" to HevcSpsInfo(1, flags(1, 2), 1, false, 8, 8),
            "main-420-8bit-conformance-window.bin" to HevcSpsInfo(1, flags(1, 2), 1, false, 8, 8),
            "main10-420-10bit.bin" to HevcSpsInfo(2, flags(2), 1, false, 10, 10),
            "rext-444-8bit.bin" to HevcSpsInfo(4, flags(4), 3, false, 8, 8),
            "rext-444-10bit.bin" to HevcSpsInfo(4, flags(4), 3, false, 10, 10),
            "main-420-8bit-3-temporal-layers.bin" to HevcSpsInfo(1, flags(1, 2), 1, false, 8, 8),
        )

    @Test
    fun `real x265 SPS NAL units are read`() {
        for ((name, info) in fixtures) {
            assertThat(HevcSps.parse(fixture(name))).isEqualTo(info)
        }
    }

    @Test
    fun `a NAL without a start code or with a three byte start code reads the same`() {
        val full = fixture("main10-420-10bit.bin")
        val bare = full.copyOfRange(4, full.size)
        val three = HevcSpsBuilder.START_CODE_3 + bare

        assertThat(HevcSps.parse(bare)).isEqualTo(fixtures.getValue("main10-420-10bit.bin"))
        assertThat(HevcSps.parse(three)).isEqualTo(fixtures.getValue("main10-420-10bit.bin"))
    }

    @Test
    fun `offset and length select the NAL inside a larger buffer`() {
        val nal = fixture("rext-444-10bit.bin")
        val buffer = byteArrayOf(9, 9, 9) + nal + byteArrayOf(7, 7)

        assertThat(HevcSps.parse(buffer, 3, nal.size))
            .isEqualTo(fixtures.getValue("rext-444-10bit.bin"))
    }

    @Test
    fun `a NAL of another type is rejected`() {
        for (type in listOf(0, 1, 19, 32, 34, 39, 63)) {
            assertThat(HevcSps.parse(HevcSpsBuilder.sps(SpsFields(nalType = type)))).isNull()
        }
    }

    @Test
    fun `an SPS of another layer is rejected`() {
        assertThat(HevcSps.parse(HevcSpsBuilder.sps(SpsFields(layerId = 1)))).isNull()
    }

    @Test
    fun `invalid offsets and lengths give null`() {
        val nal = fixture("main-420-8bit.bin")

        assertThat(HevcSps.parse(nal, -1, nal.size)).isNull()
        assertThat(HevcSps.parse(nal, 0, -1)).isNull()
        assertThat(HevcSps.parse(nal, 0, nal.size + 1)).isNull()
        assertThat(HevcSps.parse(nal, nal.size + 1, 0)).isNull()
        assertThat(HevcSps.parse(nal, Int.MAX_VALUE, Int.MAX_VALUE)).isNull()
        assertThat(HevcSps.parse(ByteArray(0))).isNull()
    }

    @Test
    fun `every truncation of a valid SPS is null or the full result`() {
        val built =
            listOf(
                HevcSpsBuilder.sps(SpsFields(conformanceWindow = true, extraFields = 40)),
                HevcSpsBuilder.sps(
                    SpsFields(
                        extraFields = 40,
                        profileIdc = 4,
                        chromaFormatIdc = 3,
                        bitDepthLuma = 10,
                        bitDepthChroma = 10,
                    )
                ),
            )
        for (nal in fixtures.keys.map(::fixture) + built) {
            val full = HevcSps.parse(nal)
            assertThat(full).isNotNull()
            for (length in 0..nal.size) {
                val result = HevcSps.parse(nal, 0, length)
                assertThat(result == null || result == full).isTrue()
            }
            // A bounded prefix suffices: from the shortest parsable length on, every longer one
            // gives the full result, and that length is shorter than the whole NAL.
            val shortest = (0..nal.size).first { HevcSps.parse(nal, 0, it) != null }
            assertThat(shortest).isLessThan(nal.size)
            for (length in shortest..nal.size) {
                assertThat(HevcSps.parse(nal, 0, length)).isEqualTo(full)
            }
            assertThat(HevcSps.parse(nal, 0, 10)).isNull()
        }
    }

    @Test
    fun `random bytes never throw`() {
        val random = Random(20261014)
        repeat(50_000) {
            val bytes = ByteArray(random.nextInt(300)).also(random::nextBytes)
            if (bytes.size >= 2 && random.nextBoolean()) bytes[0] = 0x42
            HevcSps.parse(bytes)
            HevcSps.parse(bytes, random.nextInt(bytes.size + 1), random.nextInt(bytes.size + 1))
        }
    }

    @Test
    fun `corrupted valid SPS never throw and stay in range`() {
        val random = Random(7)
        val sources = fixtures.keys.map(::fixture)
        repeat(20_000) {
            val bytes = sources[random.nextInt(sources.size)].copyOf()
            repeat(1 + random.nextInt(4)) {
                bytes[random.nextInt(bytes.size)] = random.nextInt().toByte()
            }
            val info = HevcSps.parse(bytes)
            if (info != null) {
                assertThat(info.chromaFormatIdc).isIn(0..3)
                assertThat(info.bitDepthLuma).isIn(8..16)
                assertThat(info.bitDepthChroma).isIn(8..16)
            }
        }
    }

    @Test
    fun `sub-layer profile and level fields are skipped with the right alignment`() {
        for (subLayersMinus1 in 1..6) {
            for (mask in 0 until (1 shl 12) step 7) {
                val nal =
                    HevcSpsBuilder.sps(
                        SpsFields(
                            maxSubLayersMinus1 = subLayersMinus1,
                            subLayerProfilePresent = List(6) { (mask shr it) and 1 == 1 },
                            subLayerLevelPresent = List(6) { (mask shr (it + 6)) and 1 == 1 },
                            profileIdc = 4,
                            chromaFormatIdc = 3,
                            conformanceWindow = true,
                            bitDepthLuma = 12,
                            bitDepthChroma = 10,
                        )
                    )

                val info = HevcSps.parse(nal)

                assertThat(info?.profileIdc).isEqualTo(4)
                assertThat(info?.chromaFormatIdc).isEqualTo(3)
                assertThat(info?.bitDepthLuma).isEqualTo(12)
                assertThat(info?.bitDepthChroma).isEqualTo(10)
            }
        }
    }

    @Test
    fun `a reserved sps_max_sub_layers_minus1 of 7 is rejected`() {
        assertThat(HevcSps.parse(HevcSpsBuilder.sps(SpsFields(maxSubLayersMinus1 = 7)))).isNull()
    }

    @Test
    fun `emulation prevention bytes are removed before parsing`() {
        // Zero compatibility flags give the RBSP 00 00 runs that must be escaped on the wire.
        val escaped =
            HevcSpsBuilder.sps(
                SpsFields(profileIdc = 4, compatibilityFlags = 0, chromaFormatIdc = 3)
            )
        val unescaped =
            HevcSpsBuilder.sps(
                SpsFields(
                    profileIdc = 4,
                    compatibilityFlags = 0,
                    chromaFormatIdc = 3,
                    escaped = false,
                )
            )
        assertThat(escaped.size).isGreaterThan(unescaped.size)

        assertThat(HevcSps.parse(escaped)).isEqualTo(HevcSpsInfo(4, 0, 3, false, 8, 8))
    }

    @Test
    fun `separate colour planes are reported for 4 4 4`() {
        val nal =
            HevcSpsBuilder.sps(
                SpsFields(profileIdc = 4, chromaFormatIdc = 3, separateColourPlane = true)
            )

        assertThat(HevcSps.parse(nal)?.separateColourPlane).isTrue()
        assertThat(
                HevcSps.parse(HevcSpsBuilder.sps(SpsFields(chromaFormatIdc = 3)))
                    ?.separateColourPlane
            )
            .isFalse()
    }

    @Test
    fun `values outside the specification are rejected`() {
        assertThat(HevcSps.parse(HevcSpsBuilder.sps(SpsFields(chromaFormatIdc = 4)))).isNull()
        assertThat(HevcSps.parse(HevcSpsBuilder.sps(SpsFields(bitDepthLuma = 17)))).isNull()
        assertThat(HevcSps.parse(HevcSpsBuilder.sps(SpsFields(bitDepthChroma = 20)))).isNull()
    }

    @Test
    fun `a runaway Exp-Golomb code is bounded`() {
        // Start code (4), NAL header (2), VPS id and sub-layer byte (1) and profile_tier_level
        // (12), then 40 zero bits where an Exp-Golomb code starts.
        val valid = HevcSpsBuilder.sps(SpsFields(escaped = false))
        val headerAndPtl = valid.copyOfRange(0, 4 + 2 + 1 + 12)

        assertThat(HevcSps.parse(headerAndPtl + ByteArray(40))).isNull()
    }

    @Test
    fun `a conformance window is skipped`() {
        val with =
            HevcSps.parse(
                HevcSpsBuilder.sps(
                    SpsFields(conformanceWindow = true, bitDepthLuma = 10, bitDepthChroma = 10)
                )
            )
        val without =
            HevcSps.parse(
                HevcSpsBuilder.sps(
                    SpsFields(
                        conformanceWindow = false,
                        bitDepthLuma = 10,
                        bitDepthChroma = 10,
                    )
                )
            )

        assertThat(with).isEqualTo(without)
        assertThat(with?.bitDepthLuma).isEqualTo(10)
    }
}
