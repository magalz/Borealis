package io.github.magalz.borealis.preferences

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.preferences.PreferenceConfiguration
import kotlin.math.sqrt
import org.junit.Test

/**
 * The edges of `getDefaultBitrate` that [PreferenceConfigurationStaticsTest] does not reach (Sprint
 * 18a, BORE-79): the pixels of each entry of the resolution table and the ones beside it, the
 * mid-points between entries, a pixel count that overflows an `Int`, and a sweep of sizes and frame
 * rates against the loop of the Kotlin conversion, kept here as it was.
 */
class DefaultBitrateEdgesTest {
    private fun bitrate(width: Int, height: Int, fps: Int): Int =
        PreferenceConfiguration.getDefaultBitrate("${width}x$height", fps.toString())

    @Test
    fun `the pixels of each table entry and the ones beside it round to the factor of the entry`() {
        // A pixel more or less moves the interpolated factor by less than a hundredth.
        for ((pixels, kbpsAt30) in
            mapOf(
                230_400 to 1000,
                409_920 to 2000,
                921_600 to 5000,
                2_073_600 to 10_000,
                3_686_400 to 20_000,
                8_294_400 to 40_000,
            )) {
            for (near in pixels - 1..pixels + 1) {
                assertWithMessage("$near pixels at 30 fps")
                    .that(bitrate(near, 1, 30))
                    .isEqualTo(kbpsAt30)
                assertWithMessage("$near pixels at 60 fps")
                    .that(bitrate(near, 1, 60))
                    .isEqualTo(kbpsAt30 * 2)
            }
        }
    }

    @Test
    fun `the mid-point between two entries gives the mid-point factor, a half rounding up`() {
        // Factors 1.5, 3.5 and 7.5 at 30 frames per second round to 2, 4 and 8.
        assertThat(bitrate(320_160, 1, 30)).isEqualTo(2000)
        assertThat(bitrate(665_760, 1, 30)).isEqualTo(4000)
        assertThat(bitrate(1_497_600, 1, 30)).isEqualTo(8000)
        // Factors 15 and 30 are whole.
        assertThat(bitrate(2_880_000, 1, 30)).isEqualTo(15_000)
        assertThat(bitrate(5_990_400, 1, 30)).isEqualTo(30_000)
    }

    @Test
    fun `a width or a height of zero or less is below the lowest entry`() {
        assertThat(bitrate(0, 0, 60)).isEqualTo(2000)
        assertThat(bitrate(1280, 0, 60)).isEqualTo(2000)
        assertThat(bitrate(-1280, 720, 60)).isEqualTo(2000)
        assertThat(bitrate(1280, -720, 60)).isEqualTo(2000)
        assertThat(bitrate(-1, -1, 60)).isEqualTo(2000)
    }

    @Test
    fun `a pixel count that overflows an Int is read as the wrapped number`() {
        // 46341 * 46341 wraps to a negative number and 65536 * 65536 to zero: both are below the
        // lowest entry. 100000 * 100000 wraps to 1410065408, above the highest entry.
        assertThat(bitrate(46_341, 46_341, 60)).isEqualTo(2000)
        assertThat(bitrate(65_536, 65_536, 60)).isEqualTo(2000)
        assertThat(bitrate(100_000, 100_000, 60)).isEqualTo(80_000)
        // 3000 * 3000 = 9000000 does not overflow and is above the highest entry as well.
        assertThat(bitrate(3000, 3000, 60)).isEqualTo(80_000)
    }

    @Test
    fun `a sweep of pixel counts and frame rates gives what the loop gave`() {
        for (height in listOf(1, 9)) {
            for (width in -5000..SWEEP_END step SWEEP_STEP) {
                for (fps in FRAME_RATES) {
                    assertWithMessage("${width}x$height at $fps fps")
                        .that(bitrate(width, height, fps))
                        .isEqualTo(loopBitrate(width, height, fps))
                }
            }
        }
    }

    /** `getDefaultBitrate` as the Kotlin conversion of the Java code wrote it, with a loop. */
    @Suppress("LoopWithTooManyJumpStatements", "CyclomaticComplexMethod")
    private fun loopBitrate(width: Int, height: Int, fps: Int): Int {
        val frameRateFactor: Double =
            (if (fps <= 60) fps.toDouble() else sqrt((fps / 60f).toDouble()) * 60f) / 30f

        var resolutionFactor: Float
        val pixels = width * height
        var i = 0
        while (true) {
            if (pixels == PIXEL_VALS[i]) {
                resolutionFactor = FACTOR_VALS[i].toFloat()
                break
            } else if (pixels < PIXEL_VALS[i]) {
                if (i == 0) {
                    resolutionFactor = FACTOR_VALS[i].toFloat()
                } else {
                    resolutionFactor =
                        ((pixels - PIXEL_VALS[i - 1]).toFloat() /
                            (PIXEL_VALS[i] - PIXEL_VALS[i - 1])) *
                            (FACTOR_VALS[i] - FACTOR_VALS[i - 1]) + FACTOR_VALS[i - 1]
                }
                break
            } else if (PIXEL_VALS[i] == -1) {
                resolutionFactor = FACTOR_VALS[i - 1].toFloat()
                break
            }
            i++
        }

        return Math.round(resolutionFactor * frameRateFactor).toInt() * 1000
    }

    private companion object {
        const val SWEEP_END = 9_000_000
        const val SWEEP_STEP = 997
        val FRAME_RATES = listOf(1, 15, 30, 59, 60, 61, 90, 120, 144, 240, 360)

        val PIXEL_VALS =
            intArrayOf(640 * 360, 854 * 480, 1280 * 720, 1920 * 1080, 2560 * 1440, 3840 * 2160, -1)
        val FACTOR_VALS = intArrayOf(1, 2, 5, 10, 20, 40, -1)
    }
}
