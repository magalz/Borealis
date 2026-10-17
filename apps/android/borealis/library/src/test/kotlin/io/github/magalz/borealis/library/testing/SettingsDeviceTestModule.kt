package io.github.magalz.borealis.library.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.magalz.borealis.settings.device.DecoderLimits
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import io.github.magalz.borealis.settings.device.DisplayMode
import io.github.magalz.borealis.settings.device.OscLayoutReset

/**
 * The two bindings the app module provides to the Settings graph, for the Library's Hilt tests: a
 * plain 1080p phone and a layout reset that does nothing.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsDeviceTestModule {
    @Provides fun deviceFacts(): DeviceFactsSource = DeviceFactsSource { TEST_FACTS }

    @Provides fun oscLayoutReset(): OscLayoutReset = OscLayoutReset {}

    private val TEST_FACTS =
        DeviceFacts(
            displayModes = listOf(DisplayMode(1920, 1080, 60f)),
            maxRefreshRate = 60f,
            cutoutSafeSize = null,
            avcDecoder = DecoderLimits(3840, 2160),
            hevcDecoder = DecoderLimits(3840, 2160),
            supportsHdr10 = false,
            hasBrokenHdrFirmware = false,
            isTelevision = false,
            hasTouchscreen = true,
            hasUsbHost = true,
            supportsPictureInPicture = true,
            isFireOs = false,
            isNvidiaShield = false,
            hasVibrator = true,
            hasAmplitudeControl = true,
            hasAccelerometer = true,
            hasGyroscope = true,
            sdkInt = 34,
        )
}
