package io.github.magalz.borealis.settings.testing

import io.github.magalz.borealis.settings.device.DecoderLimits
import io.github.magalz.borealis.settings.device.DeviceFacts
import io.github.magalz.borealis.settings.device.DisplayMode
import io.github.magalz.borealis.settings.device.PixelSize

/**
 * Builds a [DeviceFacts] for a test. Every field starts as a recent phone that has everything: a
 * 2400 by 1080 display at 60 and 120 Hz, no cutout, 4K decoders, HDR10, a touchscreen, USB host,
 * picture-in-picture, a vibrator with amplitude control, both motion sensors and Android 15. Change
 * only what the test is about.
 */
class DeviceFactsBuilder {
    var displayModes: List<DisplayMode> =
        listOf(DisplayMode(2400, 1080, 60f), DisplayMode(2400, 1080, 120f))
    var maxRefreshRate: Float = 120f
    var cutoutSafeSize: PixelSize? = null
    var avcDecoder: DecoderLimits? = DecoderLimits(4096, 4096)
    var hevcDecoder: DecoderLimits? = DecoderLimits(4096, 4096)
    var supportsHdr10: Boolean = true
    var hasBrokenHdrFirmware: Boolean = false
    var isTelevision: Boolean = false
    var hasTouchscreen: Boolean = true
    var hasUsbHost: Boolean = true
    var supportsPictureInPicture: Boolean = true
    var isFireOs: Boolean = false
    var isNvidiaShield: Boolean = false
    var hasVibrator: Boolean = true
    var hasAmplitudeControl: Boolean = true
    var hasAccelerometer: Boolean = true
    var hasGyroscope: Boolean = true
    var sdkInt: Int = 35

    /** The facts that this builder holds. */
    fun build(): DeviceFacts =
        DeviceFacts(
            displayModes = displayModes,
            maxRefreshRate = maxRefreshRate,
            cutoutSafeSize = cutoutSafeSize,
            avcDecoder = avcDecoder,
            hevcDecoder = hevcDecoder,
            supportsHdr10 = supportsHdr10,
            hasBrokenHdrFirmware = hasBrokenHdrFirmware,
            isTelevision = isTelevision,
            hasTouchscreen = hasTouchscreen,
            hasUsbHost = hasUsbHost,
            supportsPictureInPicture = supportsPictureInPicture,
            isFireOs = isFireOs,
            isNvidiaShield = isNvidiaShield,
            hasVibrator = hasVibrator,
            hasAmplitudeControl = hasAmplitudeControl,
            hasAccelerometer = hasAccelerometer,
            hasGyroscope = hasGyroscope,
            sdkInt = sdkInt,
        )
}

/** A [DeviceFacts] for a test: a recent phone that has everything, changed by [configure]. */
fun deviceFacts(configure: DeviceFactsBuilder.() -> Unit = {}): DeviceFacts =
    DeviceFactsBuilder().apply(configure).build()
