package io.github.magalz.borealis.settings.device

/** Provides the [DeviceFacts] of this device. The app implements it. */
fun interface DeviceFactsSource {
    /**
     * The facts of this device now. They can change with the display (a foldable, an external
     * display), so a caller asks again on a configuration change and does not keep them in a
     * singleton.
     */
    fun facts(): DeviceFacts
}
