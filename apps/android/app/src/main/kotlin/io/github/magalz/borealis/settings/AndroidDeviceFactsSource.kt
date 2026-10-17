package io.github.magalz.borealis.settings

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.device.DeviceFactsSource
import javax.inject.Inject

/** The [DeviceFactsSource] of the app: it asks the Android system. */
internal class AndroidDeviceFactsSource
@Inject
constructor(@ApplicationContext private val context: Context, private val decoders: DecoderProbe) :
    DeviceFactsSource {
    override fun facts(): DeviceFacts =
        DeviceFacts(
            displayModes = emptyList(),
            maxRefreshRate = 0f,
            cutoutSafeSize = null,
            avcDecoder = null,
            hevcDecoder = null,
            supportsHdr10 = false,
            hasBrokenHdrFirmware = false,
            isTelevision = false,
            hasTouchscreen = false,
            hasUsbHost = false,
            supportsPictureInPicture = false,
            isFireOs = false,
            isNvidiaShield = false,
            hasVibrator = false,
            hasAmplitudeControl = false,
            hasAccelerometer = false,
            hasGyroscope = false,
            sdkInt = 0,
        )
}
