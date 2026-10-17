package io.github.magalz.borealis.settings.device

/**
 * One physical display mode, as the Android system reports it. Some devices report the portrait
 * orientation (a [height] larger than the [width]); the options logic normalises that.
 */
data class DisplayMode(val width: Int, val height: Int, val refreshRate: Float)

/** A size in pixels. */
data class PixelSize(val width: Int, val height: Int)

/** What one decoder reports that it can decode: the largest supported width and height. */
data class DecoderLimits(val maxWidth: Int, val maxHeight: Int)

/**
 * What the device offers, from which the settings screen decides which rows and choices to show
 * (ADR 0055, decision 9). It is a plain snapshot of facts; the app builds it through a
 * [DeviceFactsSource] and no Android type crosses it.
 *
 * @property displayModes the physical display modes, as reported and not yet normalised.
 * @property maxRefreshRate the highest refresh rate in Hz: the current rate, or any mode rate if
 *   higher.
 * @property cutoutSafeSize the size of the display without its cutout (notch) insets, in landscape
 *   (width at least height), or `null` when the display has no cutout or the device is below
 *   Android 9.
 * @property avcDecoder the limits of the probable safe AVC (H.264) decoder, or `null` when none was
 *   found.
 * @property hevcDecoder the limits of the probable safe HEVC decoder, or `null` when none was
 *   found.
 * @property supportsHdr10 whether the display reports HDR10.
 * @property hasBrokenHdrFirmware whether this is the NVIDIA SHIELD firmware that crashes with HDR.
 * @property isTelevision whether the device is a TV, where native resolutions are not offered
 *   unless they exceed 4K (the display modes of a TV are often wrong).
 * @property hasTouchscreen whether the device has a touchscreen.
 * @property hasUsbHost whether the device supports USB host mode.
 * @property supportsPictureInPicture whether the system supports picture-in-picture.
 * @property isFireOs whether the device runs Fire OS.
 * @property isNvidiaShield whether the device is an NVIDIA SHIELD.
 * @property hasVibrator whether the device can vibrate.
 * @property hasAmplitudeControl whether the vibrator controls its amplitude.
 * @property hasAccelerometer whether the device has an accelerometer.
 * @property hasGyroscope whether the device has a gyroscope.
 * @property sdkInt the Android SDK level.
 */
data class DeviceFacts(
    val displayModes: List<DisplayMode>,
    val maxRefreshRate: Float,
    val cutoutSafeSize: PixelSize?,
    val avcDecoder: DecoderLimits?,
    val hevcDecoder: DecoderLimits?,
    val supportsHdr10: Boolean,
    val hasBrokenHdrFirmware: Boolean,
    val isTelevision: Boolean,
    val hasTouchscreen: Boolean,
    val hasUsbHost: Boolean,
    val supportsPictureInPicture: Boolean,
    val isFireOs: Boolean,
    val isNvidiaShield: Boolean,
    val hasVibrator: Boolean,
    val hasAmplitudeControl: Boolean,
    val hasAccelerometer: Boolean,
    val hasGyroscope: Boolean,
    val sdkInt: Int,
)
