package io.github.magalz.borealis.settings

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Vibrator
import android.util.DisplayMetrics
import android.view.Display
import com.limelight.preferences.PreferenceConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import io.github.magalz.borealis.settings.device.DisplayMode
import io.github.magalz.borealis.settings.device.PixelSize
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

/**
 * The [DeviceFactsSource] of the app: it asks the Android system each time, so the facts follow the
 * display (a foldable, an external display).
 *
 * The display facts are read on each call. The decoder limits come from the [DecoderProbe], which
 * the app caches. The cutout is read through `Display.getCutout`, which needs Android 10. On
 * Android 9 the cutout-safe size is `null`: the inherited screen read it from the window insets of
 * an activity, which an application context does not have.
 */
internal class AndroidDeviceFactsSource
@Inject
constructor(@ApplicationContext private val context: Context, private val decoders: DecoderProbe) :
    DeviceFactsSource {
    override fun facts(): DeviceFacts {
        val display =
            checkNotNull(
                context
                    .getSystemService(DisplayManager::class.java)
                    .getDisplay(Display.DEFAULT_DISPLAY)
            ) {
                "The default display is gone"
            }
        val modes =
            display.supportedModes.map {
                DisplayMode(it.physicalWidth, it.physicalHeight, it.refreshRate)
            }
        val vibrator = context.getSystemService(Vibrator::class.java)
        return DeviceFacts(
            displayModes = modes,
            maxRefreshRate =
                modes.fold(display.refreshRate) { best, mode -> max(best, mode.refreshRate) },
            cutoutSafeSize = cutoutSafeSize(display),
            avcDecoder = decoders.limits(DecoderProbe.AVC),
            hevcDecoder = decoders.limits(DecoderProbe.HEVC),
            supportsHdr10 = supportsHdr10(display),
            hasBrokenHdrFirmware = PreferenceConfiguration.isShieldAtvFirmwareWithBrokenHdr(),
            isTelevision = hasFeature(FEATURE_TELEVISION),
            hasTouchscreen = hasFeature(PackageManager.FEATURE_TOUCHSCREEN),
            hasUsbHost = hasFeature(PackageManager.FEATURE_USB_HOST),
            supportsPictureInPicture = hasFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE),
            isFireOs = hasFeature(FEATURE_FIRE_OS),
            isNvidiaShield = hasFeature(FEATURE_NVIDIA_SHIELD),
            hasVibrator = vibrator?.hasVibrator() == true,
            hasAmplitudeControl = vibrator?.hasAmplitudeControl() == true,
            hasAccelerometer = hasFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER),
            hasGyroscope = hasFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    private fun hasFeature(name: String): Boolean = context.packageManager.hasSystemFeature(name)

    /** The real size without the cutout insets, in landscape; `null` without a cutout. */
    @Suppress("DEPRECATION") // getRealMetrics: the inherited screen reads the same size.
    private fun cutoutSafeSize(display: Display): PixelSize? {
        val cutout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) display.cutout else null
        val widthInsets = (cutout?.safeInsetLeft ?: 0) + (cutout?.safeInsetRight ?: 0)
        val heightInsets = (cutout?.safeInsetBottom ?: 0) + (cutout?.safeInsetTop ?: 0)
        if (widthInsets == 0 && heightInsets == 0) return null
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        val width = metrics.widthPixels - widthInsets
        val height = metrics.heightPixels - heightInsets
        return PixelSize(max(width, height), min(width, height))
    }

    // getHdrCapabilities is deprecated from API 34 but is all that older versions offer; it
    // returns null on some devices, for example the Lenovo Mirage Solo.
    @Suppress("DEPRECATION")
    private fun supportsHdr10(display: Display): Boolean =
        display.hdrCapabilities
            ?.supportedHdrTypes
            ?.contains(Display.HdrCapabilities.HDR_TYPE_HDR10) == true

    private companion object {
        // FEATURE_TELEVISION is deprecated but is what the inherited screen asks.
        @Suppress("DEPRECATION") const val FEATURE_TELEVISION = PackageManager.FEATURE_TELEVISION
        const val FEATURE_FIRE_OS = "com.amazon.software.fireos"
        const val FEATURE_NVIDIA_SHIELD = "com.nvidia.feature.shield"
    }
}
