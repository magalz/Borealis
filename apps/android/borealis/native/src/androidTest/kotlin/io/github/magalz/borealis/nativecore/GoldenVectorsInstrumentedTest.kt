package io.github.magalz.borealis.nativecore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Replays the golden vectors of `core/tests/fixtures/vectors` (packaged as androidTest assets)
 * through [NativeHostRules], so a divergence between the C ABI and the Kotlin binding fails on a
 * device. The TSV format is described in `core/tests/fixtures/README.md`.
 */
@RunWith(AndroidJUnit4::class)
class GoldenVectorsInstrumentedTest {
    private class Row(val file: String, val line: Int, private val fields: List<String>) {
        operator fun get(index: Int): String = fields[index]

        override fun toString() = "$file:$line $fields"
    }

    private fun rows(file: String): List<Row> {
        val text =
            InstrumentationRegistry.getInstrumentation().context.assets.open(file).use {
                it.readBytes().decodeToString()
            }
        val records =
            text
                .lines()
                .mapIndexed { index, line -> index + 1 to line.removeSuffix("\r") }
                .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") }
        // The first remaining line is the header.
        return records.drop(1).map { (line, content) -> Row(file, line, content.split('\t')) }
    }

    private fun text(token: String): String? =
        when (token) {
            "<null>" -> null
            "<empty>" -> ""
            else -> token
        }

    // Decimal or 0x-prefixed hexadecimal; values above 0x7FFFFFFF are unsigned and keep their bits.
    private fun int(token: String): Int =
        if (token.startsWith("0x")) token.substring(2).toLong(16).toInt()
        else token.toLong().toInt()

    @Test
    fun hostCapsVectorsMatch() {
        val rows = rows("host_caps.tsv")
        assertWithMessage("host_caps.tsv rows").that(rows).isNotEmpty()
        for (row in rows) {
            val caps = NativeHostRules.parseCaps(text(row[0]), int(row[1]))
            assertWithMessage("$row").that(caps).isNotNull()
            val codec = int(row[7])
            val pyroWave = int(row[8])
            val expected =
                HostCaps(
                    family = HostFamily.valueOf(row[2]),
                    version = listOf(int(row[3]), int(row[4]), int(row[5]), int(row[6])),
                    hevc = codec and 0x01 != 0,
                    hevcMain10 = codec and 0x02 != 0,
                    av1 = codec and 0x04 != 0,
                    av1Main10 = codec and 0x08 != 0,
                    yuv444 = codec and 0x10 != 0,
                    pyroWaveProfiles =
                        mapOf(
                                0x01 to PyroWaveProfile.SDR_420,
                                0x02 to PyroWaveProfile.SDR_444,
                                0x04 to PyroWaveProfile.HDR_420,
                                0x08 to PyroWaveProfile.HDR_444,
                            )
                            .filterKeys { pyroWave and it != 0 }
                            .values
                            .toSet(),
                )
            assertWithMessage("$row").that(caps).isEqualTo(expected)
        }
    }

    @Test
    fun launchDecisionVectorsMatch() {
        val rows = rows("launch_decide.tsv")
        assertWithMessage("launch_decide.tsv rows").that(rows).isNotEmpty()
        for (row in rows) {
            val decision =
                NativeHostRules.decideLaunch(
                    verb = LaunchVerb.valueOf(row[0]),
                    status = int(row[1]),
                    message = text(row[2]),
                    attemptsInCategory = int(row[3]),
                )
            val expected =
                LaunchDecision(
                    action = LaunchAction.valueOf(row[4]),
                    delayMs = int(row[5]),
                    category = LaunchCategory.valueOf(row[6]),
                    maxAttempts = int(row[7]),
                )
            assertWithMessage("$row").that(decision).isEqualTo(expected)
        }
    }

    @Test
    fun launchFpsVectorsMatch() {
        val rows = rows("launch_fps.tsv")
        assertWithMessage("launch_fps.tsv rows").that(rows).isNotEmpty()
        for (row in rows) {
            val fps = NativeHostRules.launchFps(int(row[0]) != 0, int(row[1]), int(row[2]))
            assertWithMessage("$row").that(fps).isEqualTo(int(row[3]))
        }
    }
}
