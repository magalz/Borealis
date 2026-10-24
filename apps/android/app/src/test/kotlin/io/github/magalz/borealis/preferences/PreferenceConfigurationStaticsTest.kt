package io.github.magalz.borealis.preferences

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.limelight.preferences.PreferenceConfiguration
import com.limelight.preferences.PreferenceConfiguration.AnalogStickForScrolling
import com.limelight.preferences.PreferenceConfiguration.FormatOption
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Characterizes what the inherited `PreferenceConfiguration` does without a SharedPreferences file:
 * its constants, its pure functions and the shape of a fresh instance. It records the behaviour
 * before the Kotlin conversion, so a conversion that drifts fails here.
 */
class PreferenceConfigurationStaticsTest {
    @Test
    fun `the frame pacing constants keep their values`() {
        assertThat(PreferenceConfiguration.FRAME_PACING_MIN_LATENCY).isEqualTo(0)
        assertThat(PreferenceConfiguration.FRAME_PACING_BALANCED).isEqualTo(1)
        assertThat(PreferenceConfiguration.FRAME_PACING_CAP_FPS).isEqualTo(2)
        assertThat(PreferenceConfiguration.FRAME_PACING_MAX_SMOOTHNESS).isEqualTo(3)
    }

    @Test
    fun `the resolution constants keep their values`() {
        assertThat(PreferenceConfiguration.RES_360P).isEqualTo("640x360")
        assertThat(PreferenceConfiguration.RES_480P).isEqualTo("854x480")
        assertThat(PreferenceConfiguration.RES_720P).isEqualTo("1280x720")
        assertThat(PreferenceConfiguration.RES_1080P).isEqualTo("1920x1080")
        assertThat(PreferenceConfiguration.RES_1440P).isEqualTo("2560x1440")
        assertThat(PreferenceConfiguration.RES_4K).isEqualTo("3840x2160")
        assertThat(PreferenceConfiguration.RES_NATIVE).isEqualTo("Native")
    }

    @Test
    fun `the nested enums keep their constants in order`() {
        assertThat(FormatOption.values().map { it.name })
            .containsExactly("AUTO", "FORCE_AV1", "FORCE_HEVC", "FORCE_H264")
            .inOrder()
        assertThat(AnalogStickForScrolling.values().map { it.name })
            .containsExactly("NONE", "RIGHT", "LEFT")
            .inOrder()
    }

    @Test
    fun `a fresh instance has zero numbers, false switches and no enum or audio configuration`() {
        val fresh = PreferenceConfiguration()

        assertThat(fresh.width).isEqualTo(0)
        assertThat(fresh.height).isEqualTo(0)
        assertThat(fresh.fps).isEqualTo(0)
        assertThat(fresh.bitrate).isEqualTo(0)
        assertThat(fresh.videoFormat).isNull()
        assertThat(fresh.deadzonePercentage).isEqualTo(0)
        assertThat(fresh.gamepadMouseSpeed).isEqualTo(0)
        assertThat(fresh.oscOpacity).isEqualTo(0)
        assertThat(fresh.vibrateFallbackToDeviceStrength).isEqualTo(0)
        assertThat(fresh.framePacing).isEqualTo(0)
        assertThat(fresh.analogStickForScrolling).isNull()
        assertThat(fresh.audioConfiguration).isNull()
        assertThat(booleanFields(fresh).filterValues { it }).isEmpty()
    }

    @Test
    fun `the fields that Game writes can be written and read back`() {
        val config = PreferenceConfiguration()

        config.framePacing = PreferenceConfiguration.FRAME_PACING_BALANCED
        config.videoFormat = FormatOption.FORCE_HEVC
        config.analogStickForScrolling = AnalogStickForScrolling.LEFT

        assertThat(config.framePacing).isEqualTo(PreferenceConfiguration.FRAME_PACING_BALANCED)
        assertThat(config.videoFormat).isEqualTo(FormatOption.FORCE_HEVC)
        assertThat(config.analogStickForScrolling).isEqualTo(AnalogStickForScrolling.LEFT)
    }

    @Test
    fun `the default bitrate follows the table for the resolutions and frame rates the old screen offered`() {
        for ((resolution, fps, kbps) in BITRATE_TABLE) {
            assertWithMessage("$resolution at $fps fps")
                .that(PreferenceConfiguration.getDefaultBitrate(resolution, fps))
                .isEqualTo(kbps)
        }
    }

    @Test
    fun `a half rounds up, not to the even neighbour`() {
        // The unrounded values are 0.5, 2.5 and 14.5: half-even would give 0, 2 and 14.
        assertThat(PreferenceConfiguration.getDefaultBitrate("640x360", "15")).isEqualTo(1000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "15")).isEqualTo(3000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1600x900", "60")).isEqualTo(15_000)
        // 7.5 and 3.0 are the same either way.
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "45")).isEqualTo(8000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("854x480", "45")).isEqualTo(3000)
    }

    @Test
    fun `a resolution below the lowest entry uses the lowest factor and one above the highest uses the highest`() {
        assertThat(PreferenceConfiguration.getDefaultBitrate("1x1", "60")).isEqualTo(2000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("320x240", "60")).isEqualTo(2000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("7680x4320", "60")).isEqualTo(80_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("3840x2160", "60")).isEqualTo(80_000)
    }

    @Test
    fun `a resolution between two entries is interpolated by pixel count`() {
        // 1600x900 sits 45 percent of the way from 720p (factor 5) to 1080p (factor 10): 7.25.
        assertThat(PreferenceConfiguration.getDefaultBitrate("1600x900", "30")).isEqualTo(7000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1366x768", "60")).isEqualTo(11_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("2400x1080", "60")).isEqualTo(26_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1080x1080", "60")).isEqualTo(12_000)
    }

    @Test
    fun `the frame rate factor is linear up to 60 and grows with the square root beyond it`() {
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "30")).isEqualTo(5000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "60")).isEqualTo(10_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "61")).isEqualTo(10_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "120")).isEqualTo(14_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "240")).isEqualTo(20_000)
    }

    @Test
    fun `a zero or negative frame rate gives a zero or negative bitrate`() {
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "0")).isEqualTo(0)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "-30")).isEqualTo(-5000)
    }

    @Test
    fun `the frame rate string accepts a plus sign and leading zeros`() {
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "+60")).isEqualTo(10_000)
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720", "060")).isEqualTo(10_000)
    }

    @Test
    fun `an extra part after the height is ignored`() {
        assertThat(PreferenceConfiguration.getDefaultBitrate("1280x720x1", "60")).isEqualTo(10_000)
    }

    @Test
    fun `a frame rate that is not a number fails with a number format exception`() {
        for (fps in listOf("", "abc", " 60", "60 ", "60.0", "6 0")) {
            assertThrows(NumberFormatException::class.java) {
                PreferenceConfiguration.getDefaultBitrate("1280x720", fps)
            }
        }
    }

    @Test
    fun `a width or height that is not a number fails with a number format exception`() {
        for (resolution in
            listOf("", "x720", "xx720", "1280xabc", "axb", "1280 x720", "1280X720", "Native")) {
            assertThrows(NumberFormatException::class.java) {
                PreferenceConfiguration.getDefaultBitrate(resolution, "60")
            }
        }
    }

    @Test
    fun `a resolution without a height fails with an array index exception, trailing empty parts dropped`() {
        // Java's String.split drops trailing empty strings: "1280x" has one part, not two, and "x"
        // has none. Kotlin's split keeps them and would fail with a number format exception.
        for (resolution in listOf("1280", "1280x", "1280xx", "x")) {
            assertThrows(ArrayIndexOutOfBoundsException::class.java) {
                PreferenceConfiguration.getDefaultBitrate(resolution, "60")
            }
        }
    }

    @Test
    fun `the six standard sizes are not native and any other size is`() {
        val standard =
            listOf(
                640 to 360,
                854 to 480,
                1280 to 720,
                1920 to 1080,
                2560 to 1440,
                3840 to 2160,
            )
        for ((width, height) in standard) {
            assertThat(PreferenceConfiguration.isNativeResolution(width, height)).isFalse()
        }
        for ((width, height) in
            listOf(
                360 to 640,
                1080 to 1920,
                2400 to 1080,
                1280 to 721,
                1281 to 720,
                0 to 0,
                -1280 to -720,
            )) {
            assertThat(PreferenceConfiguration.isNativeResolution(width, height)).isTrue()
        }
    }

    @Test
    fun `a screen is squarish below the ratio 1,3 in either orientation`() {
        assertThat(PreferenceConfiguration.isSquarishScreen(1000, 1000)).isTrue()
        assertThat(PreferenceConfiguration.isSquarishScreen(1299, 1000)).isTrue()
        assertThat(PreferenceConfiguration.isSquarishScreen(1000, 1299)).isTrue()
        assertThat(PreferenceConfiguration.isSquarishScreen(1200, 1000)).isTrue()
        assertThat(PreferenceConfiguration.isSquarishScreen(1, 1)).isTrue()
    }

    @Test
    fun `a screen at the ratio 1,3 or beyond is not squarish`() {
        assertThat(PreferenceConfiguration.isSquarishScreen(1300, 1000)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(1000, 1300)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(13, 10)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(1301, 1000)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(1920, 1080)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(1024, 768)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(2400, 1080)).isFalse()
    }

    @Test
    fun `a screen with a zero side is not squarish`() {
        assertThat(PreferenceConfiguration.isSquarishScreen(0, 0)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(1080, 0)).isFalse()
        assertThat(PreferenceConfiguration.isSquarishScreen(0, 1080)).isFalse()
    }

    private fun booleanFields(config: PreferenceConfiguration): Map<String, Boolean> =
        PreferenceConfiguration::class
            .java
            .fields
            .filter { it.type == Boolean::class.javaPrimitiveType }
            .associate { it.name to (it.get(config) as Boolean) }

    private data class Row(val resolution: String, val fps: String, val kbps: Int)

    private companion object {
        /** The bitrate in kbps that the Java code gave per resolution and frame rate before the conversion. */
        val BITRATE_TABLE =
            listOf(
                Row("640x360", "30", 1000),
                Row("640x360", "60", 2000),
                Row("640x360", "90", 2000),
                Row("640x360", "120", 3000),
                Row("640x360", "144", 3000),
                Row("854x480", "30", 2000),
                Row("854x480", "60", 4000),
                Row("854x480", "90", 5000),
                Row("854x480", "120", 6000),
                Row("854x480", "144", 6000),
                Row("1280x720", "30", 5000),
                Row("1280x720", "60", 10_000),
                Row("1280x720", "90", 12_000),
                Row("1280x720", "120", 14_000),
                Row("1280x720", "144", 15_000),
                Row("1920x1080", "30", 10_000),
                Row("1920x1080", "60", 20_000),
                Row("1920x1080", "90", 24_000),
                Row("1920x1080", "120", 28_000),
                Row("1920x1080", "144", 31_000),
                Row("2560x1440", "30", 20_000),
                Row("2560x1440", "60", 40_000),
                Row("2560x1440", "90", 49_000),
                Row("2560x1440", "120", 57_000),
                Row("2560x1440", "144", 62_000),
                Row("3840x2160", "30", 40_000),
                Row("3840x2160", "60", 80_000),
                Row("3840x2160", "90", 98_000),
                Row("3840x2160", "120", 113_000),
                Row("3840x2160", "144", 124_000),
                Row("2400x1080", "15", 7000),
                Row("2400x1080", "75", 30_000),
                Row("2560x1080", "60", 29_000),
                Row("3440x1440", "50", 43_000),
                Row("3440x1440", "240", 102_000),
                Row("1024x768", "75", 9000),
                Row("800x600", "240", 10_000),
                Row("7680x4320", "240", 160_000),
            )
    }
}
