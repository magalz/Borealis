package io.github.magalz.borealis.settings

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Insets
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.os.Vibrator
import android.view.Display
import android.view.DisplayCutout
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.github.magalz.borealis.settings.device.DecoderLimits
import io.github.magalz.borealis.settings.device.DisplayMode
import io.github.magalz.borealis.settings.device.PixelSize
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager

/** What the Android system says, through the shadows, is what the facts report. */
@RunWith(RobolectricTestRunner::class)
@Suppress("DEPRECATION") // FEATURE_TELEVISION is what the source asks.
@Config(qualifiers = "w360dp-h800dp-xxhdpi")
class AndroidDeviceFactsSourceTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val decoders = mutableMapOf<String, DecoderLimits?>()
    private val source = AndroidDeviceFactsSource(context) { decoders[it] }

    private val display: Display
        get() =
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)

    /** `Display.Mode` has no public constructor; the shadow takes instances of the hidden one. */
    private fun mode(id: Int, width: Int, height: Int, refreshRate: Float): Display.Mode =
        Display.Mode::class
            .java
            .getDeclaredConstructor(
                Int::class.java,
                Int::class.java,
                Int::class.java,
                Float::class.java,
            )
            .newInstance(id, width, height, refreshRate)

    private fun feature(name: String, present: Boolean) =
        shadowOf(context.packageManager).setSystemFeature(name, present)

    @Test
    fun `the display modes are those of the display`() {
        ShadowDisplayManager.setSupportedModes(
            Display.DEFAULT_DISPLAY,
            mode(1, 1080, 2400, 60f),
            mode(2, 1080, 2400, 120f),
        )

        val facts = source.facts()

        assertThat(facts.displayModes)
            .containsExactly(DisplayMode(1080, 2400, 60f), DisplayMode(1080, 2400, 120f))
    }

    @Test
    fun `the highest refresh rate is the best of the current rate and every mode`() {
        shadowOf(display).setRefreshRate(60f)
        ShadowDisplayManager.setSupportedModes(
            Display.DEFAULT_DISPLAY,
            mode(1, 1080, 2400, 60f),
            mode(2, 1080, 2400, 144f),
        )

        assertThat(source.facts().maxRefreshRate).isEqualTo(144f)
    }

    @Test
    fun `the current rate counts when it is higher than every mode`() {
        shadowOf(display).setRefreshRate(165f)
        ShadowDisplayManager.setSupportedModes(
            Display.DEFAULT_DISPLAY,
            mode(1, 1080, 2400, 60f),
        )

        assertThat(source.facts().maxRefreshRate).isEqualTo(165f)
    }

    @Test
    fun `a display without a cutout has no cutout-safe size`() {
        assertThat(source.facts().cutoutSafeSize).isNull()
    }

    private fun withTopCutout() {
        val cutout =
            DisplayCutout(
                Insets.of(0, 100, 0, 0),
                Rect(),
                Rect(440, 0, 640, 100),
                Rect(),
                Rect(),
            )
        shadowOf(display).setDisplayCutout(cutout)
    }

    @Test
    @Config(sdk = [29])
    fun `a display with a cutout reports its cutout-safe size on Android 10`() {
        withTopCutout()

        assertThat(source.facts().cutoutSafeSize).isEqualTo(PixelSize(2300, 1080))
    }

    @Test
    fun `a display with a cutout reports the size without its insets, in landscape`() {
        withTopCutout()
        val metrics = context.resources.displayMetrics

        val size = source.facts().cutoutSafeSize

        // The test display is 1080 by 2400 pixels; the insets remove 100 rows.
        assertThat(metrics.widthPixels).isEqualTo(1080)
        assertThat(size).isEqualTo(PixelSize(2300, 1080))
    }

    @Test
    fun `the decoders are those of the probe, asked for each codec`() {
        decoders["video/avc"] = DecoderLimits(4096, 2304)
        decoders["video/hevc"] = DecoderLimits(8192, 4320)

        val facts = source.facts()

        assertThat(facts.avcDecoder).isEqualTo(DecoderLimits(4096, 2304))
        assertThat(facts.hevcDecoder).isEqualTo(DecoderLimits(8192, 4320))
    }

    @Test
    fun `a codec with no decoder gives null`() {
        decoders["video/avc"] = DecoderLimits(1920, 1088)

        val facts = source.facts()

        assertThat(facts.avcDecoder).isNotNull()
        assertThat(facts.hevcDecoder).isNull()
    }

    @Test
    fun `the HDR10 type of the display is found among its types`() {
        shadowOf(display)
            .setDisplayHdrCapabilities(
                Display.DEFAULT_DISPLAY,
                600f,
                500f,
                0.1f,
                Display.HdrCapabilities.HDR_TYPE_HLG,
                Display.HdrCapabilities.HDR_TYPE_HDR10,
            )

        assertThat(source.facts().supportsHdr10).isTrue()
    }

    @Test
    fun `a display with only another HDR type is not HDR10`() {
        shadowOf(display)
            .setDisplayHdrCapabilities(
                Display.DEFAULT_DISPLAY,
                600f,
                500f,
                0.1f,
                Display.HdrCapabilities.HDR_TYPE_HLG,
            )

        assertThat(source.facts().supportsHdr10).isFalse()
    }

    @Test
    fun `a display with no HDR type is not HDR10`() {
        shadowOf(display).setDisplayHdrCapabilities(Display.DEFAULT_DISPLAY, 0f, 0f, 0f)

        assertThat(source.facts().supportsHdr10).isFalse()
    }

    @Test
    fun `the system features set what the facts report`() {
        feature(PackageManager.FEATURE_TOUCHSCREEN, true)
        feature(PackageManager.FEATURE_USB_HOST, true)
        feature(PackageManager.FEATURE_PICTURE_IN_PICTURE, true)
        feature(PackageManager.FEATURE_SENSOR_ACCELEROMETER, true)
        feature(PackageManager.FEATURE_SENSOR_GYROSCOPE, true)
        feature(PackageManager.FEATURE_TELEVISION, true)
        feature("com.amazon.software.fireos", true)
        feature("com.nvidia.feature.shield", true)

        val facts = source.facts()

        assertThat(facts.hasTouchscreen).isTrue()
        assertThat(facts.hasUsbHost).isTrue()
        assertThat(facts.supportsPictureInPicture).isTrue()
        assertThat(facts.hasAccelerometer).isTrue()
        assertThat(facts.hasGyroscope).isTrue()
        assertThat(facts.isTelevision).isTrue()
        assertThat(facts.isFireOs).isTrue()
        assertThat(facts.isNvidiaShield).isTrue()
    }

    @Test
    fun `a device without the features reports none of them`() {
        feature(PackageManager.FEATURE_TOUCHSCREEN, false)
        feature(PackageManager.FEATURE_USB_HOST, false)
        feature(PackageManager.FEATURE_PICTURE_IN_PICTURE, false)
        feature(PackageManager.FEATURE_SENSOR_ACCELEROMETER, false)
        feature(PackageManager.FEATURE_SENSOR_GYROSCOPE, false)
        feature(PackageManager.FEATURE_TELEVISION, false)
        feature("com.amazon.software.fireos", false)
        feature("com.nvidia.feature.shield", false)

        val facts = source.facts()

        assertThat(facts.hasTouchscreen).isFalse()
        assertThat(facts.hasUsbHost).isFalse()
        assertThat(facts.supportsPictureInPicture).isFalse()
        assertThat(facts.hasAccelerometer).isFalse()
        assertThat(facts.hasGyroscope).isFalse()
        assertThat(facts.isTelevision).isFalse()
        assertThat(facts.isFireOs).isFalse()
        assertThat(facts.isNvidiaShield).isFalse()
    }

    @Test
    fun `the vibrator tells whether the device vibrates and controls the amplitude`() {
        val vibrator = shadowOf(context.getSystemService(Vibrator::class.java))
        vibrator.setHasVibrator(true)
        vibrator.setHasAmplitudeControl(true)

        val withBoth = source.facts()

        vibrator.setHasAmplitudeControl(false)
        val withoutAmplitude = source.facts()

        vibrator.setHasVibrator(false)
        val withNone = source.facts()

        assertThat(withBoth.hasVibrator).isTrue()
        assertThat(withBoth.hasAmplitudeControl).isTrue()
        assertThat(withoutAmplitude.hasVibrator).isTrue()
        assertThat(withoutAmplitude.hasAmplitudeControl).isFalse()
        assertThat(withNone.hasVibrator).isFalse()
        assertThat(withNone.hasAmplitudeControl).isFalse()
    }

    @Test
    fun `the SDK level is the one of the device`() {
        assertThat(source.facts().sdkInt).isEqualTo(35)
    }

    @Test
    @Config(sdk = [30])
    fun `the SDK level follows the device`() {
        assertThat(source.facts().sdkInt).isEqualTo(30)
    }

    @Test
    fun `the decoders are not probed again by a second call when the probe is cached`() {
        var probes = 0
        val cached = CachedDecoderProbe {
            probes++
            DecoderLimits(4096, 2304)
        }
        val source = AndroidDeviceFactsSource(context, cached)

        source.facts()
        source.facts()

        // One probe for AVC and one for HEVC, not one per call.
        assertThat(probes).isEqualTo(2)
    }

    @Test
    fun `the facts are read again on every call, so they follow the display`() {
        ShadowDisplayManager.setSupportedModes(
            Display.DEFAULT_DISPLAY,
            mode(1, 1080, 2400, 60f),
        )
        val before = source.facts()

        ShadowDisplayManager.setSupportedModes(
            Display.DEFAULT_DISPLAY,
            mode(1, 1080, 2400, 60f),
            mode(2, 1080, 2400, 120f),
        )
        feature(PackageManager.FEATURE_USB_HOST, true)
        decoders["video/avc"] = DecoderLimits(4096, 2304)
        val after = source.facts()

        assertThat(before.displayModes).hasSize(1)
        assertThat(after.displayModes).hasSize(2)
        assertThat(after.hasUsbHost).isTrue()
        assertThat(before.avcDecoder).isNull()
        assertThat(after.avcDecoder).isEqualTo(DecoderLimits(4096, 2304))
    }
}
